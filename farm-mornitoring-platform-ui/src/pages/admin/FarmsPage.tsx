import { useEffect, useState, type FormEvent } from 'react'
import { farmApi } from '../../api/farm-device/farmApi'
import { zoneApi } from '../../api/farm-device/zoneApi'
import { sensorNodeApi } from '../../api/farm-device/sensorNodeApi'
import type { SensorNodeDto } from '../../types/sensor'
import { getErrorMessage } from '../../api/errorHandler'
import type { Farm, Zone } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Table, Textarea } from '../../components/Ui'
import { FarmZoneMap } from '../../components/FarmZoneMap'
import { areaInHectares, boundaryCenter, containsCenter, defaultFarmMapCenter, isCoordinate, isValidBoundary, namedBoundary, readBoundary, type Coordinates } from '../../utils/farmZoneGeometry'
import type { Polygon } from 'geojson'

const emptyForm: Partial<Farm> = {
  name: '',
  address: '',
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
  const [editLoading, setEditLoading] = useState(false)
  const [boundary, setBoundary] = useState<string | null>(null)
  const [initialBoundary, setInitialBoundary] = useState<Polygon>()
  const [initialCenter, setInitialCenter] = useState<Coordinates>(defaultFarmMapCenter)
  const [destination, setDestination] = useState<{ point: Coordinates }>()
  const [drawing, setDrawing] = useState(false)
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

  const draft = readBoundary({ boundaryGeojson: boundary })
  const validDraft = isValidBoundary(draft)
  const location: Coordinates | undefined = form.latitude != null && form.longitude != null &&
    isCoordinate(form.latitude, form.longitude) ? [form.latitude, form.longitude] : undefined
  const center = validDraft ? (location && containsCenter(draft, location) ? location : boundaryCenter(draft)) : undefined
  const marker = center ?? location
  const hectares = validDraft ? Number(areaInHectares(draft).toFixed(2)) : undefined

  function openForm(farm?: Farm) {
    const polygon = readBoundary(farm)
    setEditingId(farm?.id ?? null)
    setForm(farm ?? emptyForm)
    setFormError('')
    setBoundary(polygon ? JSON.stringify(polygon) : null)
    setInitialBoundary(polygon)
    setInitialCenter(farm?.latitude != null && farm.longitude != null && isCoordinate(farm.latitude, farm.longitude)
      ? [farm.latitude, farm.longitude]
      : defaultFarmMapCenter)
    setDestination(undefined)
    setDrawing(false)
    setOpen(true)
  }

  async function openEdit(id: string) {
    setError('')
    setEditLoading(true)
    try {
      openForm(await farmApi.get(id))
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setEditLoading(false)
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()

    if (saving || drawing || !validDraft || !center || !hectares) return
    if (!form.name?.trim()) {
      setFormError('Farm name is required.')
      return
    }

    try {
      setSaving(true)
      setFormError('')

      const payload = { ...form, name: form.name.trim(), boundaryGeojson: boundary, latitude: center[0], longitude: center[1], areaHa: hectares }
      const saved = editingId ? await farmApi.update(editingId, payload) : await farmApi.create(payload)

      setOpen(false)
      await load()
      await openDetail(saved)
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

  const farmOutline = selected ? namedBoundary(selected) : undefined
  const zoneOutlines = zones.flatMap(zone => {
    const item = namedBoundary(zone)
    return item ? [item] : []
  })
  const detailCenter: Coordinates | undefined = selected?.latitude != null && selected.longitude != null &&
    isCoordinate(selected.latitude, selected.longitude) ? [selected.latitude, selected.longitude] : undefined

  return (
    <div>
      <PageHeader
        title="Farm management"
        description="Draw farm boundaries and view crop zones on the map."
        actions={<Button onClick={() => openForm()} disabled={editLoading || saving}>Create farm</Button>}
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
                  <button className="text-brand disabled:opacity-50" disabled={editLoading || saving} onClick={() => openEdit(farm.id)}>Edit</button>
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
          <p className="mb-3 text-sm text-muted">{[selected.address, selected.areaHa != null ? `${selected.areaHa} ha` : null].filter(Boolean).join(' · ')}</p>
          {!isValidBoundary(farmOutline?.polygon) && (
            <div className="mb-3 flex flex-wrap items-center gap-3 text-sm text-amber-800">
              <span>This farm needs a boundary before you can draw zones.</span>
              <Button variant="outline" disabled={editLoading || saving} onClick={() => openEdit(selected.id)}>Add boundary</Button>
            </div>
          )}
          <FarmZoneMap
            key={selected.id}
            center={detailCenter ?? defaultFarmMapCenter}
            fitBoundary={farmOutline?.polygon}
            fitPoints
            farm={farmOutline}
            zones={zoneOutlines}
            points={[
              ...(detailCenter ? [{ id: selected.id, lat: detailCenter[0], lng: detailCenter[1], popup: selected.name }] : []),
              ...nodePoints,
            ]}
          />
          <h4 className="mt-4 mb-2 text-sm font-medium">Zones in this farm</h4>
          {zones.length === 0 ? <Empty text="No zones" /> : zones.map((z) => <p key={z.id} className="text-sm">{z.name} — {z.cropType}</p>)}
        </Card>
      ) : null}

      <Modal open={open} title={editingId ? 'Edit farm' : 'Create farm'} wide onClose={() => { if (!saving) setOpen(false) }}>
        <form className="space-y-4" onSubmit={onSubmit}>
          <ErrorBox message={formError || (boundary && !validDraft ? 'Draw a valid polygon without crossing edges.' : '')} />
          <div className="grid gap-3 sm:grid-cols-2">
            <Input label="Name" value={form.name || ''} onChange={e => setForm({ ...form, name: e.target.value })} maxLength={150} required />
            <Input label="Address" value={form.address || ''} onChange={e => setForm({ ...form, address: e.target.value })} maxLength={255} />
          </div>
          <Textarea label="Description" value={form.description || ''} onChange={e => setForm({ ...form, description: e.target.value })} />
          <div className="grid grid-cols-2 items-end gap-3 sm:grid-cols-[1fr_1fr_auto]">
            <Input label="Latitude" type="number" step="any" min={-90} max={90} value={form.latitude ?? ''} onChange={e => setForm({ ...form, latitude: e.target.value === '' ? undefined : e.target.valueAsNumber })} />
            <Input label="Longitude" type="number" step="any" min={-180} max={180} value={form.longitude ?? ''} onChange={e => setForm({ ...form, longitude: e.target.value === '' ? undefined : e.target.valueAsNumber })} />
            <div className="col-span-2 sm:col-span-1">
              <Button variant="outline" disabled={!location || drawing} onClick={() => { if (location) setDestination({ point: location }) }}>Go to location</Button>
            </div>
          </div>
          <FarmZoneMap
            center={initialCenter}
            fitBoundary={initialBoundary}
            destination={destination}
            points={marker ? [{ id: 'center', lat: marker[0], lng: marker[1] }] : []}
            editor={{ value: boundary, onChange: setBoundary, onBusyChange: setDrawing }}
            onPickCenter={([latitude, longitude]) => setForm(current => ({ ...current, latitude, longitude }))}
            height={340}
          />
          <dl className="flex flex-wrap gap-x-8 gap-y-2 rounded-xl bg-stone-50 px-4 py-3 text-sm">
            <div><dt className="text-muted">Area (ha)</dt><dd>{hectares?.toFixed(2) ?? '—'}</dd></div>
            <div><dt className="text-muted">Center to save</dt><dd>{center ? center.map(value => value.toFixed(6)).join(', ') : '—'}</dd></div>
          </dl>
          {hectares === 0 && <p className="text-sm text-amber-800">This boundary rounds to 0.00 ha. Draw a farm boundary of at least 0.005 ha to save it.</p>}
          {center && location && (center[0] !== location[0] || center[1] !== location[1]) && (
            <p className="text-sm text-muted">The selected location is outside the interior. The center to save is calculated from the boundary.</p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="outline" disabled={saving} onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving || drawing || !validDraft || !hectares}>{saving ? 'Saving...' : 'Save'}</Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
