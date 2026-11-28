import axiosClient from '../axiosClient'
import type { SensorTypeDto } from '../../types/sensor'

export const sensorTypeApi = {
  list: async (): Promise<SensorTypeDto[]> => {
    const { data } = await axiosClient.get<SensorTypeDto[]>(
      '/v1/sensor-types'
    )

    return data
  },
}