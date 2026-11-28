import { useEffect, useState, type FormEvent } from 'react'
import { gatewayApi } from '../../api/farm-device/gatewayApi'
import { uavApi } from '../../api/farm-device/uavApi'
import { farmApi } from '../../api/farm-device/farmApi'
import { getErrorMessage } from '../../api/errorHandler'
import type { Gateway, GatewayStatus } from '../../types/gateway'
import type { Uav } from '../../types/uav'
import type { Farm } from '../../types'
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

type GatewayForm = Partial<Gateway> & {
  authKey?: string
}

const initialForm: GatewayForm = {
  code: '',
  name: '',
  uavId: '',
  hwModel: '',
  firmwareVer: '',
  storageMb: undefined,
  status: 'ready',
  authKey: '',
}

const statusLabels: Record<GatewayStatus, string> = {
  ready: 'Ready',
  in_use: 'In use',
  maintenance: 'Maintenance',
  retired: 'Retired',
}

function generateAuthKey() {
  const bytes = crypto.getRandomValues(new Uint8Array(24))

  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, '0'))
    .join('')
}

export function GatewaysPage() {
  const [items, setItems] = useState<Gateway[]>([])
  const [uavs, setUavs] = useState<Uav[]>([])
  const [farms, setFarms] = useState<Farm[]>([])

  const [farmId, setFarmId] = useState('')
  const [uavId, setUavId] = useState('')
  const [status, setStatus] = useState<GatewayStatus | ''>('')

  const [formFarmId, setFormFarmId] = useState('')
  const [originalGateway, setOriginalGateway] =
    useState<Gateway | null>(null)

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')

  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<GatewayForm>(initialForm)

  const isBusy = originalGateway?.status === 'in_use'

  const filterUavs = uavs.filter(
    u => !farmId || u.farmId === farmId
  )

  const formUavs = uavs.filter(
    u => !formFarmId || u.farmId === formFarmId
  )

  async function loadMasterData() {
    try {
      const [farmData, uavData] = await Promise.all([
        farmApi.list(),
        uavApi.list(),
      ])

      setFarms(farmData)
      setUavs(uavData)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  async function load() {
    try {
      setLoading(true)
      setError('')

      let data: Gateway[]

      if (uavId) {
        data = await gatewayApi.list(
          uavId,
          status || undefined
        )
      } else if (farmId) {
        const farmUavs = await uavApi.list(farmId)

        const groups = await Promise.all(
          farmUavs.map(u =>
            gatewayApi.list(
              u.id,
              status || undefined
            )
          )
        )

        data = groups.flat()
      } else {
        data = await gatewayApi.list(
          undefined,
          status || undefined
        )
      }

      setItems(data)
    } catch (e) {
      setItems([])
      setError(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadMasterData()
  }, [])

  useEffect(() => {
    void load()
  }, [farmId, uavId, status])

  function openCreate() {
    setEditingId(null)
    setOriginalGateway(null)
    setFormError('')
    setFormFarmId(farmId)

    setForm({
      ...initialForm,
      uavId,
      authKey: generateAuthKey(),
    })

    setOpen(true)
  }

  async function openEdit(id: string) {
    try {
      setError('')
      setFormError('')

      const gateway = await gatewayApi.get(id)

      const linkedUav = uavs.find(
        u => u.id === gateway.uavId
      )

      setEditingId(gateway.id)
      setOriginalGateway(gateway)
      setFormFarmId(linkedUav?.farmId || '')

      setForm({
        ...gateway,
        uavId: gateway.uavId || '',
      })

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
      setFormError('Gateway name is required.')
      return
    }

    if (!editingId && !form.code?.trim()) {
      setFormError('Gateway UID is required.')
      return
    }

    if (
      form.storageMb !== undefined &&
      (
        !Number.isInteger(form.storageMb) ||
        form.storageMb <= 0 ||
        form.storageMb > 2147483647
      )
    ) {
      setFormError('Storage must be a valid positive integer.')
      return
    }

    try {
      setSaving(true)

      if (editingId) {
        const body: Partial<Gateway> = {
          name: form.name.trim(),
          hwModel: form.hwModel?.trim(),
          firmwareVer: form.firmwareVer?.trim(),
          storageMb: form.storageMb,
        }

        if (!isBusy) {
          if (
            (form.uavId || '') !==
            (originalGateway?.uavId || '')
          ) {
            body.uavId = form.uavId || ''
          }

          if (
            form.status !== originalGateway?.status &&
            form.status !== 'in_use'
          ) {
            body.status = form.status
          }
        }

        await gatewayApi.update(editingId, body)
      } else {
        if (
          !form.authKey ||
          form.authKey.length < 16 ||
          form.authKey.length > 72
        ) {
          setFormError(
            'Authentication key must contain 16–72 characters.'
          )
          return
        }

        await gatewayApi.create({
          code: form.code?.trim(),
          name: form.name.trim(),
          uavId: form.uavId || undefined,
          hwModel: form.hwModel?.trim(),
          firmwareVer: form.firmwareVer?.trim(),
          storageMb: form.storageMb,
          authKey: form.authKey,
        })
      }

      setOpen(false)
      setForm(initialForm)
      setOriginalGateway(null)

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
        title="Mobile gateway management"
        description="Associate a collection gateway with a UAV."
        actions={
          <Button onClick={openCreate}>
            Add gateway
          </Button>
        }
      />

      <ErrorBox message={error} />

      <Card className="mb-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <Select
            label="Farm"
            value={farmId}
            onChange={e => {
              setFarmId(e.target.value)
              setUavId('')
            }}
          >
            <option value="">All farms</option>

            {farms.map(f => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </Select>

          <Select
            label="UAV"
            value={uavId}
            onChange={e => setUavId(e.target.value)}
          >
            <option value="">All UAVs</option>

            {filterUavs.map(u => (
              <option key={u.id} value={u.id}>
                {u.name}
              </option>
            ))}
          </Select>

          <Select
            label="Status"
            value={status}
            onChange={e =>
              setStatus(e.target.value as GatewayStatus | '')
            }
          >
            <option value="">All statuses</option>

            {Object.entries(statusLabels).map(
              ([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              )
            )}
          </Select>
        </div>
      </Card>

      <Card>
        {loading ? (
          <p className="py-4 text-center">
            Loading gateways...
          </p>
        ) : error && items.length === 0 ? (
          <div className="py-4 text-center">
            <Button
              variant="outline"
              onClick={() => {
                void loadMasterData()
                void load()
              }}
            >
              Retry
            </Button>
          </div>
        ) : items.length === 0 ? (
          <Empty text="No gateways found." />
        ) : (
          <Table
            headers={[
              'Gateway UID',
              'Name',
              'Farm',
              'UAV',
              'Hardware',
              'Firmware',
              'Storage',
              'Status',
              'Actions',
            ]}
          >
            {items.map(g => {
              const linkedUav = uavs.find(
                u => u.id === g.uavId
              )

              const linkedFarm = farms.find(
                f => f.id === linkedUav?.farmId
              )

              return (
                <tr key={g.id}>
                  <td className="px-3 py-2">
                    {g.code}
                  </td>

                  <td className="px-3 py-2">
                    {g.name}
                  </td>

                  <td className="px-3 py-2">
                    {linkedFarm?.name || '—'}
                  </td>

                  <td className="px-3 py-2">
                    {linkedUav?.name ||
                      (g.uavId ? g.uavId : 'Not linked')}
                  </td>

                  <td className="px-3 py-2">
                    {g.hwModel || '—'}
                  </td>

                  <td className="px-3 py-2">
                    {g.firmwareVer || '—'}
                  </td>

                  <td className="px-3 py-2">
                    {g.storageMb != null
                      ? `${g.storageMb} MB`
                      : '—'}
                  </td>

                  <td className="px-3 py-2">
                    {g.status
                      ? statusLabels[g.status] || g.status
                      : '—'}
                  </td>

                  <td className="px-3 py-2">
                    <button
                      className="text-brand"
                      onClick={() => openEdit(g.id)}
                    >
                      Edit
                    </button>
                  </td>
                </tr>
              )
            })}
          </Table>
        )}
      </Card>

      <Modal
        open={open}
        title={editingId ? 'Edit gateway' : 'Add gateway'}
        onClose={() => {
          if (!saving) setOpen(false)
        }}
      >
        <form className="space-y-3" onSubmit={onSubmit}>
          <ErrorBox message={formError} />

          {isBusy && (
            <div className="rounded-lg bg-yellow-50 p-3 text-sm text-yellow-800">
              This gateway is currently being used in a mission.
              UAV assignment and status cannot be changed.
            </div>
          )}

          <Input
            label="Gateway UID"
            value={form.code || ''}
            maxLength={50}
            disabled={!!editingId}
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

          <Select
            label="Farm"
            value={formFarmId}
            disabled={isBusy}
            onChange={e => {
              setFormFarmId(e.target.value)
              setForm({
                ...form,
                uavId: '',
              })
            }}
          >
            <option value="">All farms</option>

            {farms.map(f => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </Select>

          <Select
            label="UAV"
            value={form.uavId || ''}
            disabled={isBusy}
            onChange={e =>
              setForm({
                ...form,
                uavId: e.target.value,
              })
            }
          >
            <option value="">Not linked</option>

            {formUavs.map(u => (
              <option key={u.id} value={u.id}>
                {u.name}
              </option>
            ))}
          </Select>

          <Input
            label="Hardware model"
            value={form.hwModel || ''}
            maxLength={100}
            onChange={e =>
              setForm({
                ...form,
                hwModel: e.target.value,
              })
            }
          />

          <Input
            label="Firmware version"
            value={form.firmwareVer || ''}
            maxLength={20}
            onChange={e =>
              setForm({
                ...form,
                firmwareVer: e.target.value,
              })
            }
          />

          <Input
            label="Storage (MB)"
            type="number"
            min={1}
            max={2147483647}
            step={1}
            value={form.storageMb ?? ''}
            onChange={e =>
              setForm({
                ...form,
                storageMb:
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
              disabled={isBusy}
              onChange={e =>
                setForm({
                  ...form,
                  status: e.target.value as GatewayStatus,
                })
              }
            >
              <option value="ready">Ready</option>
              <option value="maintenance">Maintenance</option>
              <option value="retired">Retired</option>

              {isBusy && (
                <option value="in_use">In use</option>
              )}
            </Select>
          )}

          {!editingId && (
            <div>
              <Input
                label="Authentication key"
                value={form.authKey || ''}
                minLength={16}
                maxLength={72}
                onChange={e =>
                  setForm({
                    ...form,
                    authKey: e.target.value,
                  })
                }
                required
              />

              <div className="mt-2 flex gap-3">
                <button
                  type="button"
                  className="text-sm text-brand"
                  onClick={() =>
                    setForm({
                      ...form,
                      authKey: generateAuthKey(),
                    })
                  }
                >
                  Generate new key
                </button>

                <button
                  type="button"
                  className="text-sm text-brand"
                  onClick={async () => {
                    try {
                      await navigator.clipboard.writeText(
                        form.authKey || ''
                      )
                    } catch {
                      setFormError(
                        'Unable to copy authentication key.'
                      )
                    }
                  }}
                >
                  Copy key
                </button>
              </div>

              <p className="mt-2 text-xs text-gray-500">
                Save this key securely before registering
                the gateway. It will not be displayed again.
              </p>
            </div>
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
