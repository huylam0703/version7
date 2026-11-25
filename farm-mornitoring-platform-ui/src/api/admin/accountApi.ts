import axiosClient from '../axiosClient'
import type { User } from '../../types/auth'

export const accountApi = {
  getMe: async (): Promise<User> => {
    const { data } = await axiosClient.get('/v1/users/me')
    return data.result
  },

  // Chỉ ADMIN gọi được. Backend bắt buộc có roleName, nếu thiếu sẽ báo lỗi 400.
  update: async (
    userId: string,
    payload: { fullName: string; phone: string | null; roleName: string },
  ): Promise<User> => {
    const { data } = await axiosClient.put(`/v1/users/${userId}`, payload)
    return data.result
  },

  // Dùng khi backend đã có PUT /v1/users/me (xem 8.3)
  updateMe: async (payload: { fullName: string; phone: string | null }): Promise<User> => {
    const { data } = await axiosClient.put('/v1/users/me', payload)
    return data.result
  },
}