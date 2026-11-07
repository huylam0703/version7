import axios from 'axios'

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data

    if (response?.errors) {
      return Object.values(response.errors)
        .flat()
        .join('\n')
    }

    if (response?.message) {
      return response.message
    }

    return error.message || 'Đã xảy ra lỗi.'
  }

  return error instanceof Error
    ? error.message
    : 'Đã xảy ra lỗi.'
}