import axios from 'axios'

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Không kết nối được máy chủ.'
    }

    const data = error.response.data
    const status = error.response.status

    if (data?.errors) {
      const messages = Object.values(data.errors)
        .flat()
        .filter(value => typeof value === 'string')

      if (messages.length > 0) {
        return messages.join('\n')
      }
    }

    if (typeof data?.message === 'string') {
      return data.message
    }

    if (typeof data?.detail === 'string') {
      return data.detail
    }

    switch (status) {
      case 400:
        return 'Dữ liệu không hợp lệ.'
      case 401:
        return 'Xác thực thất bại hoặc phiên đăng nhập đã hết hạn.'
      case 403:
        return 'Bạn không có quyền thực hiện thao tác này.'
      case 404:
        return 'Không tìm thấy dữ liệu.'
      case 409:
        return 'Dữ liệu bị trùng hoặc xung đột.'
      case 422:
        return 'Dữ liệu không đáp ứng yêu cầu.'
      case 429:
        return 'Bạn thao tác quá nhanh, vui lòng thử lại sau.'
      case 500:
      case 502:
      case 503:
      case 504:
        return 'Máy chủ đang gặp sự cố.'
    }

    if (typeof data?.title === 'string') {
      return data.title
    }
  }

  return error instanceof Error
    ? error.message
    : 'Đã xảy ra lỗi.'
}