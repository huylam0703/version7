import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import type { User, UserRole } from './types'

type AuthContextValue = {
  user: User | null
  token: string | null
  loading: boolean
  login: (email: string, password: string, remember: boolean, role?: UserRole) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readStoredUser() {
  const raw = localStorage.getItem('user') || sessionStorage.getItem('user')
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readStoredUser)
  const [token, setToken] = useState<string | null>(
    localStorage.getItem('token') || sessionStorage.getItem('token'),
  )
  const [loading, setLoading] = useState(false)

  async function login(email: string, password: string, remember: boolean, role: UserRole = 'FarmAdministrator') {
    setLoading(true)

    try {
      void password
      const data = {
        token: 'mock-access-token',
        user: {
          id: '1',
          fullName: role === 'UavOperator' ? 'Minh Tran' : 'System Administrator',
          email,
          role,
          // role: 'UavOperator' as UserRole
        },
      }

      const storage = remember ? localStorage : sessionStorage

      storage.setItem('token', data.token)
      storage.setItem('user', JSON.stringify(data.user))

      const other = remember ? sessionStorage : localStorage
      other.removeItem('token')
      other.removeItem('user')

      setToken(data.token)
      setUser(data.user)
    } finally {
      setLoading(false)
    }
  }

  function logout() {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('user')
    setToken(null)
    setUser(null)
  }

  const value = useMemo(() => ({ user, token, loading, login, logout }), [user, token, loading])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}

export function homePathByRole(role?: UserRole) {
  return role === 'UavOperator' ? '/operator' : '/dashboard'
}
