import axiosClient from '../axiosClient'
import type {
  Uav,
  UavDto,
  UavStatus,
} from '../../types/uav'
import { listAll } from '../pagination'

interface UavPage {
  items: UavDto[]
  page: number
  pageSize: number
  total: number
}

const toUav = (u: UavDto): Uav => ({
  id: u.uavId,
  farmId: u.farmId,
  code: u.serialNo,
  name: u.name,
  model: u.model,
  maxFlightMin: u.maxFlightMin,
  status: u.status.toLowerCase() as UavStatus,
})

export const uavApi = {
  list: async (farmId?: string): Promise<Uav[]> => {
    const params = {
      farmId,
      page: 1,
      pageSize: 100,
    }

    const { data } = await axiosClient.get<UavPage>(
      '/v1/uavs',
      { params }
    )

    const items = [...data.items]
    const totalPages = Math.ceil(data.total / data.pageSize)

    for (let page = 2; page <= totalPages; page++) {
      const result = await axiosClient.get<UavPage>(
        '/v1/uavs',
        {
          params: {
            ...params,
            page,
          },
        }
      )

      items.push(...result.data.items)
    }

    return items.map(toUav)
  },

  get: async (id: string): Promise<Uav> => {
    const { data } = await axiosClient.get<UavDto>(
      `/v1/uavs/${id}`
    )

    return toUav(data)
  },

  create: async (body: Partial<Uav>): Promise<Uav> => {
    const { data } = await axiosClient.post<UavDto>(
      '/v1/uavs',
      {
        farmId: body.farmId,
        serialNo: body.code,
        name: body.name,
        model: body.model,
        maxFlightMin: body.maxFlightMin,
      }
    )

    return toUav(data)
  },

  update: async (
    id: string,
    body: Partial<Uav>
  ): Promise<Uav> => {
    const { data } = await axiosClient.patch<UavDto>(
      `/v1/uavs/${id}`,
      {
        name: body.name,
        model: body.model,
        maxFlightMin: body.maxFlightMin,
        status: body.status?.toUpperCase(),
      }
    )

    return toUav(data)
  },
}
