import { useEffect, useState } from 'react'
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'
import { alertApi, dashboardApi, farmApi, getErrorMessage, missionApi } from '../../api'
import type { AlertItem, Farm, Mission } from '../../types'
import { Card, ErrorBox, PageHeader, Select, StatCard, Table, alertBadge, missionBadge } from '../../components/Ui'
import { formatDate } from '../../helpers'

const PIE_COLORS = ['#0f766e', '#a8a29e', '#d97706', '#dc2626']

export function DashboardPage() {
  const [farms, setFarms] = useState<Farm[]>([])
  const [farmId, setFarmId] = useState('')
  const [range, setRange] = useState('7d')
  const [data, setData] = useState<Record<string, unknown>>({})
  const [alerts, setAlerts] = useState<AlertItem[]>([])
  const [missions, setMissions] = useState<Mission[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    farmApi.list().then(setFarms).catch(() => setFarms([]))
  }, [])

  useEffect(() => {
    setError('')
    Promise.all([
      dashboardApi.admin({ farmId: farmId || undefined, range }).catch(() => ({})),
      alertApi.list().catch(() => []),
      missionApi.list().catch(() => []),
    ])
      .then(([dash, a, m]) => {
        setData(dash as Record<string, unknown>)
        setAlerts(a.slice(0, 5))
        setMissions(m.slice(0, 5))
      })
      .catch((e) => setError(getErrorMessage(e)))
  }, [farmId, range])

  const readings = (data.temperatureSeries || data.readings || []) as { time?: string; recordedAt?: string; value?: number; temperature?: number; moisture?: number; humidity?: number }[]
  const chartData = readings.map((r) => ({
    time: r.time || r.recordedAt || '',
    temperature: r.temperature ?? r.value,
    moisture: r.moisture,
    humidity: r.humidity,
  }))
  const statusDist = (data.statusDistribution || [
    { name: 'Online', value: Number(data.onlineSensors || 0) },
    { name: 'Offline', value: Number(data.offlineSensors || 0) },
  ]) as { name: string; value: number }[]

  return (
    <div>
      <PageHeader
        title="Farm dashboard"
        description="Overview of farms, sensors, alerts and UAV collection missions."
        actions={
          <div className="flex gap-2">
            <Select value={farmId} onChange={(e) => setFarmId(e.target.value)}>
              <option value="">All farms</option>
              {farms.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </Select>
            <Select value={range} onChange={(e) => setRange(e.target.value)}>
              <option value="24h">Last 24 hours</option>
              <option value="7d">Last 7 days</option>
              <option value="30d">Last 30 days</option>
            </Select>
          </div>
        }
      />
      <ErrorBox message={error} />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Farms" value={Number(data.totalFarms ?? farms.length)} />
        <StatCard label="Zones" value={Number(data.totalZones ?? 0)} />
        <StatCard label="Sensor nodes" value={Number(data.totalSensors ?? 0)} />
        <StatCard label="Online sensors" value={Number(data.onlineSensors ?? 0)} />
        <StatCard label="Offline sensors" value={Number(data.offlineSensors ?? 0)} />
        <StatCard label="Active alerts" value={Number(data.activeAlerts ?? alerts.filter((a) => a.status !== 'closed').length)} />
        <StatCard label="Active UAV missions" value={Number(data.activeMissions ?? missions.filter((m) => m.status === 'inProgress').length)} />
        <StatCard label="Sync status" value="Synced" hint="2 minutes ago" />
      </div>

      <div className="mt-6 grid gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <h3 className="mb-3 font-medium">Temperature</h3>
          <div className="h-56">
            <ResponsiveContainer>
              <LineChart data={chartData}>
                <XAxis dataKey="time" hide />
                <YAxis />
                <Tooltip />
                <Line type="monotone" dataKey="temperature" stroke="#c45c26" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card>
          <h3 className="mb-3 font-medium">Sensor status</h3>
          <div className="h-56">
            <ResponsiveContainer>
              <PieChart>
                <Pie data={statusDist} dataKey="value" nameKey="name" innerRadius={40} outerRadius={70}>
                  {statusDist.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card>
          <h3 className="mb-3 font-medium">Soil moisture</h3>
          <div className="h-48">
            <ResponsiveContainer>
              <LineChart data={chartData}>
                <XAxis dataKey="time" hide />
                <YAxis />
                <Tooltip />
                <Line type="monotone" dataKey="moisture" stroke="#1d4ed8" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card>
          <h3 className="mb-3 font-medium">Air humidity</h3>
          <div className="h-48">
            <ResponsiveContainer>
              <LineChart data={chartData}>
                <XAxis dataKey="time" hide />
                <YAxis />
                <Tooltip />
                <Line type="monotone" dataKey="humidity" stroke="#b45309" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <Card>
          <h3 className="mb-3 font-medium">Recent alerts</h3>
          <Table headers={['Alert', 'Severity', 'Time']}>
            {alerts.map((a) => (
              <tr key={a.id}>
                <td className="px-3 py-2">{a.title}</td>
                <td className="px-3 py-2">{alertBadge(a.severity)}</td>
                <td className="px-3 py-2">{formatDate(a.createdAt)}</td>
              </tr>
            ))}
          </Table>
        </Card>
        <Card>
          <h3 className="mb-3 font-medium">Recent missions</h3>
          <Table headers={['Mission', 'Status', 'Farm']}>
            {missions.map((m) => (
              <tr key={m.id}>
                <td className="px-3 py-2">{m.name}</td>
                <td className="px-3 py-2">{missionBadge(m.status)}</td>
                <td className="px-3 py-2">{m.farmName || '—'}</td>
              </tr>
            ))}
          </Table>
        </Card>
      </div>
    </div>
  )
}
