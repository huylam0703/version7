export type UavStatus =
  | 'ready'
  | 'in_mission'
  | 'maintenance'
  | 'retired'

export interface Uav {
  id: string
  farmId: string
  code: string
  name: string
  model?: string
  maxFlightMin?: number
  status?: UavStatus
}

export interface UavDto {
  uavId: string
  farmId: string
  serialNo: string
  name: string
  model?: string
  maxFlightMin?: number
  status: Uppercase<UavStatus>
  createdAt: string
  updatedAt: string
}