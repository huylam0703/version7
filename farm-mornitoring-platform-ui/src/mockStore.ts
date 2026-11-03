import type {
  AlertItem,
  AlertThreshold,
  Farm,
  Gateway,
  Mission,
  MissionResult,
  NotificationItem,
  SensorNode,
  SensorReading,
  SensorType,
  Uav,
  User,
  Zone,
} from './types'

function daysAgo(days: number, hour = 8) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  date.setHours(hour, 0, 0, 0)
  return date.toISOString()
}

function box(lat: number, lng: number, d = 0.012): [number, number][] {
  return [
    [lat - d, lng - d],
    [lat - d, lng + d],
    [lat + d, lng + d],
    [lat + d, lng - d],
  ]
}

const farms: Farm[] = [
  {
    id: 'f1',
    name: 'Mekong Delta Plot',
    address: 'Tan Thanh, Long An',
    areaHa: 42.5,
    latitude: 10.542,
    longitude: 106.412,
    isActive: true,
    description: 'Rice and vegetable blocks served by UAV collection routes.',
    boundary: box(10.542, 106.412, 0.03),
  },
  {
    id: 'f2',
    name: 'Can Tho Orchard',
    address: 'Co Do, Can Tho',
    areaHa: 28.2,
    latitude: 10.045,
    longitude: 105.712,
    isActive: true,
    description: 'Fruit orchard with mixed soil-moisture and climate nodes.',
    boundary: box(10.045, 105.712, 0.025),
  },
  {
    id: 'f3',
    name: 'Lam Dong Highland',
    address: 'Don Duong, Lam Dong',
    areaHa: 18.6,
    latitude: 11.768,
    longitude: 108.582,
    isActive: false,
    description: 'Cool-climate vegetable beds. Currently inactive for the season.',
    boundary: box(11.768, 108.582, 0.02),
  },
]

const zones: Zone[] = [
  { id: 'z1', farmId: 'f1', name: 'North Rice', cropType: 'Rice', latitude: 10.552, longitude: 106.402, isActive: true, boundary: box(10.552, 106.402, 0.008) },
  { id: 'z2', farmId: 'f1', name: 'East Vegetables', cropType: 'Leafy greens', latitude: 10.536, longitude: 106.428, isActive: true, boundary: box(10.536, 106.428, 0.008) },
  { id: 'z3', farmId: 'f2', name: 'Pomelo Block', cropType: 'Pomelo', latitude: 10.052, longitude: 105.702, isActive: true, boundary: box(10.052, 105.702, 0.008) },
  { id: 'z4', farmId: 'f2', name: 'Mango Block', cropType: 'Mango', latitude: 10.036, longitude: 105.724, isActive: true, boundary: box(10.036, 105.724, 0.008) },
  { id: 'z5', farmId: 'f3', name: 'Greenhouse A', cropType: 'Tomato', latitude: 11.774, longitude: 108.574, isActive: true, boundary: box(11.774, 108.574, 0.006) },
  { id: 'z6', farmId: 'f3', name: 'Open Beds', cropType: 'Lettuce', latitude: 11.76, longitude: 108.592, isActive: false, boundary: box(11.76, 108.592, 0.006) },
]

const sensorTypes: SensorType[] = ['soilMoisture', 'airTemperature', 'airHumidity', 'lightIntensity', 'ph', 'waterLevel']
const units: Record<SensorType, string> = {
  soilMoisture: '%',
  airTemperature: '°C',
  airHumidity: '%',
  lightIntensity: 'lux',
  ph: '',
  waterLevel: 'cm',
}
const bases: Record<SensorType, number> = {
  soilMoisture: 34,
  airTemperature: 29,
  airHumidity: 72,
  lightIntensity: 18400,
  ph: 6.4,
  waterLevel: 18,
}

const nodes: SensorNode[] = zones.flatMap((zone, zoneIndex) =>
  sensorTypes.slice(0, zoneIndex % 2 === 0 ? 3 : 2).map((sensorType, typeIndex) => {
    const statuses = ['online', 'online', 'offline', 'maintenance', 'lost'] as const
    const status = statuses[(zoneIndex + typeIndex) % statuses.length]
    return {
      id: `n${zoneIndex + 1}-${typeIndex + 1}`,
      code: `SN-${zone.id.toUpperCase()}-${typeIndex + 1}`,
      name: `${zone.name} ${sensorType === 'airTemperature' ? 'Temp' : sensorType === 'soilMoisture' ? 'Soil' : sensorType === 'airHumidity' ? 'Humidity' : sensorType === 'lightIntensity' ? 'Light' : sensorType === 'ph' ? 'pH' : 'Water'}`,
      farmId: zone.farmId,
      zoneId: zone.id,
      sensorType,
      status,
      latitude: (zone.latitude || 0) + typeIndex * 0.002,
      longitude: (zone.longitude || 0) + typeIndex * 0.002,
      batteryPercent: status === 'lost' ? 4 : 40 + ((zoneIndex * 7 + typeIndex * 11) % 55),
      lastSeenAt: status === 'online' ? daysAgo(0, 9) : daysAgo(2 + typeIndex, 16),
      lastValue: Number((bases[sensorType] + zoneIndex * 1.4 - typeIndex).toFixed(1)),
      unit: units[sensorType],
    }
  }),
)

const readings: SensorReading[] = nodes.flatMap((node) =>
  Array.from({ length: 14 }, (_, index) => {
    const base = node.lastValue ?? bases[node.sensorType]
    const wave = Math.sin(index / 2.2) * Math.max(base * 0.06, 0.4)
    return {
      id: `${node.id}-r${index}`,
      sensorNodeId: node.id,
      sensorType: node.sensorType,
      value: Number((base + wave).toFixed(1)),
      unit: node.unit,
      recordedAt: daysAgo(13 - index, 7),
      zoneName: node.zoneName,
    }
  }),
)

const alerts: AlertItem[] = [
  { id: 'a1', title: 'Soil moisture below threshold', message: 'North Rice dropped to 21%.', severity: 'critical', status: 'open', farmName: 'Mekong Delta Plot', zoneName: 'North Rice', createdAt: daysAgo(0, 6) },
  { id: 'a2', title: 'Node offline', message: 'Pomelo humidity node missed two collection windows.', severity: 'warning', status: 'open', farmName: 'Can Tho Orchard', zoneName: 'Pomelo Block', createdAt: daysAgo(0, 8) },
  { id: 'a3', title: 'High air temperature', message: 'East Vegetables peaked at 36.2°C.', severity: 'warning', status: 'acknowledged', farmName: 'Mekong Delta Plot', zoneName: 'East Vegetables', createdAt: daysAgo(1, 13) },
  { id: 'a4', title: 'Battery low', message: 'Greenhouse light node at 12%.', severity: 'info', status: 'open', farmName: 'Lam Dong Highland', zoneName: 'Greenhouse A', createdAt: daysAgo(1, 10) },
  { id: 'a5', title: 'pH drift', message: 'Mango block pH moved outside 5.8–7.0.', severity: 'critical', status: 'acknowledged', farmName: 'Can Tho Orchard', zoneName: 'Mango Block', createdAt: daysAgo(2, 9) },
  { id: 'a6', title: 'Water level recovered', message: 'Canal sensor returned to the normal band.', severity: 'info', status: 'closed', farmName: 'Mekong Delta Plot', zoneName: 'North Rice', createdAt: daysAgo(3, 15) },
  { id: 'a7', title: 'Collection gap', message: 'East Vegetables missed yesterday’s pass.', severity: 'warning', status: 'closed', farmName: 'Mekong Delta Plot', zoneName: 'East Vegetables', createdAt: daysAgo(4, 11) },
]

const thresholds: AlertThreshold[] = [
  { id: 't1', farmId: 'f1', sensorType: 'soilMoisture', minValue: 25, maxValue: 70, severity: 'critical', isActive: true },
  { id: 't2', farmId: 'f1', sensorType: 'airTemperature', minValue: 18, maxValue: 35, severity: 'warning', isActive: true },
  { id: 't3', farmId: 'f2', sensorType: 'ph', minValue: 5.8, maxValue: 7.2, severity: 'critical', isActive: true },
  { id: 't4', farmId: 'f3', sensorType: 'airHumidity', minValue: 45, maxValue: 85, severity: 'info', isActive: false },
]

const notifications: NotificationItem[] = [
  { id: 'nt1', title: 'Critical soil alert', message: 'North Rice moisture is below the configured minimum.', isRead: false, createdAt: daysAgo(0, 6) },
  { id: 'nt2', title: 'Mission in progress', message: 'Morning Mekong sweep is collecting waypoint 4 of 6.', isRead: false, createdAt: daysAgo(0, 7) },
  { id: 'nt3', title: 'Gateway linked', message: 'GW-02 is now associated with Falcon 2.', isRead: true, createdAt: daysAgo(1, 9) },
  { id: 'nt4', title: 'Node marked lost', message: 'Open Beds water node has not reported for 48 hours.', isRead: false, createdAt: daysAgo(2, 18) },
  { id: 'nt5', title: 'Mission completed', message: 'Can Tho afternoon pass stored 18 readings.', isRead: true, createdAt: daysAgo(1, 16) },
]

const users: User[] = [
  { id: 'u1', fullName: 'System Administrator', email: 'admin@farm.local', role: 'FarmAdministrator', phone: '0901000001' },
  { id: 'u2', fullName: 'Lan Pham', email: 'lan.pham@farm.local', role: 'FarmAdministrator', phone: '0901000002' },
  { id: 'u3', fullName: 'Minh Tran', email: 'minh.tran@farm.local', role: 'UavOperator', phone: '0901000003' },
  { id: 'u4', fullName: 'Hoa Nguyen', email: 'hoa.nguyen@farm.local', role: 'UavOperator', phone: '0901000004' },
]

const uavs: Uav[] = [
  { id: 'v1', code: 'UAV-01', name: 'Falcon 1', model: 'AgriX4', status: 'ready', batteryPercent: 86, latitude: 10.548, longitude: 106.41 },
  { id: 'v2', code: 'UAV-02', name: 'Falcon 2', model: 'AgriX4', status: 'in mission', batteryPercent: 64, latitude: 10.538, longitude: 106.422 },
  { id: 'v3', code: 'UAV-03', name: 'Heron', model: 'FieldM6', status: 'maintenance', batteryPercent: 100, latitude: 10.045, longitude: 105.71 },
  { id: 'v4', code: 'UAV-04', name: 'Kite', model: 'FieldM6', status: 'ready', batteryPercent: 91, latitude: 11.77, longitude: 108.58 },
]

const gateways: Gateway[] = [
  { id: 'g1', code: 'GW-01', name: 'Mekong Gateway A', uavId: 'v1', status: 'online' },
  { id: 'g2', code: 'GW-02', name: 'Mekong Gateway B', uavId: 'v2', status: 'online' },
  { id: 'g3', code: 'GW-03', name: 'Orchard Gateway', uavId: 'v3', status: 'standby' },
  { id: 'g4', code: 'GW-04', name: 'Highland Gateway', status: 'offline' },
]

const missions: Mission[] = [
  {
    id: 'm1',
    name: 'Morning Mekong sweep',
    farmId: 'f1',
    uavId: 'v2',
    gatewayId: 'g2',
    status: 'inProgress',
    scheduledAt: daysAgo(0, 6),
    notes: 'Wind is light. Operator is watching collection only.',
    waypointProgress: 67,
    collectionProgress: 58,
    batteryPercent: 64,
    latitude: 10.538,
    longitude: 106.422,
    sensorNodeIds: ['n1-1', 'n1-2', 'n2-1'],
    waypoints: [
      { order: 1, latitude: 10.55, longitude: 106.4, sensorNodeId: 'n1-1' },
      { order: 2, latitude: 10.548, longitude: 106.406, sensorNodeId: 'n1-2' },
      { order: 3, latitude: 10.536, longitude: 106.428, sensorNodeId: 'n2-1' },
    ],
  },
  {
    id: 'm2',
    name: 'Can Tho afternoon pass',
    farmId: 'f2',
    uavId: 'v1',
    gatewayId: 'g1',
    status: 'completed',
    scheduledAt: daysAgo(1, 14),
    notes: 'All planned nodes answered.',
    waypointProgress: 100,
    collectionProgress: 100,
    batteryPercent: 41,
    sensorNodeIds: ['n3-1', 'n3-2', 'n4-1'],
  },
  {
    id: 'm3',
    name: 'Highland spot check',
    farmId: 'f3',
    uavId: 'v4',
    gatewayId: 'g4',
    status: 'planned',
    scheduledAt: daysAgo(-1, 8),
    notes: 'Waiting for gateway to come online.',
    waypointProgress: 0,
    collectionProgress: 0,
    sensorNodeIds: ['n5-1'],
  },
  {
    id: 'm4',
    name: 'East bed retry',
    farmId: 'f1',
    uavId: 'v1',
    gatewayId: 'g1',
    status: 'failed',
    scheduledAt: daysAgo(2, 9),
    notes: 'Gateway lost the node halfway through the route.',
    waypointProgress: 40,
    collectionProgress: 25,
    batteryPercent: 72,
    sensorNodeIds: ['n2-1', 'n2-2'],
  },
  {
    id: 'm5',
    name: 'Pomelo moisture loop',
    farmId: 'f2',
    uavId: 'v3',
    gatewayId: 'g3',
    status: 'paused',
    scheduledAt: daysAgo(0, 11),
    notes: 'Paused while the UAV is in maintenance. No flight control from this screen.',
    waypointProgress: 20,
    collectionProgress: 15,
    sensorNodeIds: ['n3-1'],
  },
]

const results: MissionResult[] = [
  { id: 'r1', missionId: 'm2', sensorNodeId: 'n3-1', sensorCode: 'SN-Z3-1', success: true, message: 'Reading stored', collectedAt: daysAgo(1, 14) },
  { id: 'r2', missionId: 'm2', sensorNodeId: 'n3-2', sensorCode: 'SN-Z3-2', success: true, message: 'Reading stored', collectedAt: daysAgo(1, 14) },
  { id: 'r3', missionId: 'm2', sensorNodeId: 'n4-1', sensorCode: 'SN-Z4-1', success: false, message: 'Timeout waiting for node', collectedAt: daysAgo(1, 15) },
  { id: 'r4', missionId: 'm1', sensorNodeId: 'n1-1', sensorCode: 'SN-Z1-1', success: true, message: 'Partial sweep stored', collectedAt: daysAgo(0, 7) },
  { id: 'r5', missionId: 'm1', sensorNodeId: 'n1-2', sensorCode: 'SN-Z1-2', success: true, message: 'Partial sweep stored', collectedAt: daysAgo(0, 7) },
  { id: 'r6', missionId: 'm4', sensorNodeId: 'n2-1', sensorCode: 'SN-Z2-1', success: false, message: 'Link dropped', collectedAt: daysAgo(2, 9) },
  { id: 'r7', missionId: 'm5', sensorNodeId: 'n3-1', sensorCode: 'SN-Z3-1', success: false, message: 'Paused before collection', collectedAt: daysAgo(0, 11) },
]

let seq = 100
function nextId(prefix: string) {
  seq += 1
  return `${prefix}${seq}`
}

function wait<T>(value: T) {
  return Promise.resolve(value)
}

function farmName(id?: string) {
  return farms.find((farm) => farm.id === id)?.name
}

function zoneName(id?: string) {
  return zones.find((zone) => zone.id === id)?.name
}

function withZoneMeta(zone: Zone): Zone {
  return {
    ...zone,
    farmName: farmName(zone.farmId),
    sensorCount: nodes.filter((node) => node.zoneId === zone.id).length,
  }
}

function withNodeMeta(node: SensorNode): SensorNode {
  return {
    ...node,
    farmName: farmName(node.farmId),
    zoneName: zoneName(node.zoneId),
  }
}

function withGateway(gateway: Gateway): Gateway {
  return { ...gateway, uavName: uavs.find((uav) => uav.id === gateway.uavId)?.name }
}

function withMission(mission: Mission): Mission {
  return {
    ...mission,
    farmName: farmName(mission.farmId),
    uavName: uavs.find((uav) => uav.id === mission.uavId)?.name,
    gatewayName: gateways.find((gateway) => gateway.id === mission.gatewayId)?.name,
  }
}

export const mockApi = {
  farms: {
    list: () => wait(farms.map((farm) => ({ ...farm }))),
    get: (id: string) => wait({ ...farms.find((farm) => farm.id === id)! }),
    create: (body: Partial<Farm>) => {
      const farm: Farm = {
        id: nextId('f'),
        name: body.name || 'Untitled farm',
        address: body.address,
        areaHa: body.areaHa,
        latitude: body.latitude,
        longitude: body.longitude,
        description: body.description,
        isActive: body.isActive !== false,
        boundary:
          body.latitude && body.longitude ? box(body.latitude, body.longitude, 0.02) : undefined,
      }
      farms.unshift(farm)
      return wait({ ...farm })
    },
    update: (id: string, body: Partial<Farm>) => {
      const farm = farms.find((item) => item.id === id)
      if (farm) Object.assign(farm, body)
      return wait({ ...farm! })
    },
    setActive: (id: string, isActive: boolean) => {
      const farm = farms.find((item) => item.id === id)
      if (farm) farm.isActive = isActive
      return wait(undefined)
    },
  },
  zones: {
    list: (farmId?: string) =>
      wait(zones.filter((zone) => !farmId || zone.farmId === farmId).map(withZoneMeta)),
    create: (body: Partial<Zone>) => {
      const zone: Zone = {
        id: nextId('z'),
        farmId: body.farmId || farms[0].id,
        name: body.name || 'Untitled zone',
        cropType: body.cropType,
        latitude: body.latitude,
        longitude: body.longitude,
        isActive: true,
        boundary: body.latitude && body.longitude ? box(body.latitude, body.longitude, 0.006) : undefined,
      }
      zones.unshift(zone)
      return wait(withZoneMeta(zone))
    },
    update: (id: string, body: Partial<Zone>) => {
      const zone = zones.find((item) => item.id === id)
      if (zone) Object.assign(zone, body)
      return wait(withZoneMeta(zone!))
    },
    setActive: (id: string, isActive: boolean) => {
      const zone = zones.find((item) => item.id === id)
      if (zone) zone.isActive = isActive
      return wait(undefined)
    },
  },
  sensors: {
    list: (params?: { farmId?: string; zoneId?: string; sensorType?: string }) =>
      wait(
        nodes
          .filter((node) => !params?.farmId || node.farmId === params.farmId)
          .filter((node) => !params?.zoneId || node.zoneId === params.zoneId)
          .filter((node) => !params?.sensorType || node.sensorType === params.sensorType)
          .map(withNodeMeta),
      ),
    create: (body: Partial<SensorNode>) => {
      const sensorType = body.sensorType || 'soilMoisture'
      const node: SensorNode = {
        id: nextId('n'),
        code: body.code || `SN-${seq}`,
        name: body.name || 'New node',
        farmId: body.farmId || zones.find((zone) => zone.id === body.zoneId)?.farmId,
        zoneId: body.zoneId,
        sensorType,
        status: body.status || 'online',
        latitude: body.latitude,
        longitude: body.longitude,
        batteryPercent: body.batteryPercent ?? 80,
        lastSeenAt: new Date().toISOString(),
        lastValue: body.lastValue ?? bases[sensorType],
        unit: units[sensorType],
      }
      nodes.unshift(node)
      return wait(withNodeMeta(node))
    },
    update: (id: string, body: Partial<SensorNode>) => {
      const node = nodes.find((item) => item.id === id)
      if (node) Object.assign(node, body)
      return wait(withNodeMeta(node!))
    },
    readings: (params?: {
      sensorNodeId?: string
      sensorType?: string
      farmId?: string
      zoneId?: string
      from?: string
      to?: string
    }) => {
      const allowed = new Set(
        nodes
          .filter((node) => !params?.farmId || node.farmId === params.farmId)
          .filter((node) => !params?.zoneId || node.zoneId === params.zoneId)
          .map((node) => node.id),
      )
      return wait(
        readings
          .filter((reading) => allowed.has(reading.sensorNodeId))
          .filter((reading) => !params?.sensorNodeId || reading.sensorNodeId === params.sensorNodeId)
          .filter((reading) => !params?.sensorType || reading.sensorType === params.sensorType)
          .filter((reading) => !params?.from || reading.recordedAt.slice(0, 10) >= params.from)
          .filter((reading) => !params?.to || reading.recordedAt.slice(0, 10) <= params.to)
          .map((reading) => ({ ...reading, zoneName: zoneName(nodes.find((node) => node.id === reading.sensorNodeId)?.zoneId) })),
      )
    },
  },
  alerts: {
    list: (params?: { severity?: string; status?: string }) =>
      wait(
        alerts
          .filter((alert) => !params?.severity || alert.severity === params.severity)
          .filter((alert) => !params?.status || alert.status === params.status)
          .map((alert) => ({ ...alert })),
      ),
    updateStatus: (id: string, status: AlertItem['status']) => {
      const alert = alerts.find((item) => item.id === id)
      if (alert) alert.status = status
      return wait(undefined)
    },
    thresholds: () => wait(thresholds.map((item) => ({ ...item, farmName: farmName(item.farmId) }) as AlertThreshold)),
    saveThreshold: (body: Partial<AlertThreshold>) => {
      const item: AlertThreshold = {
        id: nextId('t'),
        farmId: body.farmId,
        sensorType: body.sensorType || 'soilMoisture',
        minValue: body.minValue,
        maxValue: body.maxValue,
        severity: body.severity || 'warning',
        isActive: body.isActive !== false,
      }
      thresholds.unshift(item)
      return wait({ ...item })
    },
  },
  notifications: {
    list: () => wait(notifications.map((item) => ({ ...item }))),
    markRead: (id: string) => {
      const item = notifications.find((notification) => notification.id === id)
      if (item) item.isRead = true
      return wait(undefined)
    },
  },
  reports: {
    summary: () =>
      wait({
        farmCount: farms.length,
        zoneCount: zones.length,
        readingCount: readings.length,
        alertCount: alerts.filter((alert) => alert.status !== 'closed').length,
        missionCount: missions.length,
      }),
  },
  users: {
    list: () => wait(users.map((user) => ({ ...user }))),
    create: (body: Partial<User> & { password?: string }) => {
      const user: User = {
        id: nextId('u'),
        fullName: body.fullName || 'New user',
        email: body.email || 'user@farm.local',
        role: body.role || 'UavOperator',
        phone: body.phone,
      }
      users.unshift(user)
      return wait({ ...user })
    },
  },
  account: {
    update: (body: Partial<User>) => wait({ ...users[0], ...body }),
  },
  uavs: {
    list: () => wait(uavs.map((uav) => ({ ...uav }))),
    create: (body: Partial<Uav>) => {
      const uav: Uav = {
        id: nextId('v'),
        code: body.code || `UAV-${seq}`,
        name: body.name || 'New UAV',
        model: body.model,
        status: body.status || 'ready',
        batteryPercent: 100,
      }
      uavs.unshift(uav)
      return wait({ ...uav })
    },
    update: (id: string, body: Partial<Uav>) => {
      const uav = uavs.find((item) => item.id === id)
      if (uav) Object.assign(uav, body)
      return wait({ ...uav! })
    },
  },
  gateways: {
    list: () => wait(gateways.map(withGateway)),
    create: (body: Partial<Gateway>) => {
      const gateway: Gateway = {
        id: nextId('g'),
        code: body.code || `GW-${seq}`,
        name: body.name || 'New gateway',
        uavId: body.uavId || undefined,
        status: body.status || 'standby',
      }
      gateways.unshift(gateway)
      return wait(withGateway(gateway))
    },
    update: (id: string, body: Partial<Gateway>) => {
      const gateway = gateways.find((item) => item.id === id)
      if (gateway) Object.assign(gateway, body, { uavId: body.uavId || undefined })
      return wait(withGateway(gateway!))
    },
  },
  missions: {
    list: () => wait(missions.map(withMission)),
    get: (id: string) => wait(withMission(missions.find((mission) => mission.id === id)!)),
    create: (body: Partial<Mission>) => {
      const mission: Mission = {
        id: nextId('m'),
        name: body.name || 'New mission',
        farmId: body.farmId,
        uavId: body.uavId,
        gatewayId: body.gatewayId,
        status: 'planned',
        scheduledAt: body.scheduledAt || new Date().toISOString(),
        notes: body.notes,
        sensorNodeIds: body.sensorNodeIds,
        waypoints: body.waypoints,
        waypointProgress: 0,
        collectionProgress: 0,
      }
      missions.unshift(mission)
      return wait(withMission(mission))
    },
    updateStatus: (id: string, status: Mission['status'], notes?: string) => {
      const mission = missions.find((item) => item.id === id)
      if (mission) {
        mission.status = status
        if (notes !== undefined) mission.notes = notes
      }
      return wait(undefined)
    },
    results: (id: string) => wait(results.filter((result) => result.missionId === id).map((result) => ({ ...result }))),
  },
  dashboard: {
    admin: (params?: { farmId?: string; range?: string }) => {
      const scoped = nodes.filter((node) => !params?.farmId || node.farmId === params.farmId)
      const points = [6, 5, 4, 3, 2, 1, 0].map((day) => {
        const tempNodes = scoped.filter((node) => node.sensorType === 'airTemperature')
        const moistNodes = scoped.filter((node) => node.sensorType === 'soilMoisture')
        const humidNodes = scoped.filter((node) => node.sensorType === 'airHumidity')
        const avg = (list: SensorNode[], fallback: number) => {
          if (list.length === 0) return fallback
          const raw = list.reduce((sum, node) => sum + (node.lastValue || 0), 0) / list.length
          return Number((raw + Math.sin(day) * 1.6).toFixed(1))
        }
        const swing = [1.8, -0.6, 2.4, -1.4, 0.8, 1.6, -0.2][day] ?? 0
        return {
          time: new Date(Date.now() - day * 86400000).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }),
          temperature: Number((avg(tempNodes, 29) + swing).toFixed(1)),
          moisture: Number((avg(moistNodes, 34) + swing * 1.4).toFixed(1)),
          humidity: Number((avg(humidNodes, 72) - swing).toFixed(1)),
        }
      })
      const count = (status: SensorNode['status']) => scoped.filter((node) => node.status === status).length
      return wait({
        totalFarms: params?.farmId ? 1 : farms.length,
        totalZones: zones.filter((zone) => !params?.farmId || zone.farmId === params.farmId).length,
        totalSensors: scoped.length,
        onlineSensors: count('online'),
        offlineSensors: count('offline'),
        activeAlerts: alerts.filter((alert) => alert.status !== 'closed').length,
        activeMissions: missions.filter((mission) => mission.status === 'inProgress' && (!params?.farmId || mission.farmId === params.farmId)).length,
        syncStatus: 'Synced 2 min ago',
        temperatureSeries: points,
        statusDistribution: [
          { name: 'Online', value: count('online') },
          { name: 'Offline', value: count('offline') },
          { name: 'Maintenance', value: count('maintenance') },
          { name: 'Lost', value: count('lost') },
        ],
      })
    },
    operator: () =>
      wait({
        sensorCount: nodes.length,
        uavCount: uavs.length,
        gatewayCount: gateways.length,
        activeMissions: missions.filter((mission) => mission.status === 'inProgress' || mission.status === 'paused').length,
      }),
  },
}
