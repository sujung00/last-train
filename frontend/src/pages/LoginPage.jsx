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
      const { accessToken, refreshToken } = response.data

      localStorage.setItem('accessToken', accessToken)
      localStorage.setItem('refreshToken', refreshToken)

      navigate('/')
    } catch (err) {
      setError(err.response?.data?.message || '로그인 실패. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleKakaoLogin = () => {
    const clientId = import.meta.env.VITE_KAKAO_CLIENT_ID
    const redirectUri = 'http://localhost:3000/auth/kakao/callback'
    const kakaoAuthUrl = `https://kauth.kakao.com/oauth/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=code`
    window.location.href = kakaoAuthUrl
  }

  return (
    <div className="h-full bg-[#1a1a2e] flex items-center justify-center px-4 relative">
      {/* 뒤로가기 버튼 */}
      <button
        onClick={() => navigate(-1)}
        className="absolute top-4 left-4 text-gray-300 hover:text-white transition text-sm"
      >
        ← 뒤로
      </button>

      <div className="w-full max-w-md">
        {/* 타이틀 */}
        <div className="text-center mb-12">
          <h1 className="text-4xl font-bold text-white mb-2">막차알리미 🚂</h1>
          <p className="text-gray-300">오늘도 막차 놓치지 마세요</p>
        </div>

        {/* 로그인 폼 */}
        <form onSubmit={handleLogin} className="space-y-4">
          {/* 에러 메시지 */}
          {error && (
            <div className="bg-red-500 bg-opacity-20 border border-red-500 text-red-200 px-4 py-3 rounded">
              {error}
            </div>
          )}

          {/* 이메일 입력 */}
          <div>
            <label className="block text-white text-sm font-medium mb-2">이메일</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="example@email.com"
              className="w-full px-4 py-3 bg-gray-700 text-white rounded focus:outline-none focus:bg-gray-600 transition"
              required
              disabled={loading}
            />
          </div>

          {/* 비밀번호 입력 */}
          <div>
            <label className="block text-white text-sm font-medium mb-2">비밀번호</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호를 입력하세요"
              className="w-full px-4 py-3 bg-gray-700 text-white rounded focus:outline-none focus:bg-gray-600 transition"
              required
              disabled={loading}
            />
          </div>

          {/* 로그인 버튼 */}
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-purple-600 hover:bg-purple-700 text-white font-bold py-3 rounded transition disabled:bg-gray-600 disabled:cursor-not-allowed"
          >
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        {/* 구분선 */}
        <div className="flex items-center my-6">
          <div className="flex-grow border-t border-gray-600"></div>
          <span className="px-3 text-gray-400 text-sm">또는</span>
          <div className="flex-grow border-t border-gray-600"></div>
        </div>

        {/* 카카오로 로그인 버튼 */}
        <button
          onClick={handleKakaoLogin}
          disabled={loading}
          className="w-full bg-[#FEE500] hover:bg-yellow-300 text-black font-bold py-3 rounded transition disabled:bg-gray-600 disabled:cursor-not-allowed"
        >
          카카오로 로그인
        </button>

        {/* 회원가입 링크 */}
        <div className="text-center mt-6">
          <span className="text-gray-400">계정이 없으신가요? </span>
          <Link to="/signup" className="text-purple-400 hover:text-purple-300 font-medium transition">
            회원가입
          </Link>
        </div>
      </div>
    </div>
  )
}
