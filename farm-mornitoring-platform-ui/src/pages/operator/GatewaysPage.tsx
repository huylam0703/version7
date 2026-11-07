import { useEffect, useState, type FormEvent } from 'react'
import { gatewayApi, getErrorMessage, uavApi } from '../../api'
import type { Gateway, Uav } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Select, Table } from '../../components/Ui'

export function GatewaysPage() {
  const [items, setItems] = useState<Gateway[]>([])
  const [uavs, setUavs] = useState<Uav[]>([])
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<Partial<Gateway>>({ code: '', name: '', uavId: '', status: 'ready' })

  async function load() {
    try {
      setError('')
      const [g, u] = await Promise.all([gatewayApi.list(), uavApi.list()])
      setItems(g)
      setUavs(u)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      if (editingId) await gatewayApi.update(editingId, form)
      else await gatewayApi.create(form)
      setOpen(false)
      await load()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div>
      <PageHeader title="Mobile gateway management" description="Associate a collection gateway with a UAV." actions={<Button onClick={() => { setEditingId(null); setOpen(true) }}>Add gateway</Button>} />
      <ErrorBox message={error} />
      <Card>
        {items.length === 0 ? <Empty text="No gateways. Check API /api/gateways" /> : (
          <Table headers={['Code', 'Name', 'UAV', 'Status', 'Actions']}>
            {items.map((g) => (
              <tr key={g.id}>
                <td className="px-3 py-2">{g.code}</td>
                <td className="px-3 py-2">{g.name}</td>
                <td className="px-3 py-2">{g.uavName || g.uavId || '—'}</td>
                <td className="px-3 py-2">{g.status || '—'}</td>
                <td className="px-3 py-2"><button className="text-brand" onClick={() => { setEditingId(g.id); setForm(g); setOpen(true) }}>Edit</button></td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <Modal open={open} title={editingId ? 'Edit gateway' : 'Add gateway'} onClose={() => setOpen(false)}>
        <form className="space-y-3" onSubmit={onSubmit}>
          <Input label="Code" value={form.code || ''} onChange={(e) => setForm({ ...form, code: e.target.value })} required />
          <Input label="Name" value={form.name || ''} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Select label="UAV" value={form.uavId || ''} onChange={(e) => setForm({ ...form, uavId: e.target.value })}>
            <option value="">Not linked</option>
            {uavs.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
          </Select>
          <Button type="submit">Save</Button>
        </form>
      </Modal>
    </div>
  )
}
