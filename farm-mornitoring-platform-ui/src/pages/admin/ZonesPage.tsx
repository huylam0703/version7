import { useEffect, useState, type FormEvent } from 'react'
import { farmApi } from '../../api/farm-device/farmApi'
import { zoneApi } from '../../api/farm-device/zoneApi'
import { getErrorMessage } from '../../api/errorHandler'
import type { Farm, Zone } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Select, Table } from '../../components/Ui'
import { FarmZoneMap } from '../../components/FarmZoneMap'
import { areaInHectares, boundaryCenter, defaultFarmMapCenter, isCoordinate, isValidBoundary, namedBoundary, readBoundary, zoneBoundaryError, type Coordinates } from '../../utils/farmZoneGeometry'

type FarmZones = { id: string; farm: Farm | null; zones: Zone[] }

function zoneArea(zone: Zone) {
  const polygon = readBoundary(zone)
  return isValidBoundary(polygon) ? areaInHectares(polygon).toFixed(2) : '—'
}

export function ZonesPage() {
  const [farms, setFarms] = useState<Farm[]>([])
  const [farmId, setFarmId] = useState('')
  const [results, setResults] = useState<FarmZones | null>(null)
  const [revision, setRevision] = useState(0)
  const [error, setError] = useState('')
  const [completedQuery, setCompletedQuery] = useState('')
  const [saving, setSaving] = useState(false)
  const [editLoading, setEditLoading] = useState(false)
  const [formError, setFormError] = useState('')

  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<Partial<Zone>>({})
  const [boundary, setBoundary] = useState<string | null>(null)
  const [drawing, setDrawing] = useState(false)
  const [context, setContext] = useState<FarmZones | null>(null)
  const [contextError, setContextError] = useState('')

  const query = JSON.stringify([farmId, revision])
  const loading = completedQuery !== query

  useEffect(() => {
    let current = true
    Promise.all([farmApi.list(), zoneApi.list(farmId || undefined), farmId ? farmApi.get(farmId) : null])
      .then(([farmList, zones, farm]) => {
        if (!current) return
        setError('')
        setFarms(farmList)
        setResults({ id: farmId, farm, zones })
      })
      .catch(e => {
        if (!current) return
        setResults(null)
        setError(getErrorMessage(e))
      })
      .finally(() => { if (current) setCompletedQuery(query) })
    return () => { current = false }
  }, [farmId, query])

  const chosenFarmId = open ? form.farmId : undefined
  useEffect(() => {
    if (!chosenFarmId) return
    let current = true
    Promise.all([farmApi.get(chosenFarmId), zoneApi.list(chosenFarmId)])
      .then(([farm, zones]) => {
        if (current) setContext({ id: chosenFarmId, farm, zones })
      })
      .catch(e => { if (current) setContextError(getErrorMessage(e)) })
    return () => { current = false }
  }, [chosenFarmId])

  function openForm(zone?: Zone) {
    setEditingId(zone?.id ?? null)
    setFormError('')
    setContext(null)
    setContextError('')
    setDrawing(false)
    setForm(zone ?? { name: '', cropType: '', farmId })
    const saved = readBoundary(zone)
    setBoundary(saved ? JSON.stringify(saved) : null)
    setOpen(true)
  }

  async function openEdit(id: string) {
    setError('')
    setEditLoading(true)
    try {
      openForm(await zoneApi.get(id))
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setEditLoading(false)
    }
  }

  const savedContext = context?.id === chosenFarmId ? context : null
  const farmOutline = savedContext?.farm ? namedBoundary(savedContext.farm) : undefined
  const hasFarmBoundary = farmOutline != null && isValidBoundary(farmOutline.polygon)
  const siblingZones = savedContext?.zones.filter(zone => zone.id !== editingId)
    .flatMap(zone => {
      const item = namedBoundary(zone)
      return item && isValidBoundary(item.polygon) ? [item] : []
    }) ?? []
  const draft = readBoundary({ boundaryGeojson: boundary })
  const validDraft = isValidBoundary(draft)
  const geometryError = boundary && !validDraft ? 'Draw a valid polygon without crossing edges.' :
    draft && hasFarmBoundary ? zoneBoundaryError(draft, farmOutline.polygon, siblingZones) : ''
  const center = validDraft ? boundaryCenter(draft) : undefined

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (saving || drawing || !savedContext || !hasFarmBoundary || !validDraft || !center || geometryError) return
    if (!form.name?.trim()) {
      setFormError('Zone name is required.')
      return
    }

    setFormError('')
    setSaving(true)
    try {
      const payload = { ...form, name: form.name.trim(), boundaryGeojson: boundary, latitude: center[0], longitude: center[1] }
      if (editingId) await zoneApi.update(editingId, payload)
      else await zoneApi.create(payload)
      setOpen(false)
      setRevision(value => value + 1)
    } catch (e) {
      setFormError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  async function changeStatus(zone: Zone) {
    if (saving) return
    setSaving(true)
    setError('')
    try {
      await zoneApi.setActive(zone.id, zone.isActive === false)
      setRevision(value => value + 1)
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const activeResults = results?.id === farmId ? results : null
  const zones = activeResults?.zones ?? []
  const listFarm = activeResults?.farm
  const listOutline = listFarm ? namedBoundary(listFarm) : undefined
  const zoneOutlines = zones.flatMap(zone => {
    const item = namedBoundary(zone)
    return item ? [item] : []
  })
  const points = [listFarm, ...zones].flatMap(item =>
    item && item.latitude != null && item.longitude != null && isCoordinate(item.latitude, item.longitude)
      ? [{ id: item.id, lat: item.latitude, lng: item.longitude, popup: item.name }]
      : [],
  )

  const mapCenter: Coordinates = points.length ? [points[0].lat, points[0].lng] :
    listOutline?.polygon ? (isValidBoundary(listOutline.polygon) ? boundaryCenter(listOutline.polygon) : defaultFarmMapCenter) :
      defaultFarmMapCenter

  return (
    <div>
      <PageHeader
        title="Zone management"
        description="Draw and manage crop zones within farm boundaries."
        actions={<Button onClick={() => openForm()} disabled={editLoading || saving}>Create zone</Button>}
      />
      <ErrorBox message={error} />
      <div className="mb-4 max-w-xs">
        <Select label="Farm" value={farmId} onChange={e => setFarmId(e.target.value)}>
          <option value="">All farms</option>
          {farms.map(farm => <option key={farm.id} value={farm.id}>{farm.name}</option>)}
        </Select>
      </div>
      {listFarm && !isValidBoundary(listOutline?.polygon) && (
        <p className="mb-3 text-sm text-amber-800">Add a boundary in Farm management before drawing zones for this farm.</p>
      )}
      {activeResults && (listOutline || points.length > 0 || zoneOutlines.length > 0) && (
        <FarmZoneMap
          key={farmId}
          center={mapCenter}
          fitBoundary={listOutline?.polygon}
          fitPoints
          farm={listOutline}
          zones={zoneOutlines}
          points={points}
        />
      )}
      <Card className="mt-4">
        {loading ? (
          <p className="py-4 text-center">Loading zones...</p>
        ) : error && !activeResults ? (
          <div className="py-4 text-center">
            <Button variant="outline" onClick={() => setRevision(value => value + 1)}>Retry</Button>
          </div>
        ) : zones.length === 0 ? (
          <Empty text="No zones found." />
        ) : (
          <Table headers={['Zone', 'Farm', 'Crop', 'Area (ha)', 'Sensors', 'Status', 'Actions']}>
            {zones.map(zone => (
              <tr key={zone.id}>
                <td className="px-3 py-2">{zone.name}</td>
                <td className="px-3 py-2">{zone.farmName || zone.farmId}</td>
                <td className="px-3 py-2">{zone.cropType || '—'}</td>
                <td className="px-3 py-2">{zoneArea(zone)}</td>
                <td className="px-3 py-2">{zone.sensorCount ?? '—'}</td>
                <td className="px-3 py-2">{zone.isActive === false ? 'Inactive' : 'Active'}</td>
                <td className="space-x-2 px-3 py-2">
                  <button className="text-brand disabled:opacity-50" disabled={editLoading || saving} onClick={() => openEdit(zone.id)}>Edit</button>
                  <button disabled={saving} onClick={() => changeStatus(zone)}>{zone.isActive === false ? 'Activate' : 'Deactivate'}</button>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <Modal open={open} title={editingId ? 'Edit zone' : 'Create zone'} wide onClose={() => { if (!saving) setOpen(false) }}>
        <form className="space-y-4" onSubmit={onSubmit}>
          <ErrorBox message={formError || contextError || geometryError} />
          <div className="grid gap-3 sm:grid-cols-3">
            <Select label="Farm" aria-label="Farm" disabled={!!editingId || saving} value={form.farmId || ''} onChange={e => {
              setContext(null)
              setContextError('')
              setFormError('')
              setBoundary(null)
              setDrawing(false)
              setForm({ ...form, farmId: e.target.value })
            }}>
              <option value="">Select farm</option>
              {farms.map(farm => <option key={farm.id} value={farm.id}>{farm.name}</option>)}
            </Select>
            <Input label="Zone name" value={form.name || ''} onChange={e => setForm({ ...form, name: e.target.value })} maxLength={100} required />
            <Input label="Crop type" value={form.cropType || ''} onChange={e => setForm({ ...form, cropType: e.target.value })} maxLength={100} />
          </div>
          {!chosenFarmId ? (
            <p className="text-sm text-muted">Select a farm to load its boundary and existing zones.</p>
          ) : !savedContext && !contextError ? (
            <p role="status" className="text-sm text-muted">Loading farm boundary and zones...</p>
          ) : savedContext && !hasFarmBoundary ? (
            <p className="text-sm text-amber-800">Add a valid boundary to this farm in Farm management before saving a zone.</p>
          ) : null}
          {hasFarmBoundary && (
            <>
              <FarmZoneMap
                key={chosenFarmId}
                center={boundaryCenter(farmOutline.polygon)}
                fitBoundary={farmOutline.polygon}
                farm={farmOutline}
                zones={siblingZones}
                points={center ? [{ id: 'center', lat: center[0], lng: center[1] }] : []}
                editor={{ value: boundary, onChange: setBoundary, onBusyChange: setDrawing }}
                height={340}
              />
              <dl className="flex flex-wrap gap-x-8 gap-y-2 rounded-xl bg-stone-50 px-4 py-3 text-sm">
                <div><dt className="text-muted">Area (ha)</dt><dd>{validDraft ? areaInHectares(draft).toFixed(2) : '—'}</dd></div>
                <div><dt className="text-muted">Center coordinates</dt><dd>{center ? center.map(value => value.toFixed(6)).join(', ') : '—'}</dd></div>
              </dl>
            </>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="outline" disabled={saving} onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving || drawing || !hasFarmBoundary || !validDraft || !!geometryError}>
              {saving ? 'Saving...' : 'Save'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
