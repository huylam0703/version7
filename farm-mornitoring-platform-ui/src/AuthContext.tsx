
import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

import { authApi } from './api/authApi'
import type { User } from './types/auth'

type AuthContextValue = {
  user: User | null
  token: string | null
  loading: boolean
  login: (
    email: string,
    password: string,
    remember: boolean
  ) => Promise<User>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readStoredUser(): User | null {
  const raw =
    localStorage.getItem('user') ||
    sessionStorage.getItem('user')

  if (!raw) return null

  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

function readStoredToken(): string | null {
  return (
    localStorage.getItem('token') ||
    sessionStorage.getItem('token')
  )
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readStoredUser)
  const [token, setToken] = useState<string | null>(readStoredToken)
  const [loading, setLoading] = useState(false)

  async function login(
    email: string,
    password: string,
    remember: boolean
  ): Promise<User> {
    setLoading(true)

    try {
      const response = await authApi.login({
        email,
        password,
      })

      const storage = remember ? localStorage : sessionStorage
      const other = remember ? sessionStorage : localStorage

      storage.setItem('token', response.accessToken)
      storage.setItem('refreshToken', response.refreshToken)
      storage.setItem('user', JSON.stringify(response.user))

      other.removeItem('token')
      other.removeItem('refreshToken')
      other.removeItem('user')

      setToken(response.accessToken)
      setUser(response.user)

      return response.user
    } finally {
      setLoading(false)
    }
  }

  function logout() {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')

    sessionStorage.removeItem('token')
    sessionStorage.removeItem('refreshToken')
    sessionStorage.removeItem('user')

    setToken(null)
    setUser(null)
  }

  const value = useMemo(
    () => ({
      user,
      token,
      loading,
      login,
      logout,
    }),
    [user, token, loading]
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)

  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider')
  }

  return ctx
}

export function homePathByRole(role?: User['role']) {
  return role === 'OPERATOR'
    ? '/operator'
    : '/dashboard'
}
