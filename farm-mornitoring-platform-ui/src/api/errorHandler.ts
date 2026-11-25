import axios from 'axios'

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) return 'Không kết nối được máy chủ.'
    const d = error.response.data
    if (d?.errors) return Object.values(d.errors).flat().join('\n')
    if (d?.message) return d.message
    if (d?.detail || d?.title) return d.detail || d.title
    switch (error.response.status) {
      case 401: return 'Email hoặc mật khẩu không đúng.'
      case 403: return 'Bạn không có quyền thực hiện thao tác này.'
      case 429: return 'Bạn thao tác quá nhanh, vui lòng thử lại sau.'
    }
  }
  return error instanceof Error ? error.message : 'Đã xảy ra lỗi.'
}