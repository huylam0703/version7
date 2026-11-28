import { useEffect, useState, type FormEvent } from 'react'
import { farmApi } from '../../api/farm-device/farmApi'
import { zoneApi } from '../../api/farm-device/zoneApi'
import { sensorNodeApi } from '../../api/farm-device/sensorNodeApi'
import { sensorTypeApi } from '../../api/farm-device/sensorTypeApi'
import { getErrorMessage } from '../../api/errorHandler'
import type { Zone, Farm } from '../../types'
import type {
  SensorTypeDto,
  SensorNodeDto,
  CreateNodeRequest,
  UpdateNodeRequest,
} from '../../types/sensor'
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
  nodeStatusBadge,
} from '../../components/Ui'

type NodeForm = Partial<CreateNodeRequest & UpdateNodeRequest>

const initialForm: NodeForm = {
  deviceUid: '',
  name: '',
  zoneId: '',
  authKey: '',
  firmwareVer: '',
  channels: [],
  status: 'ACTIVE',
}

function generateAuthKey() {
  const bytes = crypto.getRandomValues(new Uint8Array(24))
  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, '0'))
    .join('')
}

export function NodesPage() {
  const [nodes, setNodes] = useState<SensorNodeDto[]>([])
  const [sensorTypes, setSensorTypes] = useState<SensorTypeDto[]>([])
  const [farms, setFarms] = useState<Farm[]>([])
  const [zones, setZones] = useState<Zone[]>([])

  const [farmId, setFarmId] = useState('')
  const [zoneId, setZoneId] = useState('')
  const [formFarmId, setFormFarmId] = useState('')

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')

  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<NodeForm>(initialForm)

  const filterZones = zones.filter(
    z => !farmId || z.farmId === farmId
  )

  const formZones = zones.filter(
    z => z.farmId === formFarmId
  )

  async function loadMasterData() {
    try {
      const [farmData, zoneData, typeData] =
        await Promise.all([
          farmApi.list(),
          zoneApi.list(),
          sensorTypeApi.list(),
        ])

      setFarms(farmData)
      setZones(zoneData)
      setSensorTypes(typeData)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  async function load(
    selectedFarmId = farmId,
    selectedZoneId = zoneId
  ) {
    try {
      setLoading(true)
      setError('')

      const params = {
        farmId: selectedFarmId || undefined,
        zoneId: selectedZoneId || undefined,
        pageSize: 100,
      }

      const firstPage = await sensorNodeApi.list({
        ...params,
        page: 1,
      })

      const items = [...firstPage.items]

      const totalPages = Math.ceil(
        firstPage.total / firstPage.pageSize
      )

      for (let page = 2; page <= totalPages; page++) {
        const result = await sensorNodeApi.list({
          ...params,
          page,
        })

        items.push(...result.items)
      }

      setNodes(items)
    } catch (e) {
      setNodes([])
      setError(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadMasterData()
  }, [])

  useEffect(() => {
    void load(farmId, zoneId)
  }, [farmId, zoneId])

  function openCreate() {
    setEditingId(null)
    setFormError('')
    setFormFarmId(farmId)

    setForm({
      ...initialForm,
      zoneId: zoneId,
      authKey: generateAuthKey(),
      channels: [
        {
          typeId: '',
          channelNo: 0,
          calibrationOffset: 0,
        },
      ],
    })

    setOpen(true)
  }

  async function openEdit(id: string) {
    try {
      setError('')
      setFormError('')

      const node = await sensorNodeApi.get(id)

      setEditingId(node.nodeId)
      setFormFarmId(node.farmId)

      setForm({
        deviceUid: node.deviceUid,
        name: node.name,
        zoneId: node.zoneId,
        lat: node.lat ?? undefined,
        lng: node.lng ?? undefined,
        firmwareVer: node.firmwareVer ?? undefined,
        status: node.status,
        installedAt: node.installedAt ?? undefined,
        endpointUrl: node.endpointUrl ?? undefined,
        channels: node.channels.map(c => ({
          typeId: c.typeId,
          channelNo: c.channelNo,
          calibrationOffset: c.calibrationOffset,
        })),
      })

      setOpen(true)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  function addChannel() {
    setForm(prev => {
      const channels = prev.channels ?? []

      const channelNo = Array.from(
        { length: 256 },
        (_, index) => index
      ).find(no => !channels.some(c => c.channelNo === no))

      if (channelNo === undefined) return prev

      return {
        ...prev,
        channels: [
          ...channels,
          {
            typeId: '',
            channelNo,
            calibrationOffset: 0,
          },
        ],
      }
    })
  }

  function removeChannel(index: number) {
    setForm(prev => ({
      ...prev,
      channels: (prev.channels ?? []).filter(
        (_, i) => i !== index
      ),
    }))
  }

  function updateChannel(
    index: number,
    value: Partial<CreateNodeRequest['channels'][number]>
  ) {
    setForm(prev => ({
      ...prev,
      channels: (prev.channels ?? []).map((channel, i) =>
        i === index
          ? { ...channel, ...value }
          : channel
      ),
    }))
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()

    if (saving) return

    setFormError('')

    if (!form.name?.trim() || !form.zoneId) {
      setFormError('Please enter name and select a zone.')
      return
    }

    try {
      setSaving(true)

      if (editingId) {
        const body: UpdateNodeRequest = {
          name: form.name.trim(),
          zoneId: form.zoneId,
          lat: form.lat,
          lng: form.lng,
          firmwareVer: form.firmwareVer,
          status: form.status,
        }

        await sensorNodeApi.update(editingId, body)
      } else {
        const channels = form.channels ?? []

        if (!form.deviceUid?.trim()) {
          setFormError('Device UID is required.')
          return
        }

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

        if (
          channels.length === 0 ||
          channels.some(c => !c.typeId)
        ) {
          setFormError(
            'Please configure at least one sensor channel.'
          )
          return
        }

        if (
          new Set(channels.map(c => c.channelNo)).size
          !== channels.length
        ) {
          setFormError('Channel numbers must be unique.')
          return
        }

        const body: CreateNodeRequest = {
          deviceUid: form.deviceUid.trim(),
          name: form.name.trim(),
          zoneId: form.zoneId,
          authKey: form.authKey,
          lat: form.lat,
          lng: form.lng,
          firmwareVer: form.firmwareVer,
          channels,
        }

        await sensorNodeApi.create(body)
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
        title="Sensor node management"
        description="Register nodes and assign them to farm zones."
        actions={
          <Button onClick={openCreate}>
            Register node
          </Button>
        }
      />

      <ErrorBox message={error} />

      <Card className="mb-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Select
            label="Farm"
            value={farmId}
            onChange={e => {
              setFarmId(e.target.value)
              setZoneId('')
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
            label="Zone"
            value={zoneId}
            onChange={e => setZoneId(e.target.value)}
          >
            <option value="">All zones</option>

            {filterZones.map(z => (
              <option key={z.id} value={z.id}>
                {z.name}
              </option>
            ))}
          </Select>
        </div>
      </Card>

      <Card>
        {loading ? (
          <p className="py-4 text-center">
            Loading nodes...
          </p>
        ) : error && nodes.length === 0 ? (
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
        ) : nodes.length === 0 ? (
          <Empty text="No sensor nodes found." />
        ) : (
          <Table
            headers={[
              'Device UID',
              'Name',
              'Farm',
              'Zone',
              'Sensor Channels',
              'Firmware',
              'Status',
              'Actions',
            ]}
          >
            {nodes.map(n => (
              <tr key={n.nodeId}>
                <td className="px-3 py-2">
                  {n.deviceUid}
                </td>

                <td className="px-3 py-2">
                  {n.name}
                </td>

                <td className="px-3 py-2">
                  {farms.find(f => f.id === n.farmId)?.name || '—'}
                </td>

                <td className="px-3 py-2">
                  {zones.find(z => z.id === n.zoneId)?.name || '—'}
                </td>

                <td className="px-3 py-2">
                  {n.channels.map(c => (
                    <div key={c.channelId}>
                      CH{c.channelNo}: {
                        sensorTypes.find(
                          t => t.typeId === c.typeId
                        )?.name || c.typeCode
                      }
                    </div>
                  ))}

                  {n.channels.length === 0 && '—'}
                </td>

                <td className="px-3 py-2">
                  {n.firmwareVer || '—'}
                </td>

                <td className="px-3 py-2">
                  {nodeStatusBadge(n.status)}
                </td>

                <td className="px-3 py-2">
                  <button
                    className="text-brand"
                    onClick={() => openEdit(n.nodeId)}
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
        title={editingId ? 'Edit node' : 'Register node'}
        onClose={() => {
          if (!saving) setOpen(false)
        }}
      >
        <form className="space-y-3" onSubmit={onSubmit}>
          <ErrorBox message={formError} />

          <Input
            label="Device UID"
            value={form.deviceUid || ''}
            maxLength={50}
            disabled={!!editingId}
            onChange={e =>
              setForm({
                ...form,
                deviceUid: e.target.value,
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
            onChange={e => {
              setFormFarmId(e.target.value)
              setForm({
                ...form,
                zoneId: '',
              })
            }}
            required
          >
            <option value="">Select farm</option>

            {farms.map(f => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </Select>

          <Select
            label="Zone"
            value={form.zoneId || ''}
            disabled={!formFarmId}
            onChange={e =>
              setForm({
                ...form,
                zoneId: e.target.value,
              })
            }
            required
          >
            <option value="">Select zone</option>

            {formZones.map(z => (
              <option key={z.id} value={z.id}>
                {z.name}
              </option>
            ))}
          </Select>

          <div className="grid grid-cols-2 gap-2">
            <Input
              label="Latitude"
              type="number"
              step="any"
              min={-90}
              max={90}
              value={form.lat ?? ''}
              onChange={e =>
                setForm({
                  ...form,
                  lat: e.target.value === ''
                    ? undefined
                    : Number(e.target.value),
                })
              }
            />

            <Input
              label="Longitude"
              type="number"
              step="any"
              min={-180}
              max={180}
              value={form.lng ?? ''}
              onChange={e =>
                setForm({
                  ...form,
                  lng: e.target.value === ''
                    ? undefined
                    : Number(e.target.value),
                })
              }
            />
          </div>

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

          {editingId && (
            <Select
              label="Status"
              value={form.status || 'ACTIVE'}
              onChange={e =>
                setForm({
                  ...form,
                  status: e.target.value as SensorNodeDto['status'],
                })
              }
            >
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
              <option value="MAINTENANCE">Maintenance</option>
              <option value="LOST">Lost</option>
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

              <button
                type="button"
                className="mt-1 text-sm text-brand"
                onClick={() =>
                  setForm({
                    ...form,
                    authKey: generateAuthKey(),
                  })
                }
              >
                Generate new key
              </button>
            </div>
          )}

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-medium">
                Sensor channels
              </span>

              {!editingId && (
                <Button
                  variant="outline"
                  onClick={addChannel}
                >
                  Add channel
                </Button>
              )}
            </div>

            {(form.channels ?? []).map((channel, index) => (
              <div
                key={index}
                className="space-y-2 rounded-xl border border-line p-3"
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">
                    Channel {channel.channelNo}
                  </span>

                  {!editingId && (
                    <button
                      type="button"
                      className="text-sm text-red-600"
                      onClick={() => removeChannel(index)}
                    >
                      Remove
                    </button>
                  )}
                </div>

                <Select
                  label="Sensor type"
                  value={channel.typeId}
                  disabled={!!editingId}
                  onChange={e =>
                    updateChannel(index, {
                      typeId: e.target.value,
                    })
                  }
                  required
                >
                  <option value="">Select sensor type</option>

                  {sensorTypes.map(type => (
                    <option
                      key={type.typeId}
                      value={type.typeId}
                    >
                      {type.name} ({type.unit})
                    </option>
                  ))}
                </Select>

                <div className="grid grid-cols-2 gap-2">
                  <Input
                    label="Channel number"
                    type="number"
                    min={0}
                    max={255}
                    value={channel.channelNo}
                    disabled={!!editingId}
                    onChange={e =>
                      updateChannel(index, {
                        channelNo: Number(e.target.value),
                      })
                    }
                    required
                  />

                  <Input
                    label="Calibration offset"
                    type="number"
                    step="any"
                    value={channel.calibrationOffset ?? 0}
                    disabled={!!editingId}
                    onChange={e =>
                      updateChannel(index, {
                        calibrationOffset: Number(e.target.value),
                      })
                    }
                  />
                </div>
              </div>
            ))}
          </div>

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
