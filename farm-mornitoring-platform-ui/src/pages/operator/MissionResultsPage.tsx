import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getErrorMessage, missionApi } from '../../api'
import type { MissionResult } from '../../types'
import { Badge, Card, Empty, ErrorBox, PageHeader, Table } from '../../components/Ui'
import { formatDate } from '../../helpers'

export function MissionResultsPage() {
  const { id } = useParams()
  const [items, setItems] = useState<MissionResult[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    if (!id) return
    missionApi.results(id).then(setItems).catch((e) => setError(getErrorMessage(e)))
  }, [id])

  const ok = items.filter((i) => i.success).length
  const fail = items.length - ok

  return (
    <div>
      <PageHeader title="Mission results" description={`Successful: ${ok} · Failed: ${fail}`} />
      <ErrorBox message={error} />
      <Card>
        {items.length === 0 ? <Empty text="No collection results yet." /> : (
          <Table headers={['Node', 'Result', 'Message', 'Time']}>
            {items.map((r) => (
              <tr key={r.id}>
                <td className="px-3 py-2">{r.sensorCode || r.sensorNodeId}</td>
                <td className="px-3 py-2">{r.success ? <Badge color="green">Success</Badge> : <Badge color="red">Failed</Badge>}</td>
                <td className="px-3 py-2">{r.message || '—'}</td>
                <td className="px-3 py-2">{formatDate(r.collectedAt)}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </div>
  )
}
