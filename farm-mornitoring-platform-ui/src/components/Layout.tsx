import { NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  Activity,
  Bell,
  ChevronLeft,
  FileBarChart,
  GitCompare,
  History,
  LandPlot,
  LayoutDashboard,
  Map,
  Menu,
  Plane,
  Plus,
  Radio,
  Router,
  Search,
  Settings,
  SlidersHorizontal,
  TriangleAlert,
  Users,
  Waypoints,
  X,
} from 'lucide-react'
import { homePathByRole, useAuth } from '../AuthContext'
import { notificationApi } from '../api'
import type { NotificationItem, UserRole } from '../types'
import { formatDate } from '../helpers'

type NavItem = { to: string; label: string; icon: typeof LayoutDashboard; roles: UserRole[]; group: string }

const NAV: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN'], group: 'Overview' },
  { to: '/farms', label: 'Farm management', icon: LandPlot, roles: ['ADMIN'], group: 'Farm' },
  { to: '/zones', label: 'Zone management', icon: Map, roles: ['ADMIN'], group: 'Farm' },
  { to: '/sensors/monitor', label: 'Sensor monitoring', icon: Activity, roles: ['ADMIN'], group: 'Sensors' },
  { to: '/sensors/history', label: 'Sensor history', icon: History, roles: ['ADMIN'], group: 'Sensors' },
  { to: '/sensors/compare', label: 'Data comparison', icon: GitCompare, roles: ['ADMIN'], group: 'Sensors' },
  { to: '/alerts', label: 'Alert management', icon: TriangleAlert, roles: ['ADMIN'], group: 'Alerts' },
  { to: '/alerts/thresholds', label: 'Alert thresholds', icon: SlidersHorizontal, roles: ['ADMIN'], group: 'Alerts' },
  { to: '/reports', label: 'Reports', icon: FileBarChart, roles: ['ADMIN'], group: 'Admin' },
  { to: '/users', label: 'User management', icon: Users, roles: ['ADMIN'], group: 'Admin' },
  { to: '/operator', label: 'Operator dashboard', icon: LayoutDashboard, roles: ['OPERATOR'], group: 'Overview' },
  { to: '/nodes', label: 'Sensor nodes', icon: Radio, roles: ['OPERATOR'], group: 'Equipment' },
  { to: '/uavs', label: 'UAVs', icon: Plane, roles: ['OPERATOR'], group: 'Equipment' },
  { to: '/gateways', label: 'Mobile gateways', icon: Router, roles: ['OPERATOR'], group: 'Equipment' },
  { to: '/missions', label: 'Missions', icon: Waypoints, roles: ['OPERATOR'], group: 'Missions' },
  { to: '/missions/new', label: 'Create mission', icon: Plus, roles: ['OPERATOR'], group: 'Missions' },
  { to: '/notifications', label: 'Notifications', icon: Bell, roles: ['ADMIN', 'OPERATOR'], group: 'Account' },
  { to: '/settings', label: 'Account settings', icon: Settings, roles: ['ADMIN', 'OPERATOR'], group: 'Account' },
]

function crumbs(path: string) {
  const item = NAV.find((entry) => entry.to === path)
  if (item) return ['Home', item.group, item.label]
  if (path.includes('/results')) return ['Home', 'Missions', 'Mission results']
  if (path.startsWith('/missions/')) return ['Home', 'Missions', 'Mission monitoring']
  return ['Home', 'Fieldline']
}

export function AppLayout() {
  const { user, token, logout } = useAuth()
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [notesOpen, setNotesOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [notes, setNotes] = useState<NotificationItem[]>([])
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    notificationApi.list().then(setNotes).catch(() => setNotes([]))
  }, [location.pathname, notesOpen])

  const items = useMemo(
    () => (user ? NAV.filter((item) => item.roles.includes(user.role)) : []),
    [user],
  )
  const matches = query.trim()
    ? items.filter((item) => item.label.toLowerCase().includes(query.trim().toLowerCase()))
    : []
  const unread = notes.filter((note) => !note.isRead).length
  const trail = crumbs(location.pathname)

  if (!token || !user) {
    return <Navigate to="/login" replace />
  }

  const sidebar = (compact: boolean) => (
    <div className="flex h-full flex-col bg-stone-950 text-stone-200">
      <div className="flex h-16 items-center justify-between px-3">
        {!compact ? (
          <div>
            <p className="text-sm font-semibold tracking-wide text-white">Fieldline</p>
            <p className="text-[11px] text-stone-400">Farm collection platform</p>
          </div>
        ) : (
          <span className="mx-auto text-sm font-semibold text-brand">F</span>
        )}
        <button
          onClick={() => (window.innerWidth < 768 ? setMobileOpen(false) : setCollapsed((value) => !value))}
          className="rounded-lg p-1.5 text-stone-400 hover:bg-white/10 hover:text-white"
        >
          {window.innerWidth < 768 ? <X className="h-4 w-4" /> : <ChevronLeft className={`h-4 w-4 ${compact ? 'rotate-180' : ''}`} />}
        </button>
      </div>
      <nav className="flex-1 space-y-4 overflow-y-auto px-2 pb-4">
        {[...new Set(items.map((item) => item.group))].map((group) => (
          <div key={group}>
            {!compact ? <p className="px-2 pb-1 text-[11px] font-medium uppercase tracking-wider text-stone-500">{group}</p> : null}
            <div className="space-y-0.5">
              {items
                .filter((item) => item.group === group)
                .map((item) => {
                  const Icon = item.icon
                  return (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      onClick={() => setMobileOpen(false)}
                      title={item.label}
                      className={({ isActive }) =>
                        `flex items-center gap-2.5 rounded-xl px-2.5 py-2 text-sm ${
                          isActive ? 'bg-brand text-white' : 'text-stone-300 hover:bg-white/10 hover:text-white'
                        }`
                      }
                    >
                      <Icon className="h-4 w-4 shrink-0" />
                      {!compact ? item.label : null}
                    </NavLink>
                  )
                })}
            </div>
          </div>
        ))}
      </nav>
    </div>
  )

  return (
    <div className="flex min-h-screen bg-canvas text-ink">
      <aside className={`${collapsed ? 'w-[72px]' : 'w-64'} hidden shrink-0 md:block`}>{sidebar(collapsed)}</aside>
      {mobileOpen ? (
        <div className="fixed inset-0 z-30 md:hidden">
          <button className="absolute inset-0 bg-stone-950/50" onClick={() => setMobileOpen(false)} />
          <aside className="relative h-full w-72">{sidebar(false)}</aside>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-line bg-surface/90 px-4 backdrop-blur">
          <button className="rounded-lg p-2 hover:bg-stone-100 md:hidden" onClick={() => setMobileOpen(true)}>
            <Menu className="h-4 w-4" />
          </button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-xs text-stone-400">{trail.join('  /  ')}</p>
            <p className="truncate text-sm font-semibold">{trail[trail.length - 1]}</p>
          </div>
          <div className="relative hidden sm:block">
            <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-stone-400" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && matches[0]) {
                  navigate(matches[0].to)
                  setQuery('')
                }
              }}
              placeholder="Search pages"
              className="w-56 rounded-xl border border-line bg-canvas py-2 pr-3 pl-9 text-sm outline-none focus:border-brand"
            />
            {matches.length > 0 ? (
              <div className="absolute right-0 mt-1 w-56 overflow-hidden rounded-xl border border-line bg-surface py-1 text-sm shadow-lg">
                {matches.map((item) => (
                  <button
                    key={item.to}
                    className="block w-full px-3 py-2 text-left hover:bg-stone-50"
                    onClick={() => {
                      navigate(item.to)
                      setQuery('')
                    }}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <div className="relative">
            <button onClick={() => { setNotesOpen((value) => !value); setMenuOpen(false) }} className="relative rounded-xl p-2 hover:bg-stone-100">
              <Bell className="h-4 w-4" />
              {unread > 0 ? (
                <span className="absolute top-1 right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-brand px-1 text-[10px] text-white">
                  {unread}
                </span>
              ) : null}
            </button>
            {notesOpen ? (
              <div className="absolute right-0 mt-2 w-80 rounded-2xl border border-line bg-surface p-2 shadow-xl">
                <div className="flex items-center justify-between px-2 py-1">
                  <p className="text-sm font-semibold">Notifications</p>
                  <button className="text-xs text-brand" onClick={() => navigate('/notifications')}>View all</button>
                </div>
                {notes.slice(0, 4).map((note) => (
                  <button
                    key={note.id}
                    className="block w-full rounded-xl px-2 py-2 text-left hover:bg-stone-50"
                    onClick={() => {
                      notificationApi.markRead(note.id)
                      setNotesOpen(false)
                      navigate('/notifications')
                    }}
                  >
                    <p className={`text-sm ${note.isRead ? 'text-stone-500' : 'font-medium'}`}>{note.title}</p>
                    <p className="text-xs text-stone-400">{formatDate(note.createdAt)}</p>
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <div className="relative">
            <button
              onClick={() => { setMenuOpen((value) => !value); setNotesOpen(false) }}
              className="flex items-center gap-2 rounded-xl px-1.5 py-1 hover:bg-stone-100"
            >
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-stone-900 text-xs font-semibold text-white">
                {user.fullName?.slice(0, 1) || 'U'}
              </div>
              <div className="hidden text-left lg:block">
                <p className="text-sm font-medium">{user.fullName}</p>
                <p className="text-xs text-stone-500">{user.role === 'OPERATOR' ? 'UAV Operator' : 'Farm Administrator'}</p>
              </div>
            </button>
            {menuOpen ? (
              <div className="absolute right-0 mt-2 w-48 overflow-hidden rounded-xl border border-line bg-surface py-1 text-sm shadow-lg">
                <button className="block w-full px-3 py-2 text-left hover:bg-stone-50" onClick={() => navigate('/settings')}>
                  Account settings
                </button>
                
                <button
                  className="block w-full px-3 py-2 text-left text-red-600 hover:bg-red-50"
                  onClick={async () => {
                    setMenuOpen(false)
                    await logout()
                    navigate('/login', { replace: true })
                  }}
                >
                  Log out
                </button>

              </div>
            ) : null}
          </div>
        </header>
        <main className="flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export function RoleRoute({ roles, children }: { roles: UserRole[]; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (!roles.includes(user.role)) {
    return <Navigate to={homePathByRole(user.role)} replace />
  }
  return children
}
