import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getErrorMessage, missionApi } from '../../api'
import type { Mission } from '../../types'

import type {
  MissionProgressDto,
  MissionStatusLogDto,
} from '../../types/mission'
import {
  Button,
  Card,
  ErrorBox,
  PageHeader,
  StatCard,
  missionBadge,
} from '../../components/Ui'
import { MapView } from '../../components/MapView'

export function MissionMonitorPage() {
  const { id } = useParams()
  const [mission, setMission] = useState<Mission | null>(null)
  const [error, setError] = useState('')
  const [progress, setProgress] = useState<MissionProgressDto | null>(null)
  const [statusLogs, setStatusLogs] = useState<MissionStatusLogDto[]>([])
  const [actionLoading, setActionLoading] = useState(false)

  async function load() {
    if (!id) return
    try {
      setError('')
      const [data, progressData, logsData] = await Promise.all([
        missionApi.get(id),
        missionApi.progress(id).catch(() => null),
        missionApi.statusLogs(id).catch(() => []),
      ])

      setMission(data)
      setProgress(progressData)
      setStatusLogs(logsData)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [id])

  async function startMission() {
    if (!id) return

    try {
      setActionLoading(true)
      setError('')

      await missionApi.start(id)
      await load()
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setActionLoading(false)
    }
  }

  async function cancelMission() {
    if (!id) return

    try {
      setActionLoading(true)
      setError('')

      await missionApi.cancel(id)
      await load()
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setActionLoading(false)
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
        <StatCard
          label="Waypoint progress"
          value={
            progress
              ? `${progress.reachedWaypoints}/${progress.totalWaypoints}`
              : 'No progress'
          }
        />

        <StatCard
          label="Collection progress"
          value={
            progress
              ? `${progress.progressPercent}%`
              : 'No progress'
          }
        />
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        {mission?.latitude != null && mission?.longitude != null ? (
          <MapView center={[mission.latitude, mission.longitude]} points={[{ id: mission.id, lat: mission.latitude, lng: mission.longitude, popup: mission.name }]} />
        ) : (
          <Card>
            <p className="text-sm text-gray-500">No live UAV location yet. The map appears when telemetry is available.</p>
          </Card>
        )}
        <Card className="space-y-3">
          <p className="font-medium">Mission actions</p>

          {mission?.status === 'planned' && (
            <Button
              onClick={startMission}
              disabled={actionLoading}
            >
              {actionLoading ? 'Processing...' : 'Start mission'}
            </Button>
          )}

          {(mission?.status === 'planned' ||
            mission?.status === 'inProgress') && (
            <Button
              onClick={cancelMission}
              disabled={actionLoading}
            >
              {actionLoading ? 'Processing...' : 'Cancel mission'}
            </Button>
          )}

          {mission?.status !== 'planned' &&
            mission?.status !== 'inProgress' && (
              <p className="text-sm text-gray-500">
                No actions available for this mission status.
              </p>
            )}
        </Card>
      </div>
      <div className="mt-4">
        <Card className="space-y-3">
          <h3 className="font-medium">Mission status history</h3>

          {statusLogs.length === 0 ? (
            <p className="text-sm text-gray-500">
              No status history available.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left">
                    <th className="px-3 py-2">Old status</th>
                    <th className="px-3 py-2">New status</th>
                    <th className="px-3 py-2">Source</th>
                    <th className="px-3 py-2">Note</th>
                    <th className="px-3 py-2">Time</th>
                  </tr>
                </thead>

                <tbody>
                  {statusLogs.map((log) => (
                    <tr key={log.logId} className="border-b">
                      <td className="px-3 py-2">
                        {log.oldStatus || '—'}
                      </td>
                      <td className="px-3 py-2">
                        {log.newStatus}
                      </td>
                      <td className="px-3 py-2">
                        {log.source || '—'}
                      </td>
                      <td className="px-3 py-2">
                        {log.note || '—'}
                      </td>
                      <td className="px-3 py-2">
                        {log.createdAt
                          ? new Date(log.createdAt).toLocaleString()
                          : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </div>
  )
}
