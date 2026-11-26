export interface SensorChannelDto {
  channelId: string
  typeId: string
  typeCode: string
  channelNo: number
  calibrationOffset: number
  active: boolean
}

export interface SensorNodeDto {
  nodeId: string
  zoneId: string
  farmId: string
  deviceUid: string
  name: string
  lat: number | null
  lng: number | null
  firmwareVer: string | null
  status: 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE' | 'LOST'
  installedAt: string | null
  connectionType: string | null
  endpointUrl: string | null
  lastSeenAt: string | null
  channels: SensorChannelDto[]
  createdAt: string
  updatedAt: string
}

export interface SensorNodeListParams {
  farmId?: string
  zoneId?: string
  status?: string
  keyword?: string
  page?: number
  pageSize?: number
}

export interface SensorNodePageDto {
  items: SensorNodeDto[]
  page: number
  pageSize: number
  total: number
}