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


// ===== Zone =====


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


// ===== Gateway =====


export const dashboardApi = {
  admin: (params?: object) => mockApi.dashboard.admin(params as { farmId?: string; range?: string }),
  operator: () => mockApi.dashboard.operator(),
}

export { missionApi }