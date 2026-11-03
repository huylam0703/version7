export type UserRole = 'ADMIN' | 'OPERATOR'

export interface User {
  userId: string
  fullName: string
  email: string
  phone: string | null
  role: UserRole
  status: string
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  result: T
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken: string
  refreshTokenExpiresAt: string
  user: User
}