import { useEffect, useState, type FormEvent } from 'react'
import { getErrorMessage, userApi } from '../../api'
import type { User, UserRole } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Select, Table } from '../../components/Ui'

export function UsersPage() {
  const [users, setUsers] = useState<User[]>([])
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'UavOperator' as UserRole, phone: '' })

  async function load() {
    try {
      setError('')
      setUsers(await userApi.list())
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      await userApi.create(form)
      setOpen(false)
      await load()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div>
      <PageHeader
        title="User management"
        description="Farm administrators can create operator and admin accounts."
        actions={<Button onClick={() => setOpen(true)}>Create user</Button>}
      />
      <ErrorBox message={error} />
      <Card>
        {users.length === 0 ? <Empty text="No users. Check API /api/users" /> : (
          <Table headers={['Name', 'Email', 'Role', 'Phone']}>
            {users.map((u) => (
              <tr key={u.id}>
                <td className="px-3 py-2">{u.fullName}</td>
                <td className="px-3 py-2">{u.email}</td>
                <td className="px-3 py-2">{u.role}</td>
                <td className="px-3 py-2">{u.phone || '—'}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <Modal open={open} title="Create user" onClose={() => setOpen(false)}>
        <form className="space-y-3" onSubmit={onSubmit}>
          <Input label="Full name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
          <Input label="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          <Input label="Password" type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
          <Input label="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          <Select label="Role" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as UserRole })}>
            <option value="FarmAdministrator">Farm Administrator</option>
            <option value="UavOperator">UAV and Device Operator</option>
          </Select>
          <Button type="submit">Save</Button>
        </form>
      </Modal>
    </div>
  )
}
