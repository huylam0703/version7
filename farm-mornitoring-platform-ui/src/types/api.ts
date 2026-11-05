export interface ApiResponse<T> {
  code: string
  message: string
  result: T | null
  errors?: Record<string, string[]>
  traceId?: string
}

export interface PagedResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}