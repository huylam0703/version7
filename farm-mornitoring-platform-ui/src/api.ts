import type {
  AlertItem,
  AlertSeverity,
  AlertThreshold,
  SensorType,
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

const sensorCodes: Record<SensorType, string> = {
  soilMoisture: 'SOIL_MOISTURE',
  airTemperature: 'AIR_TEMP',
  airHumidity: 'AIR_HUMIDITY',
  lightIntensity: 'LIGHT',
  ph: 'PH',
  waterLevel: 'WATER_LEVEL',
}

const alertSeverities = { info: 'LOW', warning: 'MEDIUM', critical: 'CRITICAL' } as const
const displaySeverity = { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'critical' } as const
const displayStatus = { NEW: 'open', ACKNOWLEDGED: 'acknowledged', RESOLVED: 'closed' } as const

type ThresholdDto = Omit<AlertThreshold, 'sensorType' | 'severity'> & {
  sensorCode: string
  severity: keyof typeof displaySeverity
}

type AlertDto = {
  id: string
  nodeId: string
  alertType: string
  message: string
  severity: keyof typeof displaySeverity
  status: keyof typeof displayStatus
  createdAt: string | null
}

export const alertApi = {
  list: async (params?: { severity?: AlertSeverity; status?: AlertItem['status'] }): Promise<AlertItem[]> => {
    const { data } = await axiosClient.get<{ result: AlertDto[] }>('/v1/alerts')
    return data.result.map((alert) => ({
      id: alert.id,
      title: alert.alertType,
      message: alert.message,
      sensorNodeId: alert.nodeId,
      severity: displaySeverity[alert.severity],
      status: displayStatus[alert.status],
      createdAt: alert.createdAt ?? '',
    })).filter((alert) => (!params?.severity || alert.severity === params.severity)
      && (!params?.status || alert.status === params.status))
  },
  updateStatus: (id: string, status: 'acknowledged' | 'closed') =>
    axiosClient.patch(`/v1/alerts/${id}/${status === 'closed' ? 'resolve' : 'acknowledge'}`),
  thresholds: async (): Promise<AlertThreshold[]> => {
    const { data } = await axiosClient.get<ThresholdDto[]>('/v1/thresholds')
    return data.map(({ sensorCode, severity, ...threshold }) => ({
      ...threshold,
      sensorType: (Object.keys(sensorCodes) as SensorType[]).find((key) => sensorCodes[key] === sensorCode)!,
      severity: displaySeverity[severity],
    }))
  },
  saveThreshold: (body: Partial<AlertThreshold>) => axiosClient.put('/v1/thresholds', {
    farmId: body.farmId || null,
    sensorCode: body.sensorType && sensorCodes[body.sensorType],
    severity: body.severity && alertSeverities[body.severity],
    minValue: body.minValue,
    maxValue: body.maxValue,
  }),
}

export const notificationApi = {
  list: async () => (await axiosClient.get<NotificationItem[]>('/v1/notifications/me')).data,
  markRead: async (id: string) => {
    await axiosClient.patch(`/v1/notifications/${id}/read`)
  },
  unreadCount: async () =>
    (await axiosClient.get<{ unreadCount: number }>('/v1/notifications/unread-count')).data.unreadCount,
  markAllRead: () => axiosClient.patch('/v1/notifications/read-all'),
  remove: (id: string) => axiosClient.delete(`/v1/notifications/${id}`),
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
