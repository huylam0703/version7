export interface ApiError {
  code: string
  message: string
  result?: null
  errors?: Record<string, string[]>
  traceId?: string
}