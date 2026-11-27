export type GatewayStatus =
  | 'ready'
  | 'in_use'
  | 'maintenance'
  | 'retired'

export interface Gateway {
  id: string
  code: string
  name: string
  uavId?: string
  status?: GatewayStatus
  hwModel?: string
  firmwareVer?: string
  storageMb?: number
}

export interface GatewayDto {
  gatewayId: string
  gatewayUid: string
  name: string
  uavId: string | null
  hwModel: string | null
  firmwareVer: string | null
  storageMb: number | null
  status: Uppercase<GatewayStatus>
  createdAt: string
}