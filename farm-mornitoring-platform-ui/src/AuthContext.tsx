
import {
  createContext,
  useContext,
  useMemo,
  useState,
  useCallback,
  type ReactNode,
} from 'react'

import { authApi } from './api/authApi'
import type { User } from './types/auth'
import { useEffect } from 'react'          // gộp vào import react có sẵn
import { accountApi } from './api/admin/accountApi'

type AuthContextValue = {
  user: User | null
  token: string | null
  loading: boolean
  login: (
    email: string,
    password: string,
    remember: boolean
  ) => Promise<User>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
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

  const login = useCallback(async (
    email: string,
    password: string,
    remember: boolean
  ): Promise<User> => {
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
  }, [])

  const logout = useCallback(async (): Promise<void> => {
    const refreshToken =
      localStorage.getItem('refreshToken') ||
      sessionStorage.getItem('refreshToken')

      try {
        // Thu hồi Refresh Token ở Backend
        if (refreshToken) {
          await authApi.logout(refreshToken)
        }
      } catch (error) {
        console.error('Không thể thu hồi Refresh Token:', error)
      } finally {
        // Luôn xóa dữ liệu đăng nhập ở Frontend
        localStorage.removeItem('token')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('user')

        sessionStorage.removeItem('token')
        sessionStorage.removeItem('refreshToken')
        sessionStorage.removeItem('user')

        setToken(null)
        setUser(null)
      }
    }, [])

  const refreshUser = useCallback(async (): Promise<void> => {
    if (!readStoredToken()) return

    const fresh = await accountApi.getMe()

    // Ghi lại vào đúng storage đang giữ token
    const storage =
      localStorage.getItem('token') !== null ? localStorage : sessionStorage
    storage.setItem('user', JSON.stringify(fresh))

    setUser(fresh)
  }, [])

  // Đồng bộ user (role, trạng thái, tên) khi có token, tức là lúc mở app hoặc vừa đăng nhập
  useEffect(() => {
    if (!token) return
    refreshUser().catch(() => {
      // 401 đã được interceptor xử lý (refresh hoặc đăng xuất)
    })
  }, [token, refreshUser])

  const value = useMemo(
  () => ({ user, token, loading, login, logout, refreshUser }),
  [user, token, loading, login, logout, refreshUser],
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
