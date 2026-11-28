import {
  area, booleanPointInPolygon, booleanValid, centerOfMass, difference,
  feature, featureCollection, intersect, kinks, tesselate,
} from '@turf/turf'
import type { Polygon } from 'geojson'

export type Coordinates = [lat: number, lng: number]

export function isCoordinate(lat: unknown, lng: unknown): boolean {
  return typeof lat === 'number' && Number.isFinite(lat) && Math.abs(lat) <= 90 &&
    typeof lng === 'number' && Number.isFinite(lng) && Math.abs(lng) <= 180
}

export function readBoundary(record?: { boundaryGeojson?: string | null; boundary?: Coordinates[] } | null): Polygon | undefined {
  if (!record) return
  if (record.boundaryGeojson) {
    try {
      const polygon = JSON.parse(record.boundaryGeojson)
      if (polygon?.type === 'Polygon' && Array.isArray(polygon.coordinates) &&
        polygon.coordinates.length > 0 && polygon.coordinates.every((ring: unknown) =>
          Array.isArray(ring) && ring.length >= 4 && ring.every((point: unknown) =>
            Array.isArray(point) && point.length === 2 && isCoordinate(point[1], point[0])) &&
          ring[0][0] === ring.at(-1)[0] && ring[0][1] === ring.at(-1)[1])) {
        return { type: 'Polygon', coordinates: polygon.coordinates }
      }
    } catch {
      return
    }
  } else if (record.boundary && record.boundary.length >= 3) {
    const coordinates = record.boundary.map(([lat, lng]) => [lng, lat])
    const first = coordinates[0]
    const last = coordinates[coordinates.length - 1]
    if (first[0] !== last[0] || first[1] !== last[1]) coordinates.push([...first])
    return { type: 'Polygon', coordinates: [coordinates] }
  }
}

export const defaultFarmMapCenter: Coordinates = [10.76, 106.66]

export function namedBoundary(item: { id: string; name: string; boundaryGeojson?: string | null; boundary?: Coordinates[] }) {
  const polygon = readBoundary(item)
  return polygon ? { id: item.id, name: item.name, polygon } : undefined
}

export function leafletRings(polygon: Polygon): Coordinates[][] {
  return polygon.coordinates.map(ring => ring.map(([lng, lat]) => [lat, lng]))
}

export function areaInHectares(polygon: Polygon): number {
  return area(polygon) / 10_000
}

export function isValidBoundary(polygon?: Polygon): polygon is Polygon {
  return !!polygon && polygon.coordinates.every(ring =>
    new Set(ring.slice(0, -1).map(([lng, lat]) => `${lng},${lat}`)).size === ring.length - 1
  ) && booleanValid(polygon) && kinks(polygon).features.length === 0 && area(polygon) > 0
}

export function containsCenter(polygon: Polygon, [lat, lng]: Coordinates): boolean {
  return booleanPointInPolygon([lng, lat], polygon, { ignoreBoundary: true })
}

export function boundaryCenter(polygon: Polygon): Coordinates {
  const [lng, lat] = centerOfMass(polygon).geometry.coordinates
  if (containsCenter(polygon, [lat, lng])) return [lat, lng]

  // A triangle centroid stays inside even when the polygon is concave or has holes.
  const triangles = tesselate(feature(polygon)).features
  const largest = triangles.reduce((best, triangle) => area(triangle) > area(best) ? triangle : best)
  const [insideLng, insideLat] = centerOfMass(largest).geometry.coordinates
  return [insideLat, insideLng]
}

export function zoneBoundaryError(zone: Polygon, farm: Polygon, others: { name: string; polygon: Polygon }[]): string {
  if (difference(featureCollection([feature(zone), feature(farm)]))) {
    return 'The zone must be entirely within the farm boundary.'
  }

  const overlap = others.find(({ polygon }) =>
    intersect(featureCollection([feature(zone), feature(polygon)]))
  )
  return overlap ? `The zone overlaps “${overlap.name}”.` : ''
}
