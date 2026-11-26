import type {
  AlertItem,
  AlertThreshold,
  Farm,
  Gateway,
  Mission,
  SensorNode,
  Uav,
  User,
  Zone,
} from './types'
import { mockApi } from './mockStore'
import axiosClient from './api/axiosClient'
import type { NotificationItem , MissionStatus } from './types'
import { missionApi } from './api/operator/missionApi'

// ===== Kiểu dữ liệu backend farm-device trả về =====
type Page<T> = { items: T[]; page: number; pageSize: number; total: number }

type FarmDto = {
  farmId: string
  name: string
  address?: string
  centerLat?: number
  centerLng?: number
  areaHa?: number
  description?: string
  status: string
}
type ZoneDto = {
  zoneId: string
  farmId: string
  code: string
  name: string
  cropType?: string
  centerLat?: number
  centerLng?: number
  status: string
}
type UavDto = { uavId: string; farmId: string; serialNo: string; name: string; model?: string; status: string }

type MissionDetailDto = {
  missionId: string
  farmId: string
  uavId: string
  gatewayId: string
  name: string
  createdBy: string
  notes?: string
  status: string
  plannedAt?: string
  startedAt?: string
  endedAt?: string
  totalNodes: number
  successNodes: number
  failedNodes: number
  waypoints?: {
    latitude: number
    longitude: number
    order: number
    sensorNodeId?: string
  }[]
  nodeIds?: string[]
}

type GatewayDto = { gatewayId: string; uavId?: string; gatewayUid: string; name: string; status: string }

// ===== Hàm dùng chung =====
const toStatus = (active?: boolean) => (active === false ? 'INACTIVE' : 'ACTIVE')
const lower = (s?: string) => s?.toLowerCase()
const upper = (s?: string) => s?.toUpperCase().replace(/-/g, '_')

async function listAll<T>(url: string, params?: object): Promise<T[]> {
  const res = await axiosClient.get<Page<T>>(url, { params: { ...params, page: 1, pageSize: 100 } })
  return res.data.items
}

async function firstFarmId() {
  const farms = await listAll<FarmDto>('/v1/farms')
  if (!farms.length) throw new Error('Hãy tạo ít nhất một Farm trước.')
  return farms[0].farmId
}

export function getErrorMessage(error: unknown) {
  const data = (error as { response?: { data?: { detail?: string; title?: string; message?: string } } })?.response
    ?.data
  if (data?.detail || data?.title || data?.message) return (data.detail || data.title || data.message) as string
  if (error instanceof Error && error.message) return error.message
  return 'Something went wrong'
}

// ===== Farm =====
const toFarm = (f: FarmDto): Farm => ({
  id: f.farmId,
  name: f.name,
  address: f.address,
  areaHa: f.areaHa,
  latitude: f.centerLat,
  longitude: f.centerLng,
  description: f.description,
  isActive: f.status === 'ACTIVE',
})

const farmBody = (b: Partial<Farm>) => ({
  name: b.name,
  address: b.address,
  centerLat: b.latitude,
  centerLng: b.longitude,
  areaHa: b.areaHa,
  description: b.description,
})

export const farmApi = {
  list: async () => (await listAll<FarmDto>('/v1/farms')).map(toFarm),
  get: async (id: string) => toFarm((await axiosClient.get<FarmDto>(`/v1/farms/${id}`)).data),
  create: async (body: Partial<Farm>) =>
    toFarm((await axiosClient.post<FarmDto>('/v1/farms', farmBody(body))).data),
  update: async (id: string, body: Partial<Farm>) =>
    toFarm((await axiosClient.patch<FarmDto>(`/v1/farms/${id}`, farmBody(body))).data),
  setActive: async (id: string, isActive: boolean) =>
    toFarm((await axiosClient.patch<FarmDto>(`/v1/farms/${id}`, { status: toStatus(isActive) })).data),
}

// ===== Zone =====
const toZone = (z: ZoneDto, farmName?: string): Zone => ({
  id: z.zoneId,
  farmId: z.farmId,
  farmName,
  name: z.name,
  cropType: z.cropType,
  latitude: z.centerLat,
  longitude: z.centerLng,
  isActive: z.status === 'ACTIVE',
})

// Backend bắt buộc có code, FE không có ô này nên tự sinh
const zoneCode = (name?: string) =>
  ((name || 'ZONE').toUpperCase().replace(/[^A-Z0-9]+/g, '').slice(0, 10) || 'ZONE') +
  '-' +
  Date.now().toString(36).slice(-4)

export const zoneApi = {
  list: async (farmId?: string) => {
    const farms = await listAll<FarmDto>('/v1/farms')
    const targets = farmId ? farms.filter((f) => f.farmId === farmId) : farms
    const groups = await Promise.all(
      targets.map(async (f) =>
        (await listAll<ZoneDto>(`/v1/farms/${f.farmId}/zones`)).map((z) => toZone(z, f.name)),
      ),
    )
    return groups.flat()
  },
  create: async (body: Partial<Zone>) => {
    const farmId = body.farmId || (await firstFarmId())
    const res = await axiosClient.post<ZoneDto>(`/v1/farms/${farmId}/zones`, {
      code: zoneCode(body.name),
      name: body.name,
      cropType: body.cropType,
      centerLat: body.latitude,
      centerLng: body.longitude,
    })
    return toZone(res.data)
  },
  update: async (id: string, body: Partial<Zone>) =>
    toZone(
      (
        await axiosClient.patch<ZoneDto>(`/v1/zones/${id}`, {
          name: body.name,
          cropType: body.cropType,
          centerLat: body.latitude,
          centerLng: body.longitude,
        })
      ).data,
    ),
  setActive: async (id: string, isActive: boolean) =>
    toZone((await axiosClient.patch<ZoneDto>(`/v1/zones/${id}`, { status: toStatus(isActive) })).data),
}

// ===== Sensor (vẫn dùng dữ liệu giả, chưa nối) =====
export const sensorApi = {
  list: (params?: object) => mockApi.sensors.list(params as { farmId?: string; zoneId?: string; sensorType?: string }),
  create: (body: Partial<SensorNode>) => mockApi.sensors.create(body),
  update: (id: string, body: Partial<SensorNode>) => mockApi.sensors.update(id, body),
  readings: (params?: object) =>
    mockApi.sensors.readings(
      params as {
        sensorNodeId?: string
        sensorType?: string
        farmId?: string
        zoneId?: string
        from?: string
        to?: string
      },
    ),
}

export const alertApi = {
  list: (params?: object) => mockApi.alerts.list(params as { severity?: string; status?: string }),
  updateStatus: (id: string, status: string) => mockApi.alerts.updateStatus(id, status as AlertItem['status']),
  thresholds: () => mockApi.alerts.thresholds(),
  saveThreshold: (body: Partial<AlertThreshold>) => mockApi.alerts.saveThreshold(body),
  updateThreshold: (id: string, body: Partial<AlertThreshold>) => mockApi.alerts.saveThreshold({ ...body, id }),
}

export const notificationApi = {
  list: async () => (await axiosClient.get<NotificationItem[]>('/v1/notifications/me')).data,
  markRead: async (id: string) => {
    await axiosClient.patch(`/v1/notifications/${id}/read`)
  },
}

export const reportApi = {
  summary: (_params?: object) => mockApi.reports.summary(),
}

// ===== UAV =====
const toUav = (u: UavDto): Uav => ({
  id: u.uavId,
  code: u.serialNo,
  name: u.name,
  model: u.model,
  status: lower(u.status),
})

export const uavApi = {
  list: async () => (await listAll<UavDto>('/v1/uavs')).map(toUav),
  create: async (body: Partial<Uav>) =>
    toUav(
      (
        await axiosClient.post<UavDto>('/v1/uavs', {
          farmId: await firstFarmId(),
          serialNo: body.code,
          name: body.name,
          model: body.model,
        })
      ).data,
    ),
  update: async (id: string, body: Partial<Uav>) =>
    toUav(
      (
        await axiosClient.patch<UavDto>(`/v1/uavs/${id}`, {
          name: body.name,
          model: body.model,
          status: body.status ? upper(body.status) : undefined,
        })
      ).data,
    ),
}

// ===== Gateway =====
const toGateway = (g: GatewayDto): Gateway => ({
  id: g.gatewayId,
  code: g.gatewayUid,
  name: g.name,
  uavId: g.uavId,
  status: lower(g.status),
})

export const gatewayApi = {
  list: async () => (await listAll<GatewayDto>('/v1/gateways')).map(toGateway),
  create: async (body: Partial<Gateway>) =>
    toGateway(
      (
        await axiosClient.post<GatewayDto>('/v1/gateways', {
          gatewayUid: body.code,
          name: body.name,
          uavId: body.uavId || undefined,
          authKey: crypto.randomUUID().replace(/-/g, ''),
        })
      ).data,
    ),
  update: async (id: string, body: Partial<Gateway>) =>
    toGateway(
      (
        await axiosClient.patch<GatewayDto>(`/v1/gateways/${id}`, {
          name: body.name,
          uavId: body.uavId || undefined,
          status: body.status ? upper(body.status) : undefined,
        })
      ).data,
    ),
}



export const dashboardApi = {
  admin: (params?: object) => mockApi.dashboard.admin(params as { farmId?: string; range?: string }),
  operator: () => mockApi.dashboard.operator(),
}

export { missionApi }