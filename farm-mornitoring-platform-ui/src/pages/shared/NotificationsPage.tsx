import { useEffect, useRef, useState } from 'react'
import { getErrorMessage, notificationApi } from '../../api'
import type { NotificationItem } from '../../types'
import { Button, Card, Empty, ErrorBox, PageHeader } from '../../components/Ui'
import { formatDate } from '../../helpers'

export function NotificationsPage() {
  const [items, setItems] = useState<NotificationItem[]>([])
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const latestLoad = useRef(0)

  async function load(showError = true) {
    const request = ++latestLoad.current
    try {
      const notifications = await notificationApi.list()
      if (request === latestLoad.current) {
        setItems(notifications)
        if (showError) setError('')
      }
    } catch (e) {
      if (request === latestLoad.current && showError) setError(getErrorMessage(e))
    }
  }

  useEffect(() => {
    void load()
    const interval = window.setInterval(() => void load(false), 10000)
    return () => {
      window.clearInterval(interval)
      latestLoad.current++
    }
  }, [])

  async function change(action: () => Promise<unknown>) {
    setPending(true)
    setError('')
    try {
      await action()
      window.dispatchEvent(new Event('notifications-changed'))
      await load()
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setPending(false)
    }
  }

  return (
    <div>
      <PageHeader title="Notifications"
        actions={<Button disabled={pending || !items.some((item) => !item.isRead)}
          onClick={() => change(notificationApi.markAllRead)}>Mark all read</Button>} />
      <ErrorBox message={error} />
      <div className="space-y-2">
        {items.length === 0 ? <Card><Empty text="No notifications." /></Card> : items.map((n) => (
          <Card key={n.id} className={n.isRead ? 'opacity-70' : ''}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-medium">{n.title}</p>
                <p className="text-sm text-gray-500">{n.message}</p>
                <p className="mt-1 text-xs text-gray-400">{formatDate(n.createdAt)}</p>
              </div>
              <div className="flex gap-2">
                {!n.isRead && <Button variant="outline" disabled={pending}
                  onClick={() => change(() => notificationApi.markRead(n.id))}>Mark read</Button>}
                <Button variant="ghost" disabled={pending} onClick={() => {
                  if (window.confirm('Delete this notification?')) void change(() => notificationApi.remove(n.id))
                }}>Delete</Button>
              </div>
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
