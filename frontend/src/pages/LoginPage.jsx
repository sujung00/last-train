import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/axios'

/**
 * 6b 리디자인: 입력창을 밑줄형으로, 위계 단순화
 * 로직은 기존 LoginPage.jsx와 동일. 마크업만 교체.
 */
export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const handleLogin = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const response = await api.post('/api/v1/auth/login', { email, password })
      const { accessToken, refreshToken } = response.data.data
      localStorage.setItem('accessToken', accessToken)
      localStorage.setItem('refreshToken', refreshToken)
      localStorage.setItem('userEmail', email)
      localStorage.setItem('userProvider', 'EMAIL')
      window.dispatchEvent(new Event('authChange'))
      navigate('/')
    } catch (err) {
      if (err.response?.status === 401) setError('이메일 또는 비밀번호가 올바르지 않아요')
      else setError('로그인에 실패했어요. 잠시 후 다시 시도해주세요')
    } finally {
      setLoading(false)
    }
  }

  const handleKakaoLogin = () => {
    const clientId = import.meta.env.VITE_KAKAO_CLIENT_ID
    const redirectUri = import.meta.env.VITE_KAKAO_REDIRECT_URI
    window.location.href = `https://kauth.kakao.com/oauth/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=code`
  }

  return (
    <div className="h-full bg-white flex items-start justify-center px-4 relative">
      <button onClick={() => navigate('/')} className="absolute top-5 left-4 text-gray-500 hover:text-gray-900 transition text-sm">← 홈으로</button>

      <div className="w-full max-w-md pt-[92px]">
        <div className="mb-7">
          <h1 className="text-2xl font-bold text-gray-900 mb-1.5">막차알리미</h1>
          <p className="text-gray-500 text-[13px]">마지막 한 대를 놓치지 마세요</p>
        </div>

        <form onSubmit={handleLogin} className="flex flex-col gap-5 mb-5">
          {error && <div className="bg-red-50 border border-red-200 text-red-900 px-4 py-3 rounded text-sm">{error}</div>}

          <div>
            <label className="block text-gray-500 text-xs mb-2">이메일</label>
            <input
              type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="example@email.com"
              className="w-full pb-2.5 border-0 border-b border-gray-300 focus:outline-none focus:border-blue-500 transition text-sm bg-transparent"
              required disabled={loading}
            />
          </div>
          <div>
            <label className="block text-gray-500 text-xs mb-2">비밀번호</label>
            <input
              type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호를 입력하세요"
              className="w-full pb-2.5 border-0 border-b border-gray-300 focus:outline-none focus:border-blue-500 transition text-sm bg-transparent"
              required disabled={loading}
            />
          </div>

          <button type="submit" disabled={loading} className="w-full bg-gray-900 hover:bg-black text-white font-semibold py-3.5 rounded-lg transition disabled:bg-gray-400 text-[15px]">
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <div className="flex items-center gap-2.5 mb-5">
          <div className="flex-1 border-t border-gray-100"></div>
          <span className="text-gray-400 text-xs">또는</span>
          <div className="flex-1 border-t border-gray-100"></div>
        </div>

        <button onClick={handleKakaoLogin} disabled={loading} className="w-full bg-[#FEE500] hover:bg-yellow-300 text-black font-semibold py-3.5 rounded-lg transition disabled:bg-gray-400 text-[15px] mb-5">
          카카오로 로그인
        </button>

        <div className="text-center text-[13px]">
          <span className="text-gray-500">계정이 없으신가요? </span>
          <Link to="/signup" className="text-blue-600 font-semibold hover:text-blue-700 transition">회원가입</Link>
        </div>
      </div>
    </div>
  )
}
