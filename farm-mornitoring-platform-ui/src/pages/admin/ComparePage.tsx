import { useEffect, useState } from 'react'
import { Line, LineChart, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getErrorMessage, sensorApi, zoneApi } from '../../api'
import type { SensorReading, SensorType, Zone } from '../../types'
import { SENSOR_TYPE_LABEL } from '../../types'
import { Card, ErrorBox, Input, PageHeader, Select, StatCard } from '../../components/Ui'
import { statsFromValues } from '../../helpers'

export function ComparePage() {
  const [zones, setZones] = useState<Zone[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [sensorType, setSensorType] = useState<SensorType>('soilMoisture')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [byZone, setByZone] = useState<Record<string, SensorReading[]>>({})
  const [error, setError] = useState('')

  useEffect(() => {
    zoneApi.list().then((list) => {
      setZones(list)
      setSelected(list.slice(0, 2).map((zone) => zone.id))
    }).catch(() => setZones([]))
  }, [])

  useEffect(() => {
    if (selected.length === 0) {
      setByZone({})
      return
    }
    setError('')
    Promise.all(
      selected.map(async (zoneId) => {
        const data = await sensorApi.readings({ zoneId, sensorType, from: from || undefined, to: to || undefined })
        return [zoneId, data] as const
      }),
    )
      .then((pairs) => setByZone(Object.fromEntries(pairs)))
      .catch((e) => setError(getErrorMessage(e)))
  }, [selected, sensorType, from, to])

  function toggle(id: string) {
    setSelected((curr) => (curr.includes(id) ? curr.filter((x) => x !== id) : [...curr, id].slice(0, 4)))
  }

  const chartData: Record<string, string | number>[] = []
  selected.forEach((zoneId) => {
    (byZone[zoneId] || []).forEach((r) => {
      const key = r.recordedAt
      let row = chartData.find((x) => x.time === key)
      if (!row) {
        row = { time: key }
        chartData.push(row)
      }
      row[zoneId] = r.value
    })
  })

  return (
    <div>
      <PageHeader title="Data comparison" description="Compare the same sensor type across up to 4 zones." />
      <ErrorBox message={error} />
      <div className="mb-4 grid gap-2 md:grid-cols-3">
        <Select value={sensorType} onChange={(e) => setSensorType(e.target.value as SensorType)}>
          {Object.entries(SENSOR_TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </Select>
        <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
      </div>
      <Card className="mb-4">
        <p className="mb-2 text-sm font-medium">Select zones</p>
        <div className="flex flex-wrap gap-2">
          {zones.map((z) => (
            <button
              key={z.id}
              onClick={() => toggle(z.id)}
              className={`rounded-full border px-3 py-1 text-sm ${selected.includes(z.id) ? 'border-brand bg-brand-soft text-brand-dark' : 'border-gray-300'}`}
            >
              {z.name}
            </button>
          ))}
        </div>
      </Card>
      <Card className="mb-4">
        <div className="h-72">
          <ResponsiveContainer>
            <LineChart data={chartData}>
              <XAxis dataKey="time" hide />
              <YAxis />
              <Tooltip />
              <Legend />
              {selected.map((id, i) => (
                <Line key={id} type="monotone" dataKey={id} name={zones.find((z) => z.id === id)?.name || id} stroke={['#c45c26', '#1d4ed8', '#b45309', '#0f766e'][i]} strokeWidth={2} dot={false} />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </div>
      </Card>
      <div className="grid gap-3 md:grid-cols-2">
        {selected.map((id) => {
          const stats = statsFromValues((byZone[id] || []).map((r) => r.value))
          const name = zones.find((z) => z.id === id)?.name || id
          return <StatCard key={id} label={name} value={`avg ${stats.avg.toFixed(1)}`} hint={`min ${stats.min.toFixed(1)} / max ${stats.max.toFixed(1)}`} />
        })}
      </div>
    </div>
  )
}
