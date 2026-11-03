import { useEffect, useState, type FormEvent } from 'react'
import { getErrorMessage, sensorApi, zoneApi } from '../../api'
import type { SensorNode, SensorType, Zone } from '../../types'
import { SENSOR_TYPE_LABEL } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Select, Table, sensorStatusBadge } from '../../components/Ui'

export function NodesPage() {
  const [nodes, setNodes] = useState<SensorNode[]>([])
  const [zones, setZones] = useState<Zone[]>([])
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<Partial<SensorNode>>({
    code: '',
    name: '',
    sensorType: 'soilMoisture',
    status: 'offline',
    zoneId: '',
  })

  async function load() {
    try {
      setError('')
      const [n, z] = await Promise.all([sensorApi.list(), zoneApi.list()])
      setNodes(n)
      setZones(z)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      if (editingId) await sensorApi.update(editingId, form)
      else await sensorApi.create(form)
      setOpen(false)
      await load()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div>
      <PageHeader
        title="Sensor node management"
        description="Register nodes and assign them to farm zones."
        actions={<Button onClick={() => { setEditingId(null); setOpen(true) }}>Register node</Button>}
      />
      <ErrorBox message={error} />
      <Card>
        {nodes.length === 0 ? <Empty text="No nodes. Check API /api/sensor-nodes" /> : (
          <Table headers={['Code', 'Name', 'Type', 'Zone', 'Status', 'Actions']}>
            {nodes.map((n) => (
              <tr key={n.id}>
                <td className="px-3 py-2">{n.code}</td>
                <td className="px-3 py-2">{n.name}</td>
                <td className="px-3 py-2">{SENSOR_TYPE_LABEL[n.sensorType]}</td>
                <td className="px-3 py-2">{n.zoneName || n.zoneId || '—'}</td>
                <td className="px-3 py-2">{sensorStatusBadge(n.status)}</td>
                <td className="px-3 py-2">
                  <button className="text-brand" onClick={() => { setEditingId(n.id); setForm(n); setOpen(true) }}>Edit</button>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <Modal open={open} title={editingId ? 'Edit node' : 'Register node'} onClose={() => setOpen(false)}>
        <form className="space-y-3" onSubmit={onSubmit}>
          <Input label="Code" value={form.code || ''} onChange={(e) => setForm({ ...form, code: e.target.value })} required />
          <Input label="Name" value={form.name || ''} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Select label="Sensor type" value={form.sensorType} onChange={(e) => setForm({ ...form, sensorType: e.target.value as SensorType })}>
            {Object.entries(SENSOR_TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </Select>
          <Select label="Zone" value={form.zoneId || ''} onChange={(e) => setForm({ ...form, zoneId: e.target.value })}>
            <option value="">Unassigned</option>
            {zones.map((z) => <option key={z.id} value={z.id}>{z.name}</option>)}
          </Select>
          <div className="grid grid-cols-2 gap-2">
            <Input label="Latitude" type="number" step="any" value={form.latitude ?? ''} onChange={(e) => setForm({ ...form, latitude: Number(e.target.value) })} />
            <Input label="Longitude" type="number" step="any" value={form.longitude ?? ''} onChange={(e) => setForm({ ...form, longitude: Number(e.target.value) })} />
          </div>
          <Button type="submit">Save</Button>
        </form>
      </Modal>
    </div>
  )
}
