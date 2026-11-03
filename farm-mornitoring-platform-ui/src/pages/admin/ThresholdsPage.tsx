import { useEffect, useState, type FormEvent } from 'react'
import { alertApi, farmApi, getErrorMessage } from '../../api'
import type { AlertThreshold, Farm, SensorType } from '../../types'
import { SENSOR_TYPE_LABEL } from '../../types'
import { Button, Card, Empty, ErrorBox, Input, Modal, PageHeader, Select, Table, alertBadge } from '../../components/Ui'

export function ThresholdsPage() {
  const [items, setItems] = useState<AlertThreshold[]>([])
  const [farms, setFarms] = useState<Farm[]>([])
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<Partial<AlertThreshold>>({
    sensorType: 'soilMoisture',
    severity: 'warning',
    minValue: 0,
    maxValue: 100,
    isActive: true,
  })

  async function load() {
    try {
      setError('')
      const [t, f] = await Promise.all([alertApi.thresholds(), farmApi.list()])
      setItems(t)
      setFarms(f)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  useEffect(() => { load() }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    try {
      await alertApi.saveThreshold(form)
      setOpen(false)
      await load()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div>
      <PageHeader
        title="Alert thresholds"
        description="Set min/max values that create alerts. Admin only."
        actions={<Button onClick={() => setOpen(true)}>Add threshold</Button>}
      />
      <ErrorBox message={error} />
      <Card>
        {items.length === 0 ? <Empty text="No thresholds. Check API /api/alert-thresholds" /> : (
          <Table headers={['Sensor type', 'Min', 'Max', 'Severity', 'Active']}>
            {items.map((t) => (
              <tr key={t.id}>
                <td className="px-3 py-2">{SENSOR_TYPE_LABEL[t.sensorType]}</td>
                <td className="px-3 py-2">{t.minValue ?? '—'}</td>
                <td className="px-3 py-2">{t.maxValue ?? '—'}</td>
                <td className="px-3 py-2">{alertBadge(t.severity)}</td>
                <td className="px-3 py-2">{t.isActive === false ? 'No' : 'Yes'}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <Modal open={open} title="New threshold" onClose={() => setOpen(false)}>
        <form className="space-y-3" onSubmit={onSubmit}>
          <Select label="Farm (optional)" value={form.farmId || ''} onChange={(e) => setForm({ ...form, farmId: e.target.value })}>
            <option value="">All farms</option>
            {farms.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </Select>
          <Select label="Sensor type" value={form.sensorType} onChange={(e) => setForm({ ...form, sensorType: e.target.value as SensorType })}>
            {Object.entries(SENSOR_TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </Select>
          <Input label="Min" type="number" value={form.minValue ?? 0} onChange={(e) => setForm({ ...form, minValue: Number(e.target.value) })} />
          <Input label="Max" type="number" value={form.maxValue ?? 0} onChange={(e) => setForm({ ...form, maxValue: Number(e.target.value) })} />
          <Select label="Severity" value={form.severity} onChange={(e) => setForm({ ...form, severity: e.target.value as AlertThreshold['severity'] })}>
            <option value="info">Info</option>
            <option value="warning">Warning</option>
            <option value="critical">Critical</option>
          </Select>
          <Button type="submit">Save</Button>
        </form>
      </Modal>
    </div>
  )
}
