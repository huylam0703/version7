import axiosClient from '../axiosClient'
import type { Gateway, GatewayDto } from '../../types/gateway'
import { listAll } from '../pagination'

type Page<T> = {
  items: T[]
  page: number
  pageSize: number
  total: number
}

const lower = (s?: string) => s?.toLowerCase()

const upper = (s?: string) =>
  s?.toUpperCase().replace(/-/g, '_')


const toGateway = (g: GatewayDto): Gateway => ({
  id: g.gatewayId,
  code: g.gatewayUid,
  name: g.name,
  uavId: g.uavId ?? undefined,
  status: lower(g.status) as Gateway['status'],
  hwModel: g.hwModel ?? undefined,
  firmwareVer: g.firmwareVer ?? undefined,
  storageMb: g.storageMb ?? undefined,
})

export const gatewayApi = {
  list: async (
    uavId?: string,
    status?: Gateway['status']
  ): Promise<Gateway[]> =>
    (
      await listAll<GatewayDto>('/v1/gateways', {
        uavId: uavId || undefined,
        status: upper(status),
      })
    ).map(toGateway),

  get: async (id: string): Promise<Gateway> =>
    toGateway(
      (
        await axiosClient.get<GatewayDto>(
          `/v1/gateways/${id}`
        )
      ).data
    ),

  create: async (
    body: Partial<Gateway> & { authKey: string }
  ): Promise<Gateway> =>
    toGateway(
      (
        await axiosClient.post<GatewayDto>(
          '/v1/gateways',
          {
            gatewayUid: body.code,
            name: body.name,
            uavId: body.uavId || undefined,
            authKey: body.authKey,
            hwModel: body.hwModel,
            firmwareVer: body.firmwareVer,
            storageMb: body.storageMb,
          }
        )
      ).data
    ),

  update: async (
    id: string,
    body: Partial<Gateway>
  ): Promise<Gateway> =>
    toGateway(
      (
        await axiosClient.patch<GatewayDto>(
          `/v1/gateways/${id}`,
          {
            name: body.name,
            uavId: body.uavId || undefined,
            unassignUav:
              body.uavId === '' ? true : undefined,
            hwModel: body.hwModel,
            firmwareVer: body.firmwareVer,
            storageMb: body.storageMb,
            status: upper(body.status),
          }
        )
      ).data
    ),
}