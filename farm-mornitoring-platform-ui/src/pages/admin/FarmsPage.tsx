import { useEffect, useState, type FormEvent } from 'react'
import { farmApi, getErrorMessage, zoneApi } from '../../api'
import type { Farm, Zone } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Table, Textarea } from '../../components/Ui'
import { MapView } from '../../components/MapView'

const emptyForm: Partial<Farm> = {
  name: '',
  address: '',
  areaHa: 0,
  latitude: 10.76,
  longitude: 106.66,
  description: '',
  isActive: true,
}

export function FarmsPage() {
  const [farms, setFarms] = useState<Farm[]>([])
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<Partial<Farm>>(emptyForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [selected, setSelected] = useState<Farm | null>(null)
  const [zones, setZones] = useState<Zone[]>([])

  async function load() {
    try {
      setError('')
      setFarms(await farmApi.list())
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => {
    load()
  }, [])

  const filtered = farms.filter((f) => f.name.toLowerCase().includes(search.toLowerCase()))
  const pageSize = 8
  const paged = filtered.slice((page - 1) * pageSize, page * pageSize)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      if (editingId) await farmApi.update(editingId, form)
      else await farmApi.create(form)
      setOpen(false)
      await load()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  async function openDetail(farm: Farm) {
    setSelected(farm)
    try {
      setZones(await zoneApi.list(farm.id))
    } catch {
      setZones([])
    }
  }

  return (
    <div>
      <PageHeader
        title="Farm management"
        description="Create and maintain farms, coordinates and zone lists."
        actions={<Button onClick={() => { setEditingId(null); setForm(emptyForm); setOpen(true) }}>Create farm</Button>}
      />
      <ErrorBox message={error} />
      <div className="mb-4 flex gap-2">
        <input
          className="w-full max-w-sm rounded-lg border px-3 py-2 text-sm"
          placeholder="Search farm..."
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(1) }}
        />
      </div>
      <Card>
        {paged.length === 0 ? <Empty text="No farms yet. Check API /api/farms" /> : (
          <Table headers={['Name', 'Address', 'Area (ha)', 'Coordinates', 'Status', 'Actions']}>
            {paged.map((farm) => (
              <tr key={farm.id}>
                <td className="px-3 py-2 font-medium">{farm.name}</td>
                <td className="px-3 py-2">{farm.address || '—'}</td>
                <td className="px-3 py-2">{farm.areaHa ?? '—'}</td>
                <td className="px-3 py-2">{farm.latitude}, {farm.longitude}</td>
                <td className="px-3 py-2">{farm.isActive === false ? 'Inactive' : 'Active'}</td>
                <td className="px-3 py-2 space-x-2">
                  <button className="text-brand" onClick={() => openDetail(farm)}>View</button>
                  <button className="text-brand" onClick={() => { setEditingId(farm.id); setForm(farm); setOpen(true) }}>Edit</button>
                  <button className="text-gray-600" onClick={() => farmApi.setActive(farm.id, farm.isActive === false).then(load)}>
                    {farm.isActive === false ? 'Activate' : 'Deactivate'}
                  </button>
                </td>
              </tr>
            ))}
          </Table>
        )}
        <div className="mt-3 flex justify-end gap-2 text-sm">
          <Button variant="ghost" onClick={() => setPage((p) => Math.max(1, p - 1))}>Prev</Button>
          <span className="py-2">Page {page}</span>
          <Button variant="ghost" onClick={() => setPage((p) => p + 1)}>Next</Button>
        </div>
      </Card>

      {selected ? (
        <Card className="mt-4">
          <h3 className="mb-2 font-medium">{selected.name}</h3>
          <p className="mb-3 text-sm text-gray-500">{selected.address} · {selected.areaHa} ha</p>
          <MapView
            center={[selected.latitude || 10.76, selected.longitude || 106.66]}
            points={[{ id: selected.id, lat: selected.latitude || 10.76, lng: selected.longitude || 106.66, popup: selected.name }]}
            polygons={selected.boundary ? [selected.boundary] : []}
          />
          <h4 className="mt-4 mb-2 text-sm font-medium">Zones in this farm</h4>
          {zones.length === 0 ? <Empty text="No zones" /> : zones.map((z) => <p key={z.id} className="text-sm">{z.name} — {z.cropType}</p>)}
        </Card>
      ) : null}

      <Modal open={open} title={editingId ? 'Edit farm' : 'Create farm'} onClose={() => setOpen(false)}>
        <form className="space-y-3" onSubmit={onSubmit}>
          <Input label="Name" value={form.name || ''} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Input label="Address" value={form.address || ''} onChange={(e) => setForm({ ...form, address: e.target.value })} />
          <Input label="Area (ha)" type="number" value={form.areaHa ?? 0} onChange={(e) => setForm({ ...form, areaHa: Number(e.target.value) })} />
          <div className="grid grid-cols-2 gap-2">
            <Input label="Latitude" type="number" step="any" value={form.latitude ?? 0} onChange={(e) => setForm({ ...form, latitude: Number(e.target.value) })} />
            <Input label="Longitude" type="number" step="any" value={form.longitude ?? 0} onChange={(e) => setForm({ ...form, longitude: Number(e.target.value) })} />
          </div>
          <Textarea label="Description" value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          <Button type="submit">Save</Button>
        </form>
      </Modal>
    </div>
  )
}
