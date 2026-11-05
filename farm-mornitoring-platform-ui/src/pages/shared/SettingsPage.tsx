import { useState, type FormEvent } from 'react'
import { accountApi, getErrorMessage } from '../../api/'
import { useAuth } from '../../AuthContext'
import { Button, Card, ErrorBox, Input, PageHeader } from '../../components/Ui'

export function SettingsPage() {
  const { user } = useAuth()
  const [fullName, setFullName] = useState(user?.fullName || '')
  const [phone, setPhone] = useState(user?.phone || '')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setMessage('')

    if (!user?.userId) {
      setError('Không tìm thấy thông tin tài khoản.')
      return
    }

    try {
      await accountApi.update(user.userId, {
        fullName,
        phone,
        ...(password.trim() ? { password } : {})
      })

      setMessage('Cập nhật thông tin thành công.')
      setPassword('')
    } catch (err) {
      setError(getErrorMessage(err))
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
          <Input label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          <Input label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <Input label="New password (optional)" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          <Button type="submit">Save</Button>
        </form>
      </Card>
    </div>
  )
}
