import axiosClient from '../axiosClient'
import type { ApiResponse, PagedResult } from '../../types/'
import type { User } from '../../types'
import type { UserRole } from '../../types/auth'

export interface CreateUserRequest {
  fullName: string
  email: string
  password: string
  roleName: UserRole
  phone: string | null
}

export const userApi = {
  list: async (params?: {
    searchString?: string
    role?: string
    status?: string
    page?: number
    pageSize?: number
  }): Promise<PagedResult<User>> => {
    const { data } = await axiosClient.get<ApiResponse<PagedResult<User>>>(
      '/v1/users',
      { params }
    )

    if (!data.result) {
      throw new Error(data.message)
    }

    return data.result
  },

  create: async (user: CreateUserRequest): Promise<User> => {
    const { data } = await axiosClient.post<ApiResponse<User>>(
      '/v1/users',
      user
    )

    if (!data.result) {
      throw new Error(data.message)
    }

    return data.result
  }
}