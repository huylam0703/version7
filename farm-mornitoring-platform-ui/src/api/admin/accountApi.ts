import axiosClient  from '../axiosClient'
import type { User } from '../../types'

export const accountApi = {
  getMe: async (): Promise<User> => {
    const { data } = await axiosClient.get('/v1/users/me')
    return data.result
  },

  update: async (
    userId: string,
    payload: {
      fullName: string
      phone?: string
      password?: string
    }
  ) => {
    const { data } = await axiosClient.put(
      `/v1/users/${userId}`,
      payload
    )

    return data
  }
}