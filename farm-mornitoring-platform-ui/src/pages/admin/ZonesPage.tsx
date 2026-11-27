import { useEffect, useState, type FormEvent } from 'react'
import { farmApi } from '../../api/farm-device/farmApi'
import { zoneApi } from '../../api/farm-device/zoneApi'
import { getErrorMessage } from '../../api/errorHandler'
import type { Farm, Zone } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Select, Table } from '../../components/Ui'
import { MapView } from '../../components/MapView'

export function ZonesPage() {
  const [zones, setZones] = useState<Zone[]>([])
  const [farms, setFarms] = useState<Farm[]>([])
  const [farmId, setFarmId] = useState('')

  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<Partial<Zone>>({ name: '', cropType: '', farmId: '', latitude: 10.76, longitude: 106.66 })

  async function load() {
    try {
      setLoading(true)
      setError('')

      const [zoneData, farmData] = await Promise.all([
        zoneApi.list(farmId || undefined),
        farmApi.list(),
      ])

      setZones(zoneData)
      setFarms(farmData)
    } catch (e) {
      setZones([])
      setError(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [farmId])

  function openCreate() {
    setEditingId(null)
    setFormError('')

    setForm({
      name: '',
      cropType: '',
      farmId: farmId,
      latitude: 10.76,
      longitude: 106.66,
    })

    setOpen(true)
  }

  async function openEdit(id: string) {
    try {
      setError('')
      const zone = await zoneApi.get(id)

      setEditingId(zone.id)
      setForm(zone)
      setOpen(true)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()

    if (saving) return

    setFormError('')

    if (!form.farmId) {
      setFormError('Please select a farm.')
      return
    }

    if (!form.name?.trim()) {
      setFormError('Zone name is required.')
      return
    }

    try {
      setSaving(true)

      if (editingId) {
        await zoneApi.update(editingId, form)
      } else {
        await zoneApi.create(form)
      }

      setOpen(false)
      await load()
    } catch (e) {
      setFormError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  async function changeStatus(zone: Zone) {
    if (saving) return

    try {
      setSaving(true)
      setError('')

      await zoneApi.setActive(
        zone.id,
        zone.isActive === false
      )

      await load()
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setSaving(false)
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
        actions={
          <Button onClick={openCreate}>
            Create zone
          </Button>
        }
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
        {loading ? (
          <p className="py-4 text-center">
            Loading zones...
          </p>
        ) : error && zones.length === 0 ? (
          <div className="py-4 text-center">
            <Button variant="outline" onClick={() => load()}>
              Retry
            </Button>
          </div>
        ) : zones.length === 0 ? (
          <Empty text="No zones found." />
        ) : (
          <Table headers={['Zone', 'Farm', 'Crop', 'Sensors', 'Status', 'Actions']}>
            {zones.map((z) => (
              <tr key={z.id}>
                <td className="px-3 py-2">{z.name}</td>
                <td className="px-3 py-2">{z.farmName || z.farmId}</td>
                <td className="px-3 py-2">{z.cropType || '—'}</td>
                <td className="px-3 py-2">{z.sensorCount ?? '—'}</td>
                <td className="px-3 py-2">{z.isActive === false ? 'Inactive' : 'Active'}</td>
                <td className="px-3 py-2 space-x-2">
                  <button
                    className="text-brand"
                    onClick={() => openEdit(z.id)}
                  >
                    Edit
                  </button>
                  <button
                    disabled={saving}
                    onClick={() => changeStatus(z)}
                  >
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
          <ErrorBox message={formError} />
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
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={saving}
              onClick={() => setOpen(false)}
            >
              Cancel
            </Button>

            <Button type="submit" disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
