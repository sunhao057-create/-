import axios, { type AxiosError } from 'axios'
import { axiosErrorText } from '../utils/format'

export type ApiBody<T> = {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

export const http = axios.create({
  baseURL: '/',
  withCredentials: true,
  timeout: 15000,
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiBody<unknown>
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    return response
  },
  async (error: AxiosError<ApiBody<unknown> | Blob>) => {
    const status = error.response?.status
    const data = error.response?.data
    let message = axiosErrorText(error.message)
    if (typeof Blob !== 'undefined' && data instanceof Blob) {
      try {
        const parsed = JSON.parse(await data.text()) as ApiBody<unknown>
        if (parsed?.message) message = parsed.message
      } catch {
        /* keep axios message */
      }
    } else if (data && typeof data === 'object' && 'message' in data && typeof data.message === 'string') {
      message = data.message
    }
    const code =
      data && typeof data === 'object' && !(data instanceof Blob) && typeof data.code === 'number'
        ? data.code
        : status || 500
    if (status === 401) {
      const path = window.location.pathname
      if (!path.startsWith('/login') && !path.startsWith('/m/login') && !path.startsWith('/public')) {
        window.location.assign(path.startsWith('/m') ? '/m/login' : '/login')
      }
    }
    return Promise.reject(new ApiError(code, message))
  },
)

export async function unwrap<T>(promise: Promise<{ data: ApiBody<T> }>): Promise<T> {
  const res = await promise
  return res.data.data
}
