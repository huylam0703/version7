import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getErrorMessage, missionApi } from '../../api'
import type { Mission, MissionStatus } from '../../types'
import { Button, Card, ErrorBox, PageHeader, Select, StatCard, Textarea, missionBadge } from '../../components/Ui'
import { MapView } from '../../components/MapView'

export function MissionMonitorPage() {
  const { id } = useParams()
  const [mission, setMission] = useState<Mission | null>(null)
  const [status, setStatus] = useState<MissionStatus>('planned')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState('')

  async function load() {
    if (!id) return
    try {
      setError('')
      const data = await missionApi.get(id)
      setMission(data)
      setStatus(data.status)
      setNotes(data.notes || '')
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [id])

  async function saveStatus() {
    if (!id) return
    try {
      await missionApi.updateStatus(id, status, notes)
      await load()
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  const hasTelemetry = mission && (mission.latitude || mission.batteryPercent != null || mission.waypointProgress != null)

  return (
    <div>
      <PageHeader title={mission?.name || 'Mission monitoring'} description="Telemetry is shown only when the backend sends it." />
      <ErrorBox message={error} />
      <div className="grid gap-3 sm:grid-cols-4">
        <div className="rounded-xl border bg-white p-4">
          <p className="text-sm text-gray-500">Status</p>
          <div className="mt-2">{missionBadge(mission?.status)}</div>
        </div>
        <StatCard label="Battery" value={mission?.batteryPercent != null ? `${mission.batteryPercent}%` : 'No telemetry'} />
        <StatCard label="Waypoint progress" value={mission?.waypointProgress != null ? `${mission.waypointProgress}%` : 'No telemetry'} />
        <StatCard label="Collection progress" value={mission?.collectionProgress != null ? `${mission.collectionProgress}%` : 'No telemetry'} />
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        {hasTelemetry && mission?.latitude && mission?.longitude ? (
          <MapView center={[mission.latitude, mission.longitude]} points={[{ id: mission.id, lat: mission.latitude, lng: mission.longitude, popup: mission.name }]} />
        ) : (
          <Card>
            <p className="text-sm text-gray-500">No live UAV location yet. The map appears when telemetry is available.</p>
          </Card>
        )}
        <Card className="space-y-3">
          <p className="font-medium">Manual status update</p>
          <Select value={status} onChange={(e) => setStatus(e.target.value as MissionStatus)}>
            <option value="planned">Planned</option>
            <option value="inProgress">In progress</option>
            <option value="paused">Paused</option>
            <option value="completed">Completed</option>
            <option value="failed">Failed</option>
            <option value="cancelled">Cancelled</option>
          </Select>
          <Textarea label="Operational notes" value={notes} onChange={(e) => setNotes(e.target.value)} rows={5} />
          <Button onClick={saveStatus}>Save</Button>
        </Card>
      </div>
    </div>
  )
}
