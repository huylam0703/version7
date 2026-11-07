// Role khớp với backend. Đổi string nếu backend dùng tên khác.
export type UserRole = 'ADMIN' | 'OPERATOR'

export type User = {
  userId: string
  fullName: string
  email: string
  phone: string | null
  role: UserRole
  status: string
  lastLoginAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken: string
  refreshTokenExpiresAt: string
  user: User
}

export type Farm = {
  id: string
  name: string
  address?: string
  areaHa?: number
  latitude?: number
  longitude?: number
  isActive?: boolean
  description?: string
  boundary?: [number, number][]
}

export type Zone = {
  id: string
  farmId: string
  farmName?: string
  name: string
  cropType?: string
  latitude?: number
  longitude?: number
  isActive?: boolean
  sensorCount?: number
  boundary?: [number, number][]
}

export type SensorStatus = 'online' | 'offline' | 'maintenance' | 'lost'

export type SensorType =
  | 'soilMoisture'
  | 'airTemperature'
  | 'airHumidity'
  | 'lightIntensity'
  | 'ph'
  | 'waterLevel'

export type SensorNode = {
  id: string
  code: string
  name: string
  farmId?: string
  farmName?: string
  zoneId?: string
  zoneName?: string
  sensorType: SensorType
  status: SensorStatus
  latitude?: number
  longitude?: number
  batteryPercent?: number
  lastSeenAt?: string
  lastValue?: number
  unit?: string
}

export type SensorReading = {
  id: string
  sensorNodeId: string
  sensorType: SensorType
  value: number
  unit?: string
  recordedAt: string
  zoneName?: string
}

export type AlertSeverity = 'critical' | 'warning' | 'info'
export type AlertStatus = 'open' | 'acknowledged' | 'closed'

export type AlertItem = {
  id: string
  title: string
  message?: string
  severity: AlertSeverity
  status: AlertStatus
  farmName?: string
  zoneName?: string
  sensorNodeId?: string
  createdAt: string
}

export type AlertThreshold = {
  id: string
  farmId?: string
  sensorType: SensorType
  minValue?: number
  maxValue?: number
  severity: AlertSeverity
  isActive?: boolean
}

export type NotificationItem = {
  id: string
  title: string
  message?: string
  isRead?: boolean
  createdAt: string
}

export type ReportSummary = {
  farmCount?: number
  zoneCount?: number
  readingCount?: number
  alertCount?: number
  missionCount?: number
}

export type Uav = {
  id: string
  code: string
  name: string
  model?: string
  status?: string
  batteryPercent?: number
  latitude?: number
  longitude?: number
}

export type Gateway = {
  id: string
  code: string
  name: string
  uavId?: string
  uavName?: string
  status?: string
}

export type MissionStatus =
  | 'planned'
  | 'inProgress'
  | 'paused'
  | 'completed'
  | 'failed'
  | 'cancelled'

export type Waypoint = {
  latitude: number
  longitude: number
  order: number
  sensorNodeId?: string
}

export type Mission = {
  id: string
  name: string
  farmId?: string
  farmName?: string
  uavId?: string
  uavName?: string
  gatewayId?: string
  gatewayName?: string
  status: MissionStatus
  scheduledAt?: string
  notes?: string
  waypointProgress?: number
  collectionProgress?: number
  batteryPercent?: number
  latitude?: number
  longitude?: number
  waypoints?: Waypoint[]
  sensorNodeIds?: string[]
}

export type MissionResult = {
  id: string
  missionId: string
  sensorNodeId?: string
  sensorCode?: string
  success: boolean
  message?: string
  collectedAt?: string
}

export const SENSOR_TYPE_LABEL: Record<SensorType, string> = {
  soilMoisture: 'Soil Moisture (%)',
  airTemperature: 'Air Temperature (°C)',
  airHumidity: 'Air Humidity (%)',
  lightIntensity: 'Light Intensity (lux)',
  ph: 'pH',
  waterLevel: 'Water Level (cm)',
}
