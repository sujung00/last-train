import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/axios'

export default function SignupPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const handleSignup = async (e) => {
    e.preventDefault()
    setError('')

    // 비밀번호 확인 검사
    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않습니다.')
      return
    }

    if (password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.')
      return
    }

    setLoading(true)

    try {
      await api.post('/api/v1/auth/signup', { email, password })
      navigate('/login')
    } catch (err) {
      setError(err.response?.data?.message || '회원가입 실패. 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#1a1a2e] flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* 타이틀 */}
        <div className="text-center mb-12">
          <h1 className="text-4xl font-bold text-white mb-2">막차알리미 🚂</h1>
          <p className="text-xl text-gray-300 font-semibold">회원가입</p>
        </div>

        {/* 회원가입 폼 */}
        <form onSubmit={handleSignup} className="space-y-4">
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
              placeholder="8자 이상의 비밀번호"
              className="w-full px-4 py-3 bg-gray-700 text-white rounded focus:outline-none focus:bg-gray-600 transition"
              required
              disabled={loading}
            />
          </div>

          {/* 비밀번호 확인 입력 */}
          <div>
            <label className="block text-white text-sm font-medium mb-2">비밀번호 확인</label>
            <input
              type="password"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              placeholder="비밀번호를 다시 입력하세요"
              className="w-full px-4 py-3 bg-gray-700 text-white rounded focus:outline-none focus:bg-gray-600 transition"
              required
              disabled={loading}
            />
          </div>

          {/* 회원가입 버튼 */}
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-[#6366f1] hover:bg-[#4338ca] text-white font-bold py-3 rounded transition disabled:bg-gray-600 disabled:cursor-not-allowed"
          >
            {loading ? '회원가입 중...' : '회원가입'}
          </button>
        </form>

        {/* 로그인 링크 */}
        <div className="text-center mt-6">
          <span className="text-gray-400">이미 계정이 있으신가요? </span>
          <Link to="/login" className="text-[#6366f1] hover:text-[#a5b4fc] font-medium transition">
            로그인
          </Link>
        </div>
      </div>
    </div>
  )
}
