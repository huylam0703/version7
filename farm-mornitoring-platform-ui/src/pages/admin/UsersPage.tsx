import { useEffect, useState, type FormEvent } from 'react'
import { getErrorMessage, userApi } from '../../api/'
import type { User, UserRole } from '../../types'
import {
  Button,
  Card,
  Empty,
  ErrorBox,
  Input,
  Modal,
  PageHeader,
  Select,
  Table
} from '../../components/Ui'

const initialForm = {
  fullName: '',
  email: '',
  password: '',
  role: 'OPERATOR' as UserRole,
  phone: ''
}

export function UsersPage() {
  const [users, setUsers] = useState<User[]>([])
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [form, setForm] = useState(initialForm)

  async function load() {
    try {
      setError('')
      const response = await userApi.list()
      setUsers(response.items)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => {
    load()
  }, [])

  function updateForm<K extends keyof typeof initialForm>(
    field: K,
    value: (typeof initialForm)[K]
  ) {
    setForm(prev => ({
      ...prev,
      [field]: value
    }))
  }

  function closeModal() {
    if (loading) return

    setOpen(false)
    setForm(initialForm)
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError('')

    if (!form.fullName.trim() || !form.email.trim() || !form.password) {
      setError('Vui lòng nhập đầy đủ họ tên, email và mật khẩu.')
      return
    }

    try {
      setLoading(true)

      await userApi.create({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        phone: form.phone.trim(),
        roleName: form.role
      })

      setOpen(false)
      setForm(initialForm)

      await load()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <PageHeader
        title="User management"
        description="Farm administrators can create operator and admin accounts."
        actions={
          <Button onClick={() => {
            setError('')
            setOpen(true)
          }}>
            Create user
          </Button>
        }
      />

      <ErrorBox message={error} />

      <Card>
        {users.length === 0 ? (
          <Empty text="No users. Check API /api/users" />
        ) : (
          <Table headers={['Name', 'Email', 'Role', 'Phone']}>
            {users.map((u) => (
              <tr key={u.userId}>
                <td className="px-3 py-2">{u.fullName}</td>
                <td className="px-3 py-2">{u.email}</td>
                <td className="px-3 py-2">{u.role}</td>
                <td className="px-3 py-2">{u.phone || '—'}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <Modal
        open={open}
        title="Create user"
        onClose={closeModal}
      >
        <form className="space-y-3" onSubmit={onSubmit}>
          <Input
            label="Full name"
            value={form.fullName}
            onChange={(e) => updateForm('fullName', e.target.value)}
            required
          />

          <Input
            label="Email"
            type="email"
            value={form.email}
            onChange={(e) => updateForm('email', e.target.value)}
            required
          />

          <Input
            label="Password"
            type="password"
            value={form.password}
            onChange={(e) => updateForm('password', e.target.value)}
            required
          />

          <Input
            label="Phone"
            value={form.phone}
            onChange={(e) => updateForm('phone', e.target.value)}
            placeholder="Optional"
          />

          <Select
            label="Role"
            value={form.role}
            onChange={(e) => updateForm('role', e.target.value as UserRole)}
          >
            <option value="OPERATOR">UAV and Device Operator</option>
            <option value="ADMIN">Farm Administrator</option>
          </Select>

          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              onClick={closeModal}
              disabled={loading}
            >
              Cancel
            </Button>

            <Button type="submit" disabled={loading}>
              {loading ? 'Creating...' : 'Save'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}