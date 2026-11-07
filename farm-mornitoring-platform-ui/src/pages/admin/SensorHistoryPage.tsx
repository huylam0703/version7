import { useEffect, useState } from 'react'
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { farmApi, getErrorMessage, sensorApi, zoneApi } from '../../api'
import type { Farm, SensorNode, SensorReading, SensorType, Zone } from '../../types'
import { SENSOR_TYPE_LABEL } from '../../types'
import { Button, Card, ErrorBox, Input, PageHeader, Select, StatCard, Table } from '../../components/Ui'
import { downloadCsv, formatDate, formatNumber, statsFromValues } from '../../helpers'

export function SensorHistoryPage() {
  const [farms, setFarms] = useState<Farm[]>([])
  const [zones, setZones] = useState<Zone[]>([])
  const [nodes, setNodes] = useState<SensorNode[]>([])
  const [readings, setReadings] = useState<SensorReading[]>([])
  const [farmId, setFarmId] = useState('')
  const [zoneId, setZoneId] = useState('')
  const [nodeId, setNodeId] = useState('')
  const [sensorType, setSensorType] = useState<SensorType>('soilMoisture')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [error, setError] = useState('')

  useEffect(() => { farmApi.list().then(setFarms).catch(() => setFarms([])) }, [])
  useEffect(() => { zoneApi.list(farmId || undefined).then(setZones).catch(() => setZones([])) }, [farmId])
  useEffect(() => { sensorApi.list({ farmId, zoneId }).then(setNodes).catch(() => setNodes([])) }, [farmId, zoneId])

  async function load() {
    try {
      setError('')
      const data = await sensorApi.readings({
        sensorNodeId: nodeId || undefined,
        sensorType,
        farmId: farmId || undefined,
        zoneId: zoneId || undefined,
        from: from || undefined,
        to: to || undefined,
      })
      setReadings(data)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [nodeId, sensorType, farmId, zoneId])

  const values = readings.map((r) => r.value)
  const stats = statsFromValues(values)
  const latest = readings[readings.length - 1]

  return (
    <div>
      <PageHeader
        title="Sensor history"
        description="Historical readings, stats and CSV export."
        actions={<Button onClick={() => downloadCsv('sensor-history.csv', [['Time', 'Value', 'Unit'], ...readings.map((r) => [r.recordedAt, String(r.value), r.unit || ''])])}>Export CSV</Button>}
      />
      <ErrorBox message={error} />
      <div className="mb-4 grid gap-2 md:grid-cols-3 xl:grid-cols-6">
        <Select value={farmId} onChange={(e) => setFarmId(e.target.value)}>
          <option value="">All farms</option>
          {farms.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
        </Select>
        <Select value={zoneId} onChange={(e) => setZoneId(e.target.value)}>
          <option value="">All zones</option>
          {zones.map((z) => <option key={z.id} value={z.id}>{z.name}</option>)}
        </Select>
        <Select value={nodeId} onChange={(e) => setNodeId(e.target.value)}>
          <option value="">All nodes</option>
          {nodes.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
        </Select>
        <Select value={sensorType} onChange={(e) => setSensorType(e.target.value as SensorType)}>
          {Object.entries(SENSOR_TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </Select>
        <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        <Input type="date" value={to} onChange={(e) => { setTo(e.target.value); load() }} />
      </div>
      <div className="grid gap-3 sm:grid-cols-4">
        <StatCard label="Min" value={formatNumber(stats.min)} />
        <StatCard label="Max" value={formatNumber(stats.max)} />
        <StatCard label="Average" value={formatNumber(stats.avg)} />
        <StatCard label="Latest" value={latest ? formatNumber(latest.value) : '—'} hint={formatDate(latest?.recordedAt)} />
      </div>
      <Card className="mt-4">
        <div className="h-64">
          <ResponsiveContainer>
            <LineChart data={readings}>
              <XAxis dataKey="recordedAt" hide />
              <YAxis />
              <Tooltip />
              <Line type="monotone" dataKey="value" stroke="#c45c26" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </Card>
      <Card className="mt-4">
        <Table headers={['Time', 'Value', 'Zone']}>
          {readings.map((r) => (
            <tr key={r.id}>
              <td className="px-3 py-2">{formatDate(r.recordedAt)}</td>
              <td className="px-3 py-2">{r.value} {r.unit}</td>
              <td className="px-3 py-2">{r.zoneName || '—'}</td>
            </tr>
          ))}
        </Table>
      </Card>
    </div>
  )
}
