import { useEffect, useState, type FormEvent } from 'react'
import { farmApi } from '../../api/farm-device/farmApi'
import { zoneApi } from '../../api/farm-device/zoneApi'
import { sensorNodeApi } from '../../api/farm-device/sensorNodeApi'
import type { SensorNodeDto } from '../../types/sensor'
import { getErrorMessage } from '../../api/errorHandler'
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
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [formError, setFormError] = useState('')

  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<Partial<Farm>>(emptyForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [selected, setSelected] = useState<Farm | null>(null)
  const [zones, setZones] = useState<Zone[]>([])
  const [nodes, setNodes] = useState<SensorNodeDto[]>([])

  async function load() {
    try {
      setLoading(true)
      setError('')

      const data = await farmApi.list()
      setFarms(data)
    } catch (e) {
      setFarms([])
      setError(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const filtered = farms.filter((f) => f.name.toLowerCase().includes(search.toLowerCase()))
  const pageSize = 8

  const totalPages = Math.max(
    1,
    Math.ceil(filtered.length / pageSize)
  )

  const currentPage = Math.min(page, totalPages)

  const paged = filtered.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize
  )

  async function onSubmit(e: FormEvent) {
    e.preventDefault()

    if (saving) return

    try {
      setSaving(true)
      setFormError('')

      if (editingId) {
        await farmApi.update(editingId, form)
      } else {
        await farmApi.create(form)
      }

      setOpen(false)
      await load()

      if (selected && selected.id === editingId) {
        await openDetail(selected)
      }
    } catch (e) {
      setFormError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  async function openDetail(farm: Farm) {
    try {
      setDetailLoading(true)
      setError('')

      const [detail, farmZones, nodePage] =
        await Promise.all([
          farmApi.get(farm.id),
          zoneApi.list(farm.id),
          sensorNodeApi.list({
            farmId: farm.id,
            pageSize: 100,
          }),
        ])

      setSelected(detail)
      setZones(farmZones)
      setNodes(nodePage.items)
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setDetailLoading(false)
    }
  }

  async function changeStatus(farm: Farm) {
    try {
      setError('')

      await farmApi.setActive(
        farm.id,
        farm.isActive === false
      )

      await load()

      if (selected?.id === farm.id) {
        await openDetail(farm)
      }
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  const nodePoints = nodes.flatMap(node => {
    if (
      node.lat == null ||
      node.lng == null ||
      !Number.isFinite(node.lat) ||
      !Number.isFinite(node.lng) ||
      Math.abs(node.lat) > 90 ||
      Math.abs(node.lng) > 180
    ) {
      return []
    }

    return [{
      id: node.nodeId,
      lat: node.lat,
      lng: node.lng,
      popup: `${node.name} - ${node.status}`,
    }]
  })

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
      {loading ? (
        <p className="py-4 text-center">
          Loading farms...
        </p>
      ) : error && farms.length === 0 ? (
        <div className="py-4 text-center">
          <Button variant="outline" onClick={() => load()}>
            Retry
          </Button>
        </div>
      ) : paged.length === 0 ? (
        <Empty text="No farms found." />
      ) : (
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
                  <button
                    className="text-gray-600"
                    onClick={() => changeStatus(farm)}
                  >
                    {farm.isActive === false ? 'Activate' : 'Deactivate'}
                  </button>
                </td>
              </tr>
            ))}
          </Table>
        )}
        <div className="mt-3 flex justify-end gap-2 text-sm">
          <Button
            variant="ghost"
            disabled={loading || currentPage <= 1}
            onClick={() => setPage(currentPage - 1)}
          >
            Prev
          </Button>

          <span className="py-2">
            Page {currentPage} / {totalPages}
          </span>

          <Button
            variant="ghost"
            disabled={loading || currentPage >= totalPages}
            onClick={() => setPage(currentPage + 1)}
          >
            Next
          </Button>
        </div>
      </Card>

      {detailLoading ? (
        <Card className="mt-4">
          <p className="py-4 text-center">
            Loading farm details...
          </p>
        </Card>
      ) : selected ? (
        <Card className="mt-4">
          <h3 className="mb-2 font-medium">{selected.name}</h3>
          <p className="mb-3 text-sm text-gray-500">{selected.address} · {selected.areaHa} ha</p>
          <MapView
            key={selected.id}
            center={[
              selected.latitude ?? 10.76,
              selected.longitude ?? 106.66
            ]}
            points={[
              ...(selected.latitude != null &&
                  selected.longitude != null
                ? [{
                    id: selected.id,
                    lat: selected.latitude,
                    lng: selected.longitude,
                    popup: selected.name,
                  }]
                : []),
              ...nodePoints,
            ]}
            polygons={selected.boundary ? [selected.boundary] : []}
          />
          <h4 className="mt-4 mb-2 text-sm font-medium">Zones in this farm</h4>
          {zones.length === 0 ? <Empty text="No zones" /> : zones.map((z) => <p key={z.id} className="text-sm">{z.name} — {z.cropType}</p>)}
        </Card>
      ) : null}

      <Modal open={open} title={editingId ? 'Edit farm' : 'Create farm'} onClose={() => setOpen(false)}>
        <form className="space-y-3" onSubmit={onSubmit}>
          <ErrorBox message={formError} />
          <Input label="Name" value={form.name || ''} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Input label="Address" value={form.address || ''} onChange={(e) => setForm({ ...form, address: e.target.value })} />
          <Input label="Area (ha)" type="number" value={form.areaHa ?? 0} onChange={(e) => setForm({ ...form, areaHa: Number(e.target.value) })} />
          <div className="grid grid-cols-2 gap-2">
            <Input label="Latitude" type="number" step="any" value={form.latitude ?? 0} onChange={(e) => setForm({ ...form, latitude: Number(e.target.value) })} />
            <Input label="Longitude" type="number" step="any" value={form.longitude ?? 0} onChange={(e) => setForm({ ...form, longitude: Number(e.target.value) })} />
          </div>
          <Textarea label="Description" value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
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
