import { useEffect, useState } from 'react'
import { farmApi, getErrorMessage, sensorApi, zoneApi } from '../../api'
import type { Farm, SensorNode, SensorType, Zone } from '../../types'
import { SENSOR_TYPE_LABEL } from '../../types'
import { Card, ErrorBox, PageHeader, Select, sensorStatusBadge } from '../../components/Ui'
import { MapView } from '../../components/MapView'
import { formatDate, formatNumber } from '../../helpers'

export function SensorMonitorPage() {
  const [nodes, setNodes] = useState<SensorNode[]>([])
  const [farms, setFarms] = useState<Farm[]>([])
  const [zones, setZones] = useState<Zone[]>([])
  const [farmId, setFarmId] = useState('')
  const [zoneId, setZoneId] = useState('')
  const [type, setType] = useState<'' | SensorType>('')
  const [error, setError] = useState('')

  useEffect(() => {
    farmApi.list().then(setFarms).catch(() => setFarms([]))
  }, [])

  useEffect(() => {
    zoneApi.list(farmId || undefined).then(setZones).catch(() => setZones([]))
  }, [farmId])

  useEffect(() => {
    setError('')
    sensorApi
      .list({ farmId: farmId || undefined, zoneId: zoneId || undefined, sensorType: type || undefined })
      .then(setNodes)
      .catch((e) => setError(getErrorMessage(e)))
  }, [farmId, zoneId, type])

  const points = nodes
    .filter((n) => n.latitude && n.longitude)
    .map((n) => ({
      id: n.id,
      lat: n.latitude as number,
      lng: n.longitude as number,
      popup: (
        <div className="text-sm">
          <p className="font-medium">{n.name} ({n.code})</p>
          <p>Status: {n.status}</p>
          <p>Last seen: {formatDate(n.lastSeenAt)}</p>
          <p>Reading: {formatNumber(n.lastValue)} {n.unit}</p>
          <p>Battery: {n.batteryPercent ?? '—'}%</p>
          <p>Zone: {n.zoneName || '—'}</p>
        </div>
      ),
    }))

  return (
    <div>
      <PageHeader title="Sensor monitoring" description="Farm map with live sensor node status." />
      <ErrorBox message={error} />
      <div className="mb-4 grid gap-2 sm:grid-cols-3">
        <Select value={farmId} onChange={(e) => { setFarmId(e.target.value); setZoneId('') }}>
          <option value="">All farms</option>
          {farms.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
        </Select>
        <Select value={zoneId} onChange={(e) => setZoneId(e.target.value)}>
          <option value="">All zones</option>
          {zones.map((z) => <option key={z.id} value={z.id}>{z.name}</option>)}
        </Select>
        <Select value={type} onChange={(e) => setType(e.target.value as SensorType | '')}>
          <option value="">All sensor types</option>
          {Object.entries(SENSOR_TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </Select>
      </div>
      <MapView points={points} />
      <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {nodes.map((n) => (
          <Card key={n.id}>
            <div className="flex items-center justify-between">
              <p className="font-medium">{n.name}</p>
              {sensorStatusBadge(n.status)}
            </div>
            <p className="text-sm text-gray-500">{SENSOR_TYPE_LABEL[n.sensorType]}</p>
            <p className="mt-2 text-2xl font-semibold">{formatNumber(n.lastValue)} <span className="text-sm text-gray-500">{n.unit}</span></p>
            <p className="text-xs text-gray-400">Last seen {formatDate(n.lastSeenAt)}</p>
          </Card>
        ))}
      </div>
    </div>
  )
}
