import { useState, type FormEvent } from 'react'
import { accountApi, getErrorMessage } from '../../api/'
import { useAuth } from '../../AuthContext'
import { Button, Card, ErrorBox, Input, PageHeader } from '../../components/Ui'

export function SettingsPage() {
  const { user, refreshUser } = useAuth()
  const isAdmin = user?.role === 'ADMIN'

  const [fullName, setFullName] = useState(user?.fullName || '')
  const [phone, setPhone] = useState(user?.phone || '')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setMessage('')

    if (!user?.userId) {
      setError('Không tìm thấy thông tin tài khoản.')
      return
    }
    if (!fullName.trim()) {
      setError('Họ tên không được để trống.')
      return
    }

    try {
      setSaving(true)
      await accountApi.updateMe({ fullName: fullName.trim(), phone: phone.trim() || null })
      await refreshUser()
      setMessage('Cập nhật thông tin thành công.')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <PageHeader title="Account settings" description="Update your profile. Role cannot be changed here." />
      <Card className="max-w-lg">
        <form className="space-y-3" onSubmit={onSubmit}>
          <ErrorBox message={error} />
          {message ? <p className="text-sm text-green-700">{message}</p> : null}
          <Input label="Email" value={user?.email || ''} disabled />
          <Input label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} disabled={!isAdmin} />
          <Input label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} disabled={!isAdmin} />
          
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving...' : 'Save'}
          </Button>
          
        </form>
      </Card>
    </div>
  )
}