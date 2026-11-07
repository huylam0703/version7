
import axiosClient from './axiosClient'
import type {
  ApiResponse,
  LoginRequest,
  LoginResponse,
} from '../types/auth'

export const authApi = {
  login: async (data: LoginRequest): Promise<LoginResponse> => {
    const response = await axiosClient.post<ApiResponse<LoginResponse>>(
      '/v1/auth/login',
      data
    )

    return response.data.result
  },

  refresh: async (refreshToken: string) => {
    const response = await axiosClient.post(
      '/v1/auth/refresh',
      { refreshToken }
    )

    return response.data.result
  },

  logout: async (refreshToken: string) => {
    return axiosClient.post('/v1/auth/logout', {
      refreshToken,
    })
  },
}
