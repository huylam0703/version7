import axiosClient from '../axiosClient'

import type {
  SensorNodeDto,
  SensorNodeListParams,
  SensorNodePageDto,
} from '../../types/sensor'
import type { SensorNode, SensorType } from '../../types'

const toSensorType = (code?: string): SensorType => {
  switch (code) {
    case 'SOIL_MOISTURE':
      return 'soilMoisture'
    case 'AIR_TEMP':
      return 'airTemperature'
    case 'AIR_HUMIDITY':
      return 'airHumidity'
    case 'LIGHT':
      return 'lightIntensity'
    case 'PH':
      return 'ph'
    case 'WATER_LEVEL':
      return 'waterLevel'
    default:
      return 'soilMoisture'
  }
}

const toSensorNode = (node: SensorNodeDto): SensorNode => ({
  id: node.nodeId,
  code: node.deviceUid,
  name: node.name,
  farmId: node.farmId,
  zoneId: node.zoneId,
  sensorType: toSensorType(node.channels[0]?.typeCode),
  status:
    node.status === 'ACTIVE' ? 'online' :
    node.status === 'MAINTENANCE' ? 'maintenance' :
    node.status === 'LOST' ? 'lost' :
    'offline',
  latitude: node.lat ?? undefined,
  longitude: node.lng ?? undefined,
  lastSeenAt: node.lastSeenAt ?? undefined,
})

export const sensorApi = {
  list: async (
    params: SensorNodeListParams = {},
  ): Promise<SensorNode[]> => {
    const { data } = await axiosClient.get<SensorNodePageDto>(
      '/v1/nodes',
      {
        params: {
          page: 1,
          pageSize: 100,
          ...params,
        },
      },
    )

    return data.items.map(toSensorNode)
  },

  get: async (nodeId: string): Promise<SensorNode> => {
    const { data } = await axiosClient.get<SensorNodeDto>(
      `/v1/nodes/${nodeId}`,
    )

    return toSensorNode(data)
  },
}