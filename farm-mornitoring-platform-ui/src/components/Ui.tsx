import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import type { AlertSeverity, MissionStatus, SensorStatus } from '../types'

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
        {description ? <p className="mt-1 max-w-2xl text-sm text-muted">{description}</p> : null}
      </div>
      {actions}
    </div>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-2xl border border-line bg-surface p-4 shadow-[0_1px_0_rgba(28,25,23,0.03)] ${className}`}>
      {children}
    </div>
  )
}

export function StatCard({
  label,
  value,
  hint,
}: {
  label: string
  value: string | number
  hint?: string
}) {
  return (
    <Card className="relative overflow-hidden">
      <span className="absolute inset-y-3 left-0 w-1 rounded-r bg-brand" />
      <p className="pl-2 text-sm text-muted">{label}</p>
      <p className="mt-1 pl-2 text-2xl font-semibold tracking-tight text-ink">{value}</p>
      {hint ? <p className="mt-1 pl-2 text-xs text-stone-400">{hint}</p> : null}
    </Card>
  )
}

export function Button({
  children,
  onClick,
  type = 'button',
  variant = 'primary',
  disabled,
}: {
  children: ReactNode
  onClick?: () => void
  type?: 'button' | 'submit'
  variant?: 'primary' | 'ghost' | 'danger' | 'outline'
  disabled?: boolean
}) {
  const styles = {
    primary: 'bg-brand text-white hover:bg-brand-dark',
    ghost: 'bg-stone-100 text-stone-800 hover:bg-stone-200',
    danger: 'bg-red-600 text-white hover:bg-red-700',
    outline: 'border border-line bg-surface text-stone-800 hover:bg-stone-50',
  }[variant]
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`rounded-xl px-3.5 py-2 text-sm font-medium disabled:opacity-50 ${styles}`}
    >
      {children}
    </button>
  )
}

const fieldClass =
  'w-full rounded-xl border border-line bg-surface px-3 py-2 text-sm text-ink outline-none transition focus:border-brand focus:ring-2 focus:ring-brand/15'

export function Input({
  label,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label?: string }) {
  return (
    <label className="block text-sm">
      {label ? <span className="mb-1 block font-medium text-stone-700">{label}</span> : null}
      <input {...props} className={fieldClass} />
    </label>
  )
}

export function Select({
  label,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label?: string; children: ReactNode }) {
  return (
    <label className="block text-sm">
      {label ? <span className="mb-1 block font-medium text-stone-700">{label}</span> : null}
      <select {...props} className={fieldClass}>
        {children}
      </select>
    </label>
  )
}

export function Textarea({
  label,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { label?: string }) {
  return (
    <label className="block text-sm">
      {label ? <span className="mb-1 block font-medium text-stone-700">{label}</span> : null}
      <textarea {...props} className={fieldClass} />
    </label>
  )
}

export function ErrorBox({ message }: { message?: string | null }) {
  if (!message) return null
  return <div className="mb-3 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{message}</div>
}

export function Empty({ text }: { text: string }) {
  return <p className="py-8 text-center text-sm text-muted">{text}</p>
}

export function Badge({
  children,
  color,
}: {
  children: ReactNode
  color: 'green' | 'gray' | 'red' | 'orange' | 'yellow' | 'blue'
}) {
  const map = {
    green: 'bg-teal-50 text-teal-800',
    gray: 'bg-stone-100 text-stone-600',
    red: 'bg-red-50 text-red-700',
    orange: 'bg-orange-50 text-orange-800',
    yellow: 'bg-amber-50 text-amber-800',
    blue: 'bg-sky-50 text-sky-800',
  }
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${map[color]}`}>{children}</span>
}

export function sensorStatusBadge(status?: SensorStatus) {
  if (status === 'online') return <Badge color="green">Online</Badge>
  if (status === 'maintenance') return <Badge color="yellow">Maintenance</Badge>
  if (status === 'lost') return <Badge color="red">Lost</Badge>
  return <Badge color="gray">Offline</Badge>
}

export function alertBadge(severity?: AlertSeverity) {
  if (severity === 'critical') return <Badge color="red">Critical</Badge>
  if (severity === 'warning') return <Badge color="orange">Warning</Badge>
  return <Badge color="yellow">Info</Badge>
}

export function missionBadge(status?: MissionStatus) {
  if (status === 'inProgress') return <Badge color="blue">In progress</Badge>
  if (status === 'completed') return <Badge color="green">Completed</Badge>
  if (status === 'failed') return <Badge color="red">Failed</Badge>
  if (status === 'paused') return <Badge color="yellow">Paused</Badge>
  if (status === 'cancelled') return <Badge color="gray">Cancelled</Badge>
  return <Badge color="gray">Planned</Badge>
}

export function Table({ headers, children }: { headers: string[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-left text-sm">
        <thead className="border-b border-line text-xs uppercase tracking-wide text-stone-500">
          <tr>
            {headers.map((header) => (
              <th key={header} className="px-3 py-2.5 font-medium">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line text-stone-800 [&_tr:hover]:bg-stone-50/80">{children}</tbody>
      </table>
    </div>
  )
}

export function Modal({
  open,
  title,
  onClose,
  children,
}: {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
}) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-stone-950/45 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-line bg-surface p-5 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-ink">{title}</h2>
          <button onClick={onClose} className="rounded-lg px-2 py-1 text-stone-500 hover:bg-stone-100 hover:text-ink">
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
