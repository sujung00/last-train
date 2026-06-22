import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080',
})

// 요청마다 Authorization 헤더에 AT 자동 추가
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default api
