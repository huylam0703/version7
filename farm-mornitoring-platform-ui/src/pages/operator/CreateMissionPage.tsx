import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { farmApi, gatewayApi, getErrorMessage, missionApi, sensorApi, uavApi } from '../../api'
import type { Farm, Gateway, SensorNode, Uav } from '../../types'
import { Button, Card, ErrorBox, Input, PageHeader, Select, Textarea } from '../../components/Ui'
import { MapView } from '../../components/MapView'

export function CreateMissionPage() {
  const navigate = useNavigate()
  const [farms, setFarms] = useState<Farm[]>([])
  const [nodes, setNodes] = useState<SensorNode[]>([])
  const [uavs, setUavs] = useState<Uav[]>([])
  const [gateways, setGateways] = useState<Gateway[]>([])
  const [error, setError] = useState('')
  const [form, setForm] = useState({
    name: '',
    farmId: '',
    uavId: '',
    gatewayId: '',
    scheduledAt: '',
    notes: '',
    sensorNodeIds: [] as string[],
  })

  useEffect(() => {
    Promise.all([farmApi.list(), sensorApi.list(), uavApi.list(), gatewayApi.list()])
      .then(([f, n, u, g]) => {
        setFarms(f); setNodes(n); setUavs(u); setGateways(g)
      })
      .catch((e) => setError(getErrorMessage(e)))
  }, [])

  const selectedNodes = nodes.filter((n) => form.sensorNodeIds.includes(n.id))
  const points = selectedNodes.filter((n) => n.latitude && n.longitude).map((n, i) => ({
    id: n.id,
    lat: n.latitude as number,
    lng: n.longitude as number,
    popup: `WP ${i + 1}: ${n.code}`,
  }))

  function toggleNode(id: string) {
    setForm((curr) => ({
      ...curr,
      sensorNodeIds: curr.sensorNodeIds.includes(id)
        ? curr.sensorNodeIds.filter((x) => x !== id)
        : [...curr.sensorNodeIds, id],
    }))
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      const waypoints = selectedNodes.map((n, i) => ({
        order: i + 1,
        latitude: n.latitude || 0,
        longitude: n.longitude || 0,
        sensorNodeId: n.id,
      }))
      const created = await missionApi.create({ ...form, waypoints, status: 'planned' })
      navigate(`/missions/${created.id}`)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div>
      <PageHeader title="Create collection mission" description="Select farm, nodes, UAV, gateway and waypoints. No direct flight control." />
      <ErrorBox message={error} />
      <form className="grid gap-4 lg:grid-cols-2" onSubmit={onSubmit}>
        <Card className="space-y-3">
          <Input label="Mission name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Select label="Farm" value={form.farmId} onChange={(e) => setForm({ ...form, farmId: e.target.value })}>
            <option value="">Select farm</option>
            {farms.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </Select>
          <Select label="UAV" value={form.uavId} onChange={(e) => setForm({ ...form, uavId: e.target.value })}>
            <option value="">Select UAV</option>
            {uavs.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
          </Select>
          <Select label="Gateway" value={form.gatewayId} onChange={(e) => setForm({ ...form, gatewayId: e.target.value })}>
            <option value="">Select gateway</option>
            {gateways.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </Select>
          <Input label="Scheduled at" type="datetime-local" value={form.scheduledAt} onChange={(e) => setForm({ ...form, scheduledAt: e.target.value })} />
          <Textarea label="Operational notes" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          <div>
            <p className="mb-2 text-sm font-medium">Sensor nodes / waypoints</p>
            <div className="max-h-48 space-y-1 overflow-y-auto rounded-lg border p-2">
              {nodes.map((n) => (
                <label key={n.id} className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.sensorNodeIds.includes(n.id)} onChange={() => toggleNode(n.id)} />
                  {n.code} — {n.name}
                </label>
              ))}
            </div>
          </div>
          <Button type="submit">Create mission</Button>
        </Card>
        <MapView points={points} />
      </form>
    </div>
  )
}
