import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/axios'

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
      // 401: 이메일/비밀번호 불일치
      if (err.response?.status === 401) {
        setError('이메일 또는 비밀번호가 올바르지 않아요')
      } else {
        // 그 외 에러
        setError('로그인에 실패했어요. 잠시 후 다시 시도해주세요')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleKakaoLogin = () => {
    const clientId = import.meta.env.VITE_KAKAO_CLIENT_ID
    const redirectUri = import.meta.env.VITE_KAKAO_REDIRECT_URI
    const kakaoAuthUrl = `https://kauth.kakao.com/oauth/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=code`
    window.location.href = kakaoAuthUrl
  }

  return (
    <div className="h-full bg-white flex items-center justify-center px-4 relative">
      {/* 뒤로가기 버튼 */}
      <button
        onClick={() => navigate(-1)}
        className="absolute top-4 left-4 text-gray-600 hover:text-gray-900 transition text-sm"
      >
        ← 뒤로
      </button>

      <div className="w-full max-w-md">
        {/* 타이틀 */}
        <div className="text-center mb-12">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">막차알리미</h1>
          <p className="text-gray-600">마지막 한 대를 놓치지 마세요</p>
        </div>

        {/* 로그인 폼 */}
        <form onSubmit={handleLogin} className="space-y-4">
          {/* 에러 메시지 */}
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-900 px-4 py-3 rounded">
              {error}
            </div>
          )}

          {/* 이메일 입력 */}
          <div>
            <label className="block text-gray-900 text-sm font-medium mb-2">이메일</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="example@email.com"
              className="w-full px-4 py-3 bg-gray-50 text-gray-900 border border-gray-200 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              required
              disabled={loading}
            />
          </div>

          {/* 비밀번호 입력 */}
          <div>
            <label className="block text-gray-900 text-sm font-medium mb-2">비밀번호</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호를 입력하세요"
              className="w-full px-4 py-3 bg-gray-50 text-gray-900 border border-gray-200 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
              required
              disabled={loading}
            />
          </div>

          {/* 로그인 버튼 */}
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-gray-900 hover:bg-black text-white font-bold py-3 rounded transition disabled:bg-gray-400 disabled:cursor-not-allowed"
          >
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        {/* 구분선 */}
        <div className="flex items-center my-6">
          <div className="flex-grow border-t border-gray-300"></div>
          <span className="px-3 text-gray-500 text-sm">또는</span>
          <div className="flex-grow border-t border-gray-300"></div>
        </div>

        {/* 카카오로 로그인 버튼 */}
        <button
          onClick={handleKakaoLogin}
          disabled={loading}
          className="w-full bg-[#FEE500] hover:bg-yellow-300 text-black font-bold py-3 rounded transition disabled:bg-gray-400 disabled:cursor-not-allowed"
        >
          카카오로 로그인
        </button>

        {/* 회원가입 링크 */}
        <div className="text-center mt-6">
          <span className="text-gray-600">계정이 없으신가요? </span>
          <Link to="/signup" className="text-gray-900 hover:text-black font-medium transition">
            회원가입
          </Link>
        </div>
      </div>
    </div>
  )
}
