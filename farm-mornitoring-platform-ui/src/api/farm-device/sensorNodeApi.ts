import axiosClient from '../axiosClient'
import { listAll } from '../pagination'
import type {
  SensorNodeDto,
  SensorNodeListParams,
  SensorNodePageDto,
  CreateNodeRequest,
  UpdateNodeRequest,
  RotateCredentialResponse,
} from '../../types/sensor'

export const sensorNodeApi = {
  list: async (
    params: SensorNodeListParams = {}
  ): Promise<SensorNodePageDto> => {
    const { data } = await axiosClient.get<SensorNodePageDto>(
      '/v1/nodes',
      {
        params: {
          page: 1,
          pageSize: 20,
          ...params,
        },
      }
    )

    return data
  },

  get: async (id: string): Promise<SensorNodeDto> => {
    const { data } = await axiosClient.get<SensorNodeDto>(
      `/v1/nodes/${id}`
    )

    return data
  },

  create: async (
    body: CreateNodeRequest
  ): Promise<SensorNodeDto> => {
    const { data } = await axiosClient.post<SensorNodeDto>(
      '/v1/nodes',
      body
    )

    return data
  },

  update: async (
    id: string,
    body: UpdateNodeRequest
  ): Promise<SensorNodeDto> => {
    const { data } = await axiosClient.patch<SensorNodeDto>(
      `/v1/nodes/${id}`,
      body
    )

    return data
  },

  rotateCredential: async (
    id: string,
    newAuthKey: string
  ): Promise<RotateCredentialResponse> => {
    const { data } =
      await axiosClient.post<RotateCredentialResponse>(
        `/v1/nodes/${id}/rotate-credential`,
        { newAuthKey }
      )

    return data
  },
}