import { MapContainer, Marker, Popup, Polygon, TileLayer, useMap } from 'react-leaflet'
import L from 'leaflet'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import { useEffect, type ReactNode } from 'react'

const defaultIcon = L.icon({
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
})

L.Marker.prototype.options.icon = defaultIcon

function FitPoints({ points }: { points: Point[] }) {
  const map = useMap()
  const signature = points.map((point) => point.id).join(',')

  useEffect(() => {
    if (points.length === 0) return
    const bounds = L.latLngBounds(points.map((point) => [point.lat, point.lng]))
    map.fitBounds(bounds.pad(0.25))
  }, [map, signature])

  return null
}

type Point = {
  id: string
  lat: number
  lng: number
  popup?: ReactNode
}

export function MapView({
  center = [10.76, 106.66],
  zoom = 12,
  points = [],
  polygons = [],
  height = 420,
}: {
  center?: [number, number]
  zoom?: number
  points?: Point[]
  polygons?: [number, number][][]
  height?: number
}) {
  return (
    <div style={{ height }} className="overflow-hidden rounded-2xl border border-line">
      <MapContainer center={center} zoom={zoom} scrollWheelZoom>
        <TileLayer attribution="&copy; OpenStreetMap" url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <FitPoints points={points} />
        {polygons.map((path, index) => (
          <Polygon key={index} positions={path} pathOptions={{ color: '#c45c26', fillColor: '#c45c26', fillOpacity: 0.15 }} />
        ))}
        {points.map((p) => (
          <Marker key={p.id} position={[p.lat, p.lng]}>
            {p.popup ? <Popup>{p.popup}</Popup> : null}
          </Marker>
        ))}
      </MapContainer>
    </div>
  )
}
