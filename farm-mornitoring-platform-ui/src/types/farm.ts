export type FarmDto = {
  farmId: string
  name: string
  address?: string
  centerLat?: number
  centerLng?: number
  areaHa?: number
  boundaryGeojson?: string | null
  description?: string
  status: string
}