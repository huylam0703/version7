import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { Eye, EyeOff } from 'lucide-react'
import { homePathByRole, useAuth } from '../AuthContext'
import type { UserRole } from '../types'
import { Button, ErrorBox, Input } from '../components/Ui'

export function LoginPage() {
  const { login, token, user, loading } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('admin@farm.local')
  const [password, setPassword] = useState('demo')
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(true)
  const [role, setRole] = useState<UserRole>('FarmAdministrator')
  const [error, setError] = useState('')

  if (token && user) {
    return <Navigate to={homePathByRole(user.role)} replace />
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!email.trim() || !password.trim()) {
      setError('Please enter email and password.')
      return
    }
    try {
      await login(email.trim(), password, remember, role)
      navigate(homePathByRole(role))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    }
  }

  return (
    <div className="grid min-h-screen bg-canvas lg:grid-cols-2">
      <section className="hidden flex-col justify-between bg-stone-950 p-10 text-stone-100 lg:flex">
        <div>
          <p className="text-sm font-semibold tracking-[0.18em] text-brand uppercase">Fieldline</p>
          <h1 className="mt-8 max-w-md text-4xl font-semibold leading-tight text-white">
            Farm monitoring for UAV-assisted sensor collection.
          </h1>
          <p className="mt-4 max-w-md text-sm leading-6 text-stone-400">
            Track farms, zones, sensor nodes, collection missions, and alerts. This console monitors missions. It does not fly the aircraft.
          </p>
        </div>
        <p className="max-w-md text-sm text-stone-500">Demo data is already loaded. Sign in to review farms, sensors, alerts, and collection missions.</p>
      </section>
      <section className="flex items-center justify-center p-4">
        <form onSubmit={onSubmit} className="w-full max-w-md space-y-4 rounded-3xl border border-line bg-surface p-8 shadow-sm">
          <div>
            <p className="text-sm font-semibold text-brand">Fieldline</p>
            <h2 className="mt-2 text-2xl font-semibold text-ink">Sign in</h2>
            <p className="text-sm text-muted">Any email and password opens the demo. Pick a role to preview that workspace.</p>
          </div>
          <ErrorBox message={error} />
          <div className="grid grid-cols-2 gap-2 rounded-2xl bg-canvas p-1 text-sm">
            <button
              type="button"
              onClick={() => setRole('FarmAdministrator')}
              className={`rounded-xl px-3 py-2 ${role === 'FarmAdministrator' ? 'bg-surface font-medium text-ink shadow-sm' : 'text-stone-500'}`}
            >
              Farm admin
            </button>
            <button
              type="button"
              onClick={() => setRole('UavOperator')}
              className={`rounded-xl px-3 py-2 ${role === 'UavOperator' ? 'bg-surface font-medium text-ink shadow-sm' : 'text-stone-500'}`}
            >
              UAV operator
            </button>
          </div>
          <Input label="Email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          <div className="relative">
            <Input
              label="Password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
            <button
              type="button"
              className="absolute top-8 right-3 text-stone-500"
              onClick={() => setShowPassword((value) => !value)}
            >
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          <label className="flex items-center gap-2 text-sm text-stone-600">
            <input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} />
            Remember me
          </label>
          <Button type="submit" disabled={loading}>
            {loading ? 'Signing in...' : 'Login'}
          </Button>
        </form>
      </section>
    </div>
  )
}
