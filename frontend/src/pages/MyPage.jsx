import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios' // 계정 삭제 API 호출용

/**
 * MyPage: 마이페이지
 *
 * 기능:
 * - 비로그인 시 /login으로 자동 redirect
 * - 로그인 시 사용자 이메일 표시
 * - 로그아웃 버튼: localStorage accessToken 삭제 후 /login으로 이동
 * - 계정 삭제: DELETE /api/v1/auth/withdraw 호출
 */
export default function MyPage() {
  const navigate = useNavigate()
  const [email] = useState(() => localStorage.getItem('userEmail') || '')
  const [error, setError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const [withdrawing, setWithdrawing] = useState(false)
  const [showWithdrawConfirm, setShowWithdrawConfirm] = useState(false)
  const [provider] = useState(() => localStorage.getItem('userProvider') || '')

  useEffect(() => {
    // 비로그인 상태 체크
    const token = localStorage.getItem('accessToken')
    if (!token) {
      navigate('/login', { replace: true })
      return
    }
  }, [navigate])

  const handleLogout = async () => {
    setLoggingOut(true)
    try {
      // localStorage 삭제
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userEmail')
      localStorage.removeItem('userProvider')

      window.dispatchEvent(new Event('authChange'))
      // 로그아웃 후 /login으로 이동
      navigate('/login', { replace: true })
    } catch {
      setError('로그아웃 중 오류가 발생했습니다')
      setLoggingOut(false)
    }
  }

  const handleWithdraw = async () => {
    setWithdrawing(true)
    try {
      // DELETE /api/v1/auth/withdraw 호출
      await api.delete('/api/v1/auth/withdraw')

      // localStorage 삭제
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userEmail')
      localStorage.removeItem('userProvider')

      window.dispatchEvent(new Event('authChange'))
      // 계정 삭제 후 /login으로 이동
      navigate('/login', { replace: true })
    } catch (err) {
      setError(err.response?.data?.message || '계정 삭제 중 오류가 발생했습니다')
      setShowWithdrawConfirm(false)
      setWithdrawing(false)
    }
  }

  const handleWithdrawClick = () => {
    setShowWithdrawConfirm(true)
  }

  const handleWithdrawCancel = () => {
    setShowWithdrawConfirm(false)
    setError('')
  }

  return (
    <div className="h-full bg-[#1a1a2e] flex flex-col">
      {/* 헤더 */}
      <header className="bg-[#6366f1] rounded-b-2xl px-4 py-8">
        <h1 className="text-2xl font-bold text-white">마이페이지</h1>
        <p className="text-[#e0e7ff] text-sm mt-1">계정 설정 및 관리</p>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="flex-1 px-4 py-6 overflow-y-auto">
        {/* 에러 메시지 */}
        {error && (
          <div className="mb-6 text-sm px-4 py-3 rounded bg-red-900 bg-opacity-50 border border-red-600 text-red-200">
            {error}
          </div>
        )}

        {/* 사용자 정보 섹션 */}
        <div className="mb-8 bg-gray-800 rounded-lg p-6 border border-gray-700">
          <div className="text-gray-400 text-xs font-medium mb-3 uppercase tracking-wider">
            로그인된 계정
          </div>
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 bg-[#6366f1] rounded-full flex items-center justify-center text-white text-lg font-bold">
              {email.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-white font-semibold text-sm">{email}</div>
              <div className="text-gray-400 text-xs mt-1">
                {provider === 'KAKAO' ? '카카오 계정' : '메일 계정'}
              </div>
            </div>
          </div>
        </div>

        {/* 설정 섹션 */}
        <div className="mb-8">
          <label className="block text-white text-sm font-medium mb-3">설정</label>
          <div className="space-y-2">
            <button
              onClick={() => navigate('/notifications')}
              className="w-full text-left px-4 py-3 bg-gray-800 hover:bg-gray-700 rounded border border-gray-700 transition text-gray-300 text-sm"
            >
              📱 알림 설정
            </button>
            {provider === 'EMAIL' && (
              <button className="w-full text-left px-4 py-3 bg-gray-800 hover:bg-gray-700 rounded border border-gray-700 transition text-gray-300 text-sm">
                🔒 비밀번호 변경
              </button>
            )}
          </div>
        </div>

        {/* 로그아웃 버튼 */}
        <button
          onClick={handleLogout}
          disabled={loggingOut}
          className="w-full px-6 py-3 bg-red-600 hover:bg-red-700 text-white rounded-lg font-medium transition disabled:bg-gray-600 disabled:cursor-not-allowed"
        >
          {loggingOut ? '로그아웃 중...' : '로그아웃'}
        </button>

        {/* 계정 삭제 버튼 */}
        <div className="mt-8 text-center">
          <button
            onClick={handleWithdrawClick}
            className="text-gray-400 hover:text-red-400 text-xs transition"
          >
            계정 삭제
          </button>
        </div>
      </main>

      {/* 계정 삭제 확인 모달 */}
      {showWithdrawConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 px-4">
          <div className="bg-gray-800 rounded-lg p-6 max-w-sm w-full border border-gray-700">
            <h2 className="text-lg font-bold text-white mb-2">계정 삭제</h2>
            <p className="text-gray-300 text-sm mb-6">
              정말 탈퇴하시겠어요? 모든 데이터가 삭제됩니다.
            </p>

            {error && (
              <div className="mb-4 text-sm px-3 py-2 bg-red-900 bg-opacity-50 border border-red-600 text-red-200 rounded">
                {error}
              </div>
            )}

            <div className="flex gap-3">
              <button
                onClick={handleWithdrawCancel}
                disabled={withdrawing}
                className="flex-1 px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white text-sm rounded font-medium transition disabled:bg-gray-600 disabled:cursor-not-allowed"
              >
                취소
              </button>
              <button
                onClick={handleWithdraw}
                disabled={withdrawing}
                className="flex-1 px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm rounded font-medium transition disabled:bg-gray-600 disabled:cursor-not-allowed"
              >
                {withdrawing ? '삭제 중...' : '탈퇴'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
