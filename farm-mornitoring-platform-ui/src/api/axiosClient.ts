import axios from 'axios'
import type {
  AxiosError,
  InternalAxiosRequestConfig,
} from 'axios'

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Axios riêng để refresh, tránh interceptor lặp vô hạn
const refreshClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

type RetryRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean
}

const getToken = (key: string) =>
  localStorage.getItem(key) || sessionStorage.getItem(key)

const saveToken = (key: string, value: string) => {
  const storage = localStorage.getItem(key) !== null
    ? localStorage
    : sessionStorage.getItem(key) !== null
      ? sessionStorage
      : localStorage

  storage.setItem(key, value)
}

const clearSession = () => {
  ;['token', 'refreshToken', 'user'].forEach((key) => {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  })

  window.dispatchEvent(new CustomEvent('session-expired'))
}

let isRefreshing = false

let failedQueue: {
  resolve: (token: string) => void
  reject: (error: unknown) => void
}[] = []

const processQueue = (error: unknown, token?: string) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error)
    } else {
      promise.resolve(token!)
    }
  })

  failedQueue = []
}

// Gắn Access Token vào request
axiosClient.interceptors.request.use((config) => {
  const token = getToken('token')

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

// Tự động refresh khi API trả về 401
axiosClient.interceptors.response.use(
  (response) => response,

  async (error: AxiosError) => {
    const originalRequest = error.config as
      | RetryRequestConfig
      | undefined

    if (
      error.response?.status !== 401 ||
      !originalRequest ||
      originalRequest._retry ||
      originalRequest.url?.includes('/v1/auth/login') ||
      originalRequest.url?.includes('/v1/auth/refresh') ||
      originalRequest.url?.includes('/v1/auth/logout')
    ) {
      return Promise.reject(error)
    }

    // Nếu đang refresh, đưa request vào hàng đợi
    if (isRefreshing) {
      originalRequest._retry = true

      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then((newToken) => {
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return axiosClient(originalRequest)
      })
    }

    originalRequest._retry = true
    isRefreshing = true

    try {
      const refreshToken = getToken('refreshToken')

      if (!refreshToken) {
        throw new Error('Không tìm thấy Refresh Token')
      }

      const response = await refreshClient.post(
        '/v1/auth/refresh',
        { refreshToken }
      )

      const result = response.data?.result

      // Đúng tên thuộc tính theo RefreshResponse Backend
      const newAccessToken = result?.accessToken
      const newRefreshToken = result?.refreshToken

      if (!newAccessToken || !newRefreshToken) {
        throw new Error('Dữ liệu refresh token không hợp lệ')
      }

      // Lưu cả hai token mới vào đúng storage
      saveToken('token', newAccessToken)
      saveToken('refreshToken', newRefreshToken)

      // Xử lý các request đang chờ
      processQueue(null, newAccessToken)

      // Gửi lại request ban đầu
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`

      return axiosClient(originalRequest)

    } catch (refreshError) {
      processQueue(refreshError)
      clearSession()

      return Promise.reject(refreshError)

    } finally {
      isRefreshing = false
    }
  }
)

export default axiosClient