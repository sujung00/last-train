import axios from 'axios'

const api = axios.create({
  baseURL: '/',
})

// 요청마다 Authorization 헤더에 AT 자동 추가
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 응답 인터셉터: 401 토큰 만료 처리
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // 401 Unauthorized: 토큰 만료 또는 토큰 없음
    if (error.response?.status === 401) {
      console.warn('[API 401 Unauthorized] 토큰이 만료되었습니다. 다시 로그인해주세요.')

      // 로컬스토리지에서 토큰 제거
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')

      // /login으로 이동 (BottomTabBar 자동 갱신)
      window.location.href = '/login'
    }

    // 다른 에러는 그대로 전달
    return Promise.reject(error)
  }
)

export default api
