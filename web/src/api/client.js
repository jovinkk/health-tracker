import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000'

const api = axios.create({ baseURL: BASE_URL })

// Attach token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Redirect to login on 401
api.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default api

// ── Auth ──────────────────────────────────────────────────────────────────────
export const login = (username, password) => {
  const form = new URLSearchParams({ username, password })
  return api.post('/auth/login', form, { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
}
export const register = (username, password) => api.post('/auth/register', { username, password })
export const getMe = () => api.get('/auth/me')

// ── Data ──────────────────────────────────────────────────────────────────────
export const getEntries = (params) => api.get('/entries', { params })
export const getWearable = (params) => api.get('/wearable', { params })
export const getSummary = () => api.get('/analysis/summary')

// ── Analysis ──────────────────────────────────────────────────────────────────
export const getPatterns = (days = 30) => api.get('/analysis/patterns', { params: { days } })
export const getCorrelation = (metric1, metric2, days = 30) =>
  api.get('/analysis/correlations', { params: { metric1, metric2, days } })
export const getCorrelationMatrix = (days = 30) =>
  api.get('/analysis/correlations/matrix', { params: { days } })
