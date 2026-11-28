export type ZoneDto = {
  zoneId: string
  farmId: string
  code: string
  name: string
  cropType?: string
  centerLat?: number
  centerLng?: number
  boundaryGeojson?: string | null
  status: string
}