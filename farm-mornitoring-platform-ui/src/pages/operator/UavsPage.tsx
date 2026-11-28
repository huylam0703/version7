
import { useEffect, useState, type FormEvent } from 'react'
import { farmApi } from '../../api/farm-device/farmApi'
import { uavApi } from '../../api/farm-device/uavApi'
import { getErrorMessage } from '../../api/errorHandler'
import type { Farm } from '../../types'
import type { Uav, UavStatus } from '../../types/uav'
import {
  Button,
  Card,
  Empty,
  ErrorBox,
  Input,
  Modal,
  PageHeader,
  Select,
  Table,
} from '../../components/Ui'

const initialForm: Partial<Uav> = {
  farmId: '',
  code: '',
  name: '',
  model: '',
  maxFlightMin: undefined,
  status: 'ready',
}

const statusLabels: Record<UavStatus, string> = {
  ready: 'Ready',
  in_mission: 'In mission',
  maintenance: 'Maintenance',
  retired: 'Retired',
}

export function UavsPage() {
  const [items, setItems] = useState<Uav[]>([])
  const [farms, setFarms] = useState<Farm[]>([])
  const [farmId, setFarmId] = useState('')

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')

  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<Partial<Uav>>(initialForm)

  async function loadFarms() {
    try {
      const data = await farmApi.list()
      setFarms(data)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  async function load(selectedFarmId = farmId) {
    try {
      setLoading(true)
      setError('')

      const data = await uavApi.list(
        selectedFarmId || undefined
      )

      setItems(data)
    } catch (e) {
      setItems([])
      setError(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadFarms()
  }, [])

  useEffect(() => {
    void load(farmId)
  }, [farmId])

  function openCreate() {
    setEditingId(null)
    setFormError('')

    setForm({
      ...initialForm,
      farmId,
    })

    setOpen(true)
  }

  async function openEdit(id: string) {
    try {
      setError('')
      setFormError('')

      const uav = await uavApi.get(id)

      setEditingId(uav.id)
      setForm(uav)
      setOpen(true)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()

    if (saving) return

    setFormError('')

    if (!form.name?.trim()) {
      setFormError('UAV name is required.')
      return
    }

    if (!editingId) {
      if (!form.farmId) {
        setFormError('Please select a farm.')
        return
      }

      if (!form.code?.trim()) {
        setFormError('Serial number is required.')
        return
      }
    }

    if (
      form.maxFlightMin !== undefined &&
      (
        !Number.isInteger(form.maxFlightMin) ||
        form.maxFlightMin <= 0 ||
        form.maxFlightMin > 32767
      )
    ) {
      setFormError('Max flight time must be a positive integer.')
      return
    }

    try {
      setSaving(true)

      if (editingId) {
        await uavApi.update(editingId, {
          name: form.name.trim(),
          model: form.model?.trim(),
          maxFlightMin: form.maxFlightMin,
          status: form.status,
        })
      } else {
        await uavApi.create({
          farmId: form.farmId,
          code: form.code?.trim(),
          name: form.name.trim(),
          model: form.model?.trim(),
          maxFlightMin: form.maxFlightMin,
        })
      }

      setOpen(false)
      setForm(initialForm)

      await load()
    } catch (e) {
      setFormError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <PageHeader
        title="UAV management"
        description="Register UAVs used as collection platforms. This app does not fly the UAV."
        actions={
          <Button onClick={openCreate}>
            Add UAV
          </Button>
        }
      />

      <ErrorBox message={error} />

      <Card className="mb-4">
        <Select
          label="Farm"
          value={farmId}
          onChange={e => setFarmId(e.target.value)}
        >
          <option value="">All farms</option>

          {farms.map(f => (
            <option key={f.id} value={f.id}>
              {f.name}
            </option>
          ))}
        </Select>
      </Card>

      <Card>
        {loading ? (
          <p className="py-4 text-center">
            Loading UAVs...
          </p>
        ) : error && items.length === 0 ? (
          <div className="py-4 text-center">
            <Button
              variant="outline"
              onClick={() => {
                void loadFarms()
                void load()
              }}
            >
              Retry
            </Button>
          </div>
        ) : items.length === 0 ? (
          <Empty text="No UAVs found." />
        ) : (
          <Table
            headers={[
              'Serial Number',
              'Name',
              'Farm',
              'Model',
              'Max Flight Time',
              'Status',
              'Actions',
            ]}
          >
            {items.map(u => (
              <tr key={u.id}>
                <td className="px-3 py-2">
                  {u.code}
                </td>

                <td className="px-3 py-2">
                  {u.name}
                </td>

                <td className="px-3 py-2">
                  {farms.find(f => f.id === u.farmId)?.name || '—'}
                </td>

                <td className="px-3 py-2">
                  {u.model || '—'}
                </td>

                <td className="px-3 py-2">
                  {u.maxFlightMin != null
                    ? `${u.maxFlightMin} min`
                    : '—'}
                </td>

                <td className="px-3 py-2">
                  {u.status
                    ? statusLabels[u.status] || u.status
                    : '—'}
                </td>

                <td className="px-3 py-2">
                  <button
                    className="text-brand"
                    onClick={() => openEdit(u.id)}
                  >
                    Edit
                  </button>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <Modal
        open={open}
        title={editingId ? 'Edit UAV' : 'Add UAV'}
        onClose={() => {
          if (!saving) setOpen(false)
        }}
      >
        <form className="space-y-3" onSubmit={onSubmit}>
          <ErrorBox message={formError} />

          <Select
            label="Farm"
            value={form.farmId || ''}
            disabled={!!editingId}
            onChange={e =>
              setForm({
                ...form,
                farmId: e.target.value,
              })
            }
            required
          >
            <option value="">Select farm</option>

            {farms.map(f => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </Select>

          <Input
            label="Serial Number"
            value={form.code || ''}
            disabled={!!editingId}
            maxLength={50}
            onChange={e =>
              setForm({
                ...form,
                code: e.target.value,
              })
            }
            required
          />

          <Input
            label="Name"
            value={form.name || ''}
            maxLength={100}
            onChange={e =>
              setForm({
                ...form,
                name: e.target.value,
              })
            }
            required
          />

          <Input
            label="Model"
            value={form.model || ''}
            maxLength={100}
            onChange={e =>
              setForm({
                ...form,
                model: e.target.value,
              })
            }
          />

          <Input
            label="Max Flight Time (minutes)"
            type="number"
            min={1}
            max={32767}
            step={1}
            value={form.maxFlightMin ?? ''}
            onChange={e =>
              setForm({
                ...form,
                maxFlightMin:
                  e.target.value === ''
                    ? undefined
                    : Number(e.target.value),
              })
            }
          />

          {editingId && (
            <Select
              label="Status"
              value={form.status || 'ready'}
              onChange={e =>
                setForm({
                  ...form,
                  status: e.target.value as UavStatus,
                })
              }
            >
              {Object.entries(statusLabels).map(
                ([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                )
              )}
            </Select>
          )}

          <div className="flex justify-end gap-2">
            <Button
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
