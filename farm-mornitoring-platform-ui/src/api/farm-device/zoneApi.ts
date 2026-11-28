import axiosClient from '../axiosClient'
import type{
    Zone
} from '../../types'

import type {
  ZoneDto
} from '../../types/zone'

import type {
  FarmDto
} from '../../types/farm'

import { listAll } from '../pagination'

type Page<T> = { items: T[]; page: number; pageSize: number; total: number }

const toStatus = (active?: boolean) => (active === false ? 'INACTIVE' : 'ACTIVE')
const lower = (s?: string) => s?.toLowerCase()
const upper = (s?: string) => s?.toUpperCase().replace(/-/g, '_')


const toZone = (z: ZoneDto, farmName?: string): Zone => ({
  id: z.zoneId,
  farmId: z.farmId,
  farmName,
  name: z.name,
  cropType: z.cropType,
  latitude: z.centerLat,
  longitude: z.centerLng,
  boundaryGeojson: z.boundaryGeojson,
  isActive: z.status === 'ACTIVE',
})

// Backend bắt buộc có code, FE không có ô này nên tự sinh
const zoneCode = (name?: string) =>
  ((name || 'ZONE').toUpperCase().replace(/[^A-Z0-9]+/g, '').slice(0, 10) || 'ZONE') +
  '-' +
  Date.now().toString(36).slice(-4)

export const zoneApi = {
  list: async (farmId?: string) => {
    const farms = await listAll<FarmDto>('/v1/farms')
    const targets = farmId ? farms.filter((f) => f.farmId === farmId) : farms
    const groups = await Promise.all(
      targets.map(async (f) =>
        (await listAll<ZoneDto>(`/v1/farms/${f.farmId}/zones`)).map((z) => toZone(z, f.name)),
      ),
    )
    return groups.flat()
  },
  create: async (body: Partial<Zone>) => {
    if (!body.farmId) {
        throw new Error('Please select a farm.')
    }
    const farmId = body.farmId
    const res = await axiosClient.post<ZoneDto>(`/v1/farms/${farmId}/zones`, {
      code: zoneCode(body.name),
      name: body.name,
      cropType: body.cropType,
      centerLat: body.latitude,
      centerLng: body.longitude,
      boundaryGeojson: body.boundaryGeojson,
    })
    return toZone(res.data)
  },
  update: async (id: string, body: Partial<Zone>) =>
    toZone(
      (
        await axiosClient.patch<ZoneDto>(`/v1/zones/${id}`, {
          name: body.name,
          cropType: body.cropType,
          centerLat: body.latitude,
          centerLng: body.longitude,
          boundaryGeojson: body.boundaryGeojson,
        })
      ).data,
    ),
  setActive: async (id: string, isActive: boolean) =>
    toZone((await axiosClient.patch<ZoneDto>(`/v1/zones/${id}`, { status: toStatus(isActive) })).data),

  get: async (id: string) =>
  toZone(
    (await axiosClient.get<ZoneDto>(
      `/v1/zones/${id}`
    )).data
  ),
}