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

export function getErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) return error.message
  return 'Something went wrong'
}

export const farmApi = {
  list: () => mockApi.farms.list(),
  get: (id: string) => mockApi.farms.get(id),
  create: (body: Partial<Farm>) => mockApi.farms.create(body),
  update: (id: string, body: Partial<Farm>) => mockApi.farms.update(id, body),
  setActive: (id: string, isActive: boolean) => mockApi.farms.setActive(id, isActive),
}

export const zoneApi = {
  list: (farmId?: string) => mockApi.zones.list(farmId),
  create: (body: Partial<Zone>) => mockApi.zones.create(body),
  update: (id: string, body: Partial<Zone>) => mockApi.zones.update(id, body),
  setActive: (id: string, isActive: boolean) => mockApi.zones.setActive(id, isActive),
}

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
  list: () => mockApi.notifications.list(),
  markRead: (id: string) => mockApi.notifications.markRead(id),
}

export const reportApi = {
  summary: (_params?: object) => mockApi.reports.summary(),
}

export const userApi = {
  list: () => mockApi.users.list(),
  create: (body: Partial<User> & { password?: string }) => mockApi.users.create(body),
  update: (userId: string, body: Partial<User>) => mockApi.users.create({ ...body, userId }),
}

export const accountApi = {
  update: (body: Partial<User> & { password?: string }) => mockApi.account.update(body),
}

export const uavApi = {
  list: () => mockApi.uavs.list(),
  create: (body: Partial<Uav>) => mockApi.uavs.create(body),
  update: (id: string, body: Partial<Uav>) => mockApi.uavs.update(id, body),
}

export const gatewayApi = {
  list: () => mockApi.gateways.list(),
  create: (body: Partial<Gateway>) => mockApi.gateways.create(body),
  update: (id: string, body: Partial<Gateway>) => mockApi.gateways.update(id, body),
}

export const missionApi = {
  list: () => mockApi.missions.list(),
  get: (id: string) => mockApi.missions.get(id),
  create: (body: Partial<Mission>) => mockApi.missions.create(body),
  update: (id: string, body: Partial<Mission>) => mockApi.missions.updateStatus(id, body.status || 'planned', body.notes),
  updateStatus: (id: string, status: string, notes?: string) =>
    mockApi.missions.updateStatus(id, status as Mission['status'], notes),
  results: (id: string) => mockApi.missions.results(id),
}

export const dashboardApi = {
  admin: (params?: object) => mockApi.dashboard.admin(params as { farmId?: string; range?: string }),
  operator: () => mockApi.dashboard.operator(),
}
