import axiosClient from '../axiosClient'
import type{
    Farm
} from '../../types'
import { listAll } from '../pagination'
import type {
  FarmDto
} from '../../types/farm'

type Page<T> = { items: T[]; page: number; pageSize: number; total: number }

const toFarm = (f: FarmDto): Farm => ({
  id: f.farmId,
  name: f.name,
  address: f.address,
  areaHa: f.areaHa,
  latitude: f.centerLat,
  longitude: f.centerLng,
  description: f.description,
  isActive: f.status === 'ACTIVE',
})

const farmBody = (b: Partial<Farm>) => ({
  name: b.name,
  address: b.address,
  centerLat: b.latitude,
  centerLng: b.longitude,
  areaHa: b.areaHa,
  description: b.description,
})

const toStatus = (active?: boolean) => (active === false ? 'INACTIVE' : 'ACTIVE')
const lower = (s?: string) => s?.toLowerCase()
const upper = (s?: string) => s?.toUpperCase().replace(/-/g, '_')


export const farmApi = {
  list: async () => (await listAll<FarmDto>('/v1/farms')).map(toFarm),
  get: async (id: string) => toFarm((await axiosClient.get<FarmDto>(`/v1/farms/${id}`)).data),
  create: async (body: Partial<Farm>) =>
    toFarm((await axiosClient.post<FarmDto>('/v1/farms', farmBody(body))).data),
  update: async (id: string, body: Partial<Farm>) =>
    toFarm((await axiosClient.patch<FarmDto>(`/v1/farms/${id}`, farmBody(body))).data),
  setActive: async (id: string, isActive: boolean) =>
    toFarm((await axiosClient.patch<FarmDto>(`/v1/farms/${id}`, { status: toStatus(isActive) })).data),
}