import axiosClient from '../axiosClient'
import type { ApiResponse, PagedResult } from '../../types/'
import type { User, UserRole } from '../../types/auth'

export interface CreateUserRequest {
  fullName: string
  email: string
  password: string
  roleName: UserRole
  phone: string | null
}

export interface UpdateUserRequest {
  fullName: string
  phone: string | null
  roleName: UserRole
}

function unwrap<T>(body: ApiResponse<T>): T {
  if (!body.result) throw new Error(body.message)
  return body.result
}

export const userApi = {
  list: async (params?: {
    searchString?: string
    role?: string
    status?: string
    page?: number
    pageSize?: number
  }): Promise<PagedResult<User>> => {
    const { data } = await axiosClient.get<ApiResponse<PagedResult<User>>>('/v1/users', { params })
    return unwrap(data)
  },

  getById: async (id: string): Promise<User> => {
    const { data } = await axiosClient.get<ApiResponse<User>>(`/v1/users/${id}`)
    return unwrap(data)
  },

  create: async (user: CreateUserRequest): Promise<User> => {
    const { data } = await axiosClient.post<ApiResponse<User>>('/v1/users', user)
    return unwrap(data)
  },

  update: async (id: string, payload: UpdateUserRequest): Promise<User> => {
    const { data } = await axiosClient.put<ApiResponse<User>>(`/v1/users/${id}`, payload)
    return unwrap(data)
  },

  lock: async (id: string): Promise<User> => {
    const { data } = await axiosClient.post<ApiResponse<User>>(`/v1/users/${id}/lock`)
    return unwrap(data)
  },

  unlock: async (id: string): Promise<User> => {
    const { data } = await axiosClient.post<ApiResponse<User>>(`/v1/users/${id}/unlock`)
    return unwrap(data)
  },
}