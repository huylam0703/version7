import { useEffect, useState } from 'react'
import { dashboardApi, getErrorMessage, missionApi } from '../../api'
import type { Mission } from '../../types'
import { Card, ErrorBox, PageHeader, StatCard, Table, missionBadge } from '../../components/Ui'
import { formatDate } from '../../helpers'

export function OperatorDashboardPage() {
  const [data, setData] = useState<Record<string, unknown>>({})
  const [missions, setMissions] = useState<Mission[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([dashboardApi.operator().catch(() => ({})), missionApi.list().catch(() => [])])
      .then(([d, m]) => {
        setData(d as Record<string, unknown>)
        setMissions(m.slice(0, 8))
      })
      .catch((e) => setError(getErrorMessage(e)))
  }, [])

  return (
    <div>
      <PageHeader title="Operator dashboard" description="Equipment and collection missions. Flight control is not included." />
      <ErrorBox message={error} />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Sensor nodes" value={Number(data.sensorCount ?? 0)} />
        <StatCard label="UAVs" value={Number(data.uavCount ?? 0)} />
        <StatCard label="Gateways" value={Number(data.gatewayCount ?? 0)} />
        <StatCard label="Active missions" value={Number(data.activeMissions ?? 0)} />
      </div>
      <Card className="mt-4">
        <h3 className="mb-3 font-medium">Recent missions</h3>
        <Table headers={['Mission', 'Status', 'UAV', 'When']}>
          {missions.map((m) => (
            <tr key={m.id}>
              <td className="px-3 py-2">{m.name}</td>
              <td className="px-3 py-2">{missionBadge(m.status)}</td>
              <td className="px-3 py-2">{m.uavName || '—'}</td>
              <td className="px-3 py-2">{formatDate(m.scheduledAt)}</td>
            </tr>
          ))}
        </Table>
      </Card>
    </div>
  )
}
