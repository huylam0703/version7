import { useEffect, useState, type FormEvent } from 'react'
import { getErrorMessage, uavApi } from '../../api'
import type { Uav } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Table } from '../../components/Ui'

export function UavsPage() {
  const [items, setItems] = useState<Uav[]>([])
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<Partial<Uav>>({ code: '', name: '', model: '', status: 'ready' })

  async function load() {
    try {
      setError('')
      setItems(await uavApi.list())
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      if (editingId) await uavApi.update(editingId, form)
      else await uavApi.create(form)
      setOpen(false)
      await load()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div>
      <PageHeader title="UAV management" description="Register UAVs used as collection platforms. This app does not fly the UAV." actions={<Button onClick={() => { setEditingId(null); setOpen(true) }}>Add UAV</Button>} />
      <ErrorBox message={error} />
      <Card>
        {items.length === 0 ? <Empty text="No UAVs. Check API /api/uavs" /> : (
          <Table headers={['Code', 'Name', 'Model', 'Status', 'Battery', 'Actions']}>
            {items.map((u) => (
              <tr key={u.id}>
                <td className="px-3 py-2">{u.code}</td>
                <td className="px-3 py-2">{u.name}</td>
                <td className="px-3 py-2">{u.model || '—'}</td>
                <td className="px-3 py-2">{u.status || '—'}</td>
                <td className="px-3 py-2">{u.batteryPercent ?? '—'}%</td>
                <td className="px-3 py-2"><button className="text-brand" onClick={() => { setEditingId(u.id); setForm(u); setOpen(true) }}>Edit</button></td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <Modal open={open} title={editingId ? 'Edit UAV' : 'Add UAV'} onClose={() => setOpen(false)}>
        <form className="space-y-3" onSubmit={onSubmit}>
          <Input label="Code" value={form.code || ''} onChange={(e) => setForm({ ...form, code: e.target.value })} required />
          <Input label="Name" value={form.name || ''} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Input label="Model" value={form.model || ''} onChange={(e) => setForm({ ...form, model: e.target.value })} />
          <Input label="Status" value={form.status || ''} onChange={(e) => setForm({ ...form, status: e.target.value })} />
          <Button type="submit">Save</Button>
        </form>
      </Modal>
    </div>
  )
}
