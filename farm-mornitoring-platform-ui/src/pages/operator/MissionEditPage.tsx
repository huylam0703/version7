
import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getErrorMessage, missionApi } from '../../api'
import type { Mission } from '../../types'
import {
  Button,
  Card,
  ErrorBox,
  Input,
  PageHeader,
  Textarea,
} from '../../components/Ui'

// Chuyển ISO date sang định dạng datetime-local
function toLocalDateTime(value?: string) {
  if (!value) return ''

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) return ''

  const offset = date.getTimezoneOffset()
  const localDate = new Date(date.getTime() - offset * 60000)

  return localDate.toISOString().slice(0, 16)
}

export function MissionEditPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [mission, setMission] = useState<Mission | null>(null)
  const [name, setName] = useState('')
  const [notes, setNotes] = useState('')
  const [plannedAt, setPlannedAt] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!id) return

    const fetchMission = async () => {
      try {
        setError('')

        const data = await missionApi.get(id)

        setMission(data)
        setName(data.name)
        setNotes(data.notes || '')
        setPlannedAt(toLocalDateTime(data.scheduledAt))
      } catch (e) {
        setError(getErrorMessage(e))
      } finally {
        setLoading(false)
      }
    }

    fetchMission()
  }, [id])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()

    if (!id) return

    try {
      setError('')
      setSaving(true)

      await missionApi.update(id, {
        name,
        notes,
        scheduledAt: plannedAt
          ? new Date(plannedAt).toISOString()
          : undefined,
      })

      navigate(`/missions/${id}`)
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <p className="p-4 text-gray-500">Loading mission...</p>
  }

  if (!mission) {
    return (
      <div>
        <PageHeader title="Edit mission" />
        <ErrorBox message={error || 'Mission not found'} />
      </div>
    )
  }

  if (mission.status !== 'planned') {
    return (
      <div>
        <PageHeader title="Edit mission" />
        <ErrorBox message="Only planned missions can be edited." />
        <Button onClick={() => navigate(`/missions/${id}`)}>
          Back to monitor
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        title="Edit mission"
        description="Update mission name, notes and planned time."
      />

      <ErrorBox message={error} />

      <Card>
        <form onSubmit={onSubmit} className="space-y-4">
          <Input
            label="Mission name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={150}
            required
          />

          <Input
            label="Planned at"
            type="datetime-local"
            value={plannedAt}
            onChange={(e) => setPlannedAt(e.target.value)}
            required
          />

          <Textarea
            label="Operational notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={5}
          />

          <div className="flex gap-2">
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving...' : 'Save changes'}
            </Button>

            <Button
              variant="outline"
              onClick={() => navigate(`/missions/${id}`)}
              disabled={saving}
            >
              Cancel
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
