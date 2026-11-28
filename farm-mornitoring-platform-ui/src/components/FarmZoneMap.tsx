import { useCallback, useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import 'leaflet-draw'
import 'leaflet-draw/dist/leaflet.draw.css'
import { LayersControl, MapContainer, Marker, Polygon, Popup, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import type { Polygon as GeoPolygon } from 'geojson'
import { defaultFarmMapCenter, leafletRings, readBoundary, type Coordinates } from '../utils/farmZoneGeometry'
import { Button } from './Ui'

const pin = L.icon({ iconUrl: markerIcon, shadowUrl: markerShadow, iconSize: [25, 41], iconAnchor: [12, 41] })
const farmStyle = { color: '#f59e0b', fillOpacity: 0.08, weight: 3 }
const zoneStyle = { color: '#38bdf8', fillOpacity: 0.16, weight: 2 }
const draftStyle = { color: '#34d399', fillOpacity: 0.25, weight: 3 }

type NamedBoundary = { id: string; name: string; polygon: GeoPolygon }
type MapPoint = { id: string; lat: number; lng: number; popup?: string }
type BoundaryEditor = {
  value?: string | null
  onChange: (value: string | null) => void
  onBusyChange: (busy: boolean) => void
}

function BaseLayers({ onSatelliteError }: { onSatelliteError: (failed: boolean) => void }) {
  const map = useMap()
  const streetLayer = useRef<L.TileLayer>(null)
  return (
    <LayersControl position="topright">
      <LayersControl.BaseLayer name="Satellite" checked>
        <TileLayer
          attribution="Imagery &copy; Esri, Maxar, Earthstar Geographics, and the GIS User Community"
          url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
          maxNativeZoom={19}
          maxZoom={22}
          eventHandlers={{
            load: ({ target }) => { if (map.hasLayer(target)) onSatelliteError(false) },
            tileerror: ({ target }) => {
              if (!map.hasLayer(target) || !streetLayer.current) return
              map.removeLayer(target)
              streetLayer.current.addTo(map)
              onSatelliteError(true)
            },
          }}
        />
      </LayersControl.BaseLayer>
      <LayersControl.BaseLayer name="Street map">
        <TileLayer
          ref={streetLayer}
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          maxNativeZoom={19}
          maxZoom={22}
        />
      </LayersControl.BaseLayer>
    </LayersControl>
  )
}

function Viewport({
  fit, destination,
}: { fit?: string; destination?: { point: Coordinates } }) {
  const map = useMap()

  useEffect(() => {
    if (!fit) return
    const points: Coordinates[] = JSON.parse(fit)
    if (points.length) map.fitBounds(L.latLngBounds(points), { padding: [24, 24], maxZoom: 18, animate: false })
  }, [map, fit])

  useEffect(() => {
    if (destination) map.flyTo(destination.point, Math.max(map.getZoom(), 17))
  }, [map, destination])

  return null
}

function DrawingTools({ editor, onPoint, onDrawing }: {
  editor?: BoundaryEditor
  onPoint?: (point: Coordinates) => void
  onDrawing: (busy: boolean) => void
}) {
  const map = useMap()
  const group = useRef<L.FeatureGroup>(null)
  const busy = useRef(false)
  const onChange = editor?.onChange
  const onBusyChange = editor?.onBusyChange
  const value = editor?.value

  useMapEvents({
    click: ({ latlng }) => {
      if (!busy.current) onPoint?.([latlng.lat, L.Util.wrapNum(latlng.lng, [-180, 180], true)])
    },
  })

  useEffect(() => {
    if (!onChange) return
    const layers = L.featureGroup().addTo(map)
    group.current = layers
    const editOptions = { featureGroup: layers, poly: { allowIntersection: false } }
    const control = new L.Control.Draw({
      position: 'topleft',
      draw: {
        polygon: { allowIntersection: false, shapeOptions: draftStyle },
        polyline: false, rectangle: false, circle: false, marker: false, circlemarker: false,
      },
      edit: editOptions,
    })
    const saveBoundary = (layer: L.Polygon) => onChange(JSON.stringify(layer.toGeoJSON(false).geometry))
    const created = (event: L.LeafletEvent) => saveBoundary((event as L.DrawEvents.Created).layer as L.Polygon)
    const edited = (event: L.LeafletEvent) => (event as L.DrawEvents.Edited).layers.eachLayer(layer => {
      if (layer instanceof L.Polygon) saveBoundary(layer)
    })
    const deleted = () => onChange(null)
    const setBusy = (active: boolean) => {
      busy.current = active
      onBusyChange?.(active)
      onDrawing(active)
    }
    const start = () => setBusy(true)
    const stop = () => setBusy(false)
    const starts = 'draw:drawstart draw:editstart draw:deletestart'
    const stops = 'draw:drawstop draw:editstop draw:deletestop'

    map.addControl(control)
    map.on('draw:created', created).on('draw:edited', edited).on('draw:deleted', deleted)
    map.on(starts, start).on(stops, stop)
    return () => {
      map.off('draw:created', created).off('draw:edited', edited).off('draw:deleted', deleted)
      map.off(starts, start).off(stops, stop)
      map.removeControl(control)
      map.removeLayer(layers)
      group.current = null
      setBusy(false)
    }
  }, [map, onChange, onBusyChange, onDrawing])

  useEffect(() => {
    const layers = group.current
    if (!layers) return
    layers.clearLayers()
    const polygon = readBoundary({ boundaryGeojson: value })
    if (polygon) L.polygon(leafletRings(polygon), draftStyle).addTo(layers)
  }, [map, value, onChange, onBusyChange, onDrawing])

  return null
}

export function FarmZoneMap({
  center, fitBoundary, fitPoints, points = [], farm, zones = [], editor, onPickCenter, destination, height = 420,
}: {
  center: Coordinates
  fitBoundary?: GeoPolygon
  fitPoints?: boolean
  points?: MapPoint[]
  farm?: NamedBoundary
  zones?: NamedBoundary[]
  editor?: BoundaryEditor
  onPickCenter?: (point: Coordinates) => void
  destination?: { point: Coordinates }
  height?: number
}) {
  const [satelliteError, setSatelliteError] = useState(false)
  const [picking, setPicking] = useState(false)
  const [drawing, setDrawing] = useState(false)
  const onDrawing = useCallback((active: boolean) => {
    setDrawing(active)
    if (active) setPicking(false)
  }, [])
  const fit = fitBoundary ? leafletRings(fitBoundary)[0] : fitPoints ? [
    ...zones.flatMap(zone => leafletRings(zone.polygon)[0]),
    ...points.map(({ lat, lng }): Coordinates => [lat, lng]),
  ] : undefined

  return (
    <div className="space-y-2">
      {editor && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm text-muted">
          {onPickCenter && (
            <Button variant={picking && !drawing ? 'primary' : 'outline'} disabled={drawing}
              onClick={() => setPicking(!picking)}>
              {picking && !drawing ? 'Cancel center selection' : 'Pick center on map'}
            </Button>
          )}
          <span>{picking && !drawing ? 'Click a point on the map.' :
            'Use the map toolbar to draw, edit or delete the boundary. Click the first vertex to finish drawing.'}</span>
        </div>
      )}
      <div className="relative isolate overflow-hidden rounded-2xl border border-line" style={{ height }}>
        <MapContainer center={center} zoom={center === defaultFarmMapCenter ? 12 : 16} maxZoom={22} zoomAnimation={false}>
          <BaseLayers onSatelliteError={setSatelliteError} />
          <Viewport fit={fit?.length ? JSON.stringify(fit) : undefined} destination={destination} />
          <DrawingTools editor={editor} onDrawing={onDrawing} onPoint={picking ? point => {
            onPickCenter?.(point)
            setPicking(false)
          } : undefined} />
          {farm && <Polygon positions={leafletRings(farm.polygon)} pathOptions={farmStyle} interactive={!editor}>
            {!editor && <Popup>{farm.name}</Popup>}
          </Polygon>}
          {zones.map(zone => (
            <Polygon key={zone.id} positions={leafletRings(zone.polygon)} pathOptions={zoneStyle} interactive={!editor}>
              {!editor && <Popup>{zone.name}</Popup>}
            </Polygon>
          ))}
          {points.map(point => (
            <Marker key={point.id} position={[point.lat, point.lng]} icon={pin} interactive={!editor}>
              {point.popup && !editor && <Popup>{point.popup}</Popup>}
            </Marker>
          ))}
        </MapContainer>
      </div>
      {(farm || zones.length > 0 || editor) && (
        <p className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
          {farm && <span><span className="text-amber-600" aria-hidden="true">■ </span>Farm boundary</span>}
          {zones.length > 0 && <span><span className="text-sky-600" aria-hidden="true">■ </span>Existing zones</span>}
          {editor && <span><span className="text-emerald-600" aria-hidden="true">■ </span>Boundary being edited</span>}
        </p>
      )}
      {satelliteError && <p role="status" className="text-sm text-amber-800">Satellite imagery could not load. The street map is shown instead.</p>}
    </div>
  )
}
