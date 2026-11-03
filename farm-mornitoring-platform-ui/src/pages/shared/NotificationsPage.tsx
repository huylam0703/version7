import { useEffect, useState } from 'react'
import { getErrorMessage, notificationApi } from '../../api'
import type { NotificationItem } from '../../types'
import { Card, Empty, ErrorBox, PageHeader } from '../../components/Ui'
import { formatDate } from '../../helpers'

export function NotificationsPage() {
  const [items, setItems] = useState<NotificationItem[]>([])
  const [error, setError] = useState('')

  async function load() {
    try {
      setError('')
      setItems(await notificationApi.list())
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [])

  return (
    <div>
      <PageHeader title="Notifications" description="Web notifications from alerts and mission events." />
      <ErrorBox message={error} />
      <div className="space-y-2">
        {items.length === 0 ? <Card><Empty text="No notifications. Check API /api/notifications" /></Card> : items.map((n) => (
          <Card key={n.id} className={n.isRead ? 'opacity-70' : ''}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-medium">{n.title}</p>
                <p className="text-sm text-gray-500">{n.message}</p>
                <p className="mt-1 text-xs text-gray-400">{formatDate(n.createdAt)}</p>
              </div>
              {!n.isRead ? (
                <button className="text-sm text-brand" onClick={() => notificationApi.markRead(n.id).then(load)}>
                  Mark read
                </button>
              ) : null}
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
