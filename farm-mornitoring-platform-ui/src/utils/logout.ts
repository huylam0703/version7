
import { authApi } from '../api/authApi'

export const logout = async () => {
  const refreshToken =
    localStorage.getItem('refreshToken') ||
    sessionStorage.getItem('refreshToken')

  try {
    if (refreshToken) {
      await authApi.logout(refreshToken)
    }
  } catch (error) {
    console.error('Không thể thu hồi Refresh Token:', error)
  } finally {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')

    sessionStorage.removeItem('token')
    sessionStorage.removeItem('refreshToken')
    sessionStorage.removeItem('user')

    window.location.href = '/login'
  }
}
