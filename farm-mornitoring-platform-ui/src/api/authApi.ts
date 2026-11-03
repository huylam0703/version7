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
}