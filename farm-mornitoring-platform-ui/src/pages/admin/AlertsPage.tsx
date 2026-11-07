import { useEffect, useState } from 'react'
import { alertApi, getErrorMessage } from '../../api'
import type { AlertItem, AlertSeverity, AlertStatus } from '../../types'
import { Button, Card, Empty, ErrorBox, PageHeader, Select, Table, alertBadge } from '../../components/Ui'
import { formatDate } from '../../helpers'

export function AlertsPage() {
  const [items, setItems] = useState<AlertItem[]>([])
  const [severity, setSeverity] = useState<'' | AlertSeverity>('')
  const [status, setStatus] = useState<'' | AlertStatus>('')
  const [error, setError] = useState('')

  async function load() {
    try {
      setError('')
      setItems(await alertApi.list({ severity: severity || undefined, status: status || undefined }))
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [severity, status])

  async function setAlertStatus(id: string, next: AlertStatus) {
    try {
      await alertApi.updateStatus(id, next)
      await load()
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  return (
    <div>
      <PageHeader title="Alert management" description="Review, acknowledge and close farm alerts." />
      <ErrorBox message={error} />
      <div className="mb-4 flex max-w-lg gap-2">
        <Select value={severity} onChange={(e) => setSeverity(e.target.value as AlertSeverity | '')}>
          <option value="">All severities</option>
          <option value="critical">Critical</option>
          <option value="warning">Warning</option>
          <option value="info">Info</option>
        </Select>
        <Select value={status} onChange={(e) => setStatus(e.target.value as AlertStatus | '')}>
          <option value="">All statuses</option>
          <option value="open">Open</option>
          <option value="acknowledged">Acknowledged</option>
          <option value="closed">Closed</option>
        </Select>
      </div>
      <Card>
        {items.length === 0 ? <Empty text="No alerts. Check API /api/alerts" /> : (
          <Table headers={['Alert', 'Farm / Zone', 'Severity', 'Status', 'Time', 'Actions']}>
            {items.map((a) => (
              <tr key={a.id}>
                <td className="px-3 py-2">
                  <p className="font-medium">{a.title}</p>
                  <p className="text-xs text-gray-500">{a.message}</p>
                </td>
                <td className="px-3 py-2">{a.farmName || '—'} / {a.zoneName || '—'}</td>
                <td className="px-3 py-2">{alertBadge(a.severity)}</td>
                <td className="px-3 py-2">{a.status}</td>
                <td className="px-3 py-2">{formatDate(a.createdAt)}</td>
                <td className="px-3 py-2 space-x-2">
                  {a.status === 'open' ? <Button variant="outline" onClick={() => setAlertStatus(a.id, 'acknowledged')}>Acknowledge</Button> : null}
                  {a.status !== 'closed' ? <Button variant="ghost" onClick={() => setAlertStatus(a.id, 'closed')}>Close</Button> : null}
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </div>
  )
}
