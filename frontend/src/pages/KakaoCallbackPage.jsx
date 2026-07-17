import { useEffect, useRef } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import api from '../api/axios'

export default function KakaoCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const processedRef = useRef(false)

  useEffect(() => {
    if (processedRef.current) return
    processedRef.current = true

    const handleKakaoCallback = async () => {
      const code = searchParams.get('code')

      if (!code) {
        navigate('/login')
        return
      }

      try {
        const response = await api.get(`/api/v1/auth/kakao/callback?code=${code}`)
        const { accessToken, refreshToken, email } = response.data.data

        localStorage.setItem('accessToken', accessToken)
        localStorage.setItem('refreshToken', refreshToken)
        localStorage.setItem('userProvider', 'KAKAO')

        // email이 있으면 저장 (없으면 저장 안 함)
        if (email) {
          localStorage.setItem('userEmail', email)
        }

        window.dispatchEvent(new Event('authChange'))
        navigate('/')
      } catch (error) {
        console.error('카카오 로그인 콜백 처리 실패:', {
          status: error.response?.status,
          statusText: error.response?.statusText,
          data: error.response?.data,
          message: error.message,
        })
        navigate('/login')
      }
    }

    handleKakaoCallback()
  }, [searchParams, navigate])

  return (
    <div className="flex items-center justify-center min-h-screen bg-[#1a1a2e]">
      <div className="text-center">
        <div className="mb-4">
          <div className="inline-block w-12 h-12 border-4 border-white border-t-transparent rounded-full animate-spin"></div>
        </div>
        <p className="text-white text-lg">카카오 로그인 처리 중...</p>
      </div>
    </div>
  )
}
