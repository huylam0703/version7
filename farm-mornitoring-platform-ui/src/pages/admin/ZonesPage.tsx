import { useEffect, useState, type FormEvent } from 'react'
import { farmApi, getErrorMessage, zoneApi } from '../../api'
import type { Farm, Zone } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Select, Table } from '../../components/Ui'
import { MapView } from '../../components/MapView'

export function ZonesPage() {
  const [zones, setZones] = useState<Zone[]>([])
  const [farms, setFarms] = useState<Farm[]>([])
  const [farmId, setFarmId] = useState('')
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<Partial<Zone>>({ name: '', cropType: '', farmId: '', latitude: 10.76, longitude: 106.66 })

  async function load() {
    try {
      setError('')
      const [z, f] = await Promise.all([zoneApi.list(farmId || undefined), farmApi.list()])
      setZones(z)
      setFarms(f)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [farmId])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      if (editingId) await zoneApi.update(editingId, form)
      else await zoneApi.create(form)
      setOpen(false)
      await load()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  const points = zones.filter((z) => z.latitude && z.longitude).map((z) => ({
    id: z.id,
    lat: z.latitude as number,
    lng: z.longitude as number,
    popup: `${z.name} (${z.cropType || 'crop'})`,
  }))

  return (
    <div>
      <PageHeader
        title="Zone management"
        description="Assign crop zones to farms and show them on the map."
        actions={<Button onClick={() => { setEditingId(null); setOpen(true) }}>Create zone</Button>}
      />
      <ErrorBox message={error} />
      <div className="mb-4 max-w-xs">
        <Select value={farmId} onChange={(e) => setFarmId(e.target.value)}>
          <option value="">All farms</option>
          {farms.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
        </Select>
      </div>
      <MapView points={points} polygons={zones.filter((z) => z.boundary).map((z) => z.boundary!)} />
      <Card className="mt-4">
        {zones.length === 0 ? <Empty text="No zones. Check API /api/zones" /> : (
          <Table headers={['Zone', 'Farm', 'Crop', 'Sensors', 'Status', 'Actions']}>
            {zones.map((z) => (
              <tr key={z.id}>
                <td className="px-3 py-2">{z.name}</td>
                <td className="px-3 py-2">{z.farmName || z.farmId}</td>
                <td className="px-3 py-2">{z.cropType || '—'}</td>
                <td className="px-3 py-2">{z.sensorCount ?? '—'}</td>
                <td className="px-3 py-2">{z.isActive === false ? 'Inactive' : 'Active'}</td>
                <td className="px-3 py-2 space-x-2">
                  <button className="text-brand" onClick={() => { setEditingId(z.id); setForm(z); setOpen(true) }}>Edit</button>
                  <button onClick={() => zoneApi.setActive(z.id, z.isActive === false).then(load)}>
                    {z.isActive === false ? 'Activate' : 'Deactivate'}
                  </button>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <Modal open={open} title={editingId ? 'Edit zone' : 'Create zone'} onClose={() => setOpen(false)}>
        <form className="space-y-3" onSubmit={onSubmit}>
          <Select label="Farm" value={form.farmId || ''} onChange={(e) => setForm({ ...form, farmId: e.target.value })}>
            <option value="">Select farm</option>
            {farms.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </Select>
          <Input label="Name" value={form.name || ''} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Input label="Crop type" value={form.cropType || ''} onChange={(e) => setForm({ ...form, cropType: e.target.value })} />
          <div className="grid grid-cols-2 gap-2">
            <Input label="Latitude" type="number" step="any" value={form.latitude ?? 0} onChange={(e) => setForm({ ...form, latitude: Number(e.target.value) })} />
            <Input label="Longitude" type="number" step="any" value={form.longitude ?? 0} onChange={(e) => setForm({ ...form, longitude: Number(e.target.value) })} />
          </div>
          <Button type="submit">Save</Button>
        </form>
      </Modal>
    </div>
  )
}
