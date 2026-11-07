import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage, missionApi } from '../../api'
import type { Mission } from '../../types'
import { Button, Card, Empty, ErrorBox, PageHeader, Table, missionBadge } from '../../components/Ui'
import { formatDate } from '../../helpers'

export function MissionsPage() {
  const [items, setItems] = useState<Mission[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    missionApi.list().then(setItems).catch((e) => setError(getErrorMessage(e)))
  }, [])

  return (
    <div>
      <PageHeader
        title="Mission management"
        description="Plan and follow collection missions. The platform does not control flight."
        actions={<Link to="/missions/new"><Button>Create mission</Button></Link>}
      />
      <ErrorBox message={error} />
      <Card>
        {items.length === 0 ? <Empty text="No missions. Check API /api/missions" /> : (
          <Table headers={['Name', 'Farm', 'UAV', 'Status', 'When', 'Actions']}>
            {items.map((m) => (
              <tr key={m.id}>
                <td className="px-3 py-2">{m.name}</td>
                <td className="px-3 py-2">{m.farmName || '—'}</td>
                <td className="px-3 py-2">{m.uavName || '—'}</td>
                <td className="px-3 py-2">{missionBadge(m.status)}</td>
                <td className="px-3 py-2">{formatDate(m.scheduledAt)}</td>
                <td className="px-3 py-2 space-x-3">
                  <Link className="text-brand" to={`/missions/${m.id}`}>Monitor</Link>
                  <Link className="text-brand" to={`/missions/${m.id}/results`}>Results</Link>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </div>
  )
}
