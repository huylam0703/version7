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

export type NodeStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'MAINTENANCE'
  | 'LOST'

export type ConnectionType =
  | 'HTTP'
  | 'HTTPS'
  | 'MQTT'
  | 'TCP'

export interface SensorTypeDto {
  typeId: string
  code: string
  name: string
  unit: string
  minValid: number | null
  maxValid: number | null
  decimals: number | null
}

export interface ChannelRequest {
  typeId: string
  channelNo: number
  calibrationOffset?: number
}

export interface CreateNodeRequest {
  zoneId: string
  deviceUid: string
  name: string
  authKey: string
  lat?: number
  lng?: number
  firmwareVer?: string
  installedAt?: string
  connectionType?: ConnectionType
  endpointUrl?: string
  channels: ChannelRequest[]
}

export interface UpdateNodeRequest {
  zoneId?: string
  name?: string
  lat?: number
  lng?: number
  firmwareVer?: string
  status?: NodeStatus
  installedAt?: string
  connectionType?: ConnectionType
  endpointUrl?: string
  lastSeenAt?: string
}

export interface RotateCredentialResponse {
  nodeId: string
  credentialRotated: boolean
}