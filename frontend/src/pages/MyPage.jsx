import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'

/**
 * 5b 리디자인: 설정은 구분선 목록으로, 로그아웃은 톤 다운
 * 로직은 기존 MyPage.jsx와 동일. 마크업만 교체.
 */
export default function MyPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const [withdrawing, setWithdrawing] = useState(false)
  const [showWithdrawConfirm, setShowWithdrawConfirm] = useState(false)
  const [provider, setProvider] = useState('')

  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    if (!token) { navigate('/login', { replace: true }); return }
    setProvider(localStorage.getItem('userProvider') || '')
    setEmail(localStorage.getItem('userEmail') || '')
    setLoading(false)
  }, [navigate])

  const handleLogout = async () => {
    setLoggingOut(true)
    try {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userEmail')
      localStorage.removeItem('userProvider')
      window.dispatchEvent(new Event('authChange'))
      navigate('/login', { replace: true })
    } catch (err) {
      setError('로그아웃 중 오류가 발생했습니다')
      setLoggingOut(false)
    }
  }

  const handleWithdraw = async () => {
    setWithdrawing(true)
    try {
      await api.delete('/api/v1/auth/withdraw')
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userEmail')
      localStorage.removeItem('userProvider')
      window.dispatchEvent(new Event('authChange'))
      navigate('/login', { replace: true })
    } catch (err) {
      setError(err.response?.data?.message || '계정 삭제 중 오류가 발생했습니다')
      setShowWithdrawConfirm(false)
      setWithdrawing(false)
    }
  }

  const handleWithdrawClick = () => setShowWithdrawConfirm(true)
  const handleWithdrawCancel = () => { setShowWithdrawConfirm(false); setError('') }

  if (loading) {
    return (
      <div className="h-full bg-white flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="animate-spin rounded-full h-8 w-8 border-2 border-gray-300 border-t-gray-900"></div>
          <span className="text-gray-500">로드 중...</span>
        </div>
      </div>
    )
  }

  return (
    <div className="h-full bg-white flex flex-col">
      <header className="bg-white border-b border-gray-200 px-4 py-6">
        <h1 className="text-xl font-bold text-gray-900">마이페이지</h1>
      </header>

      <main className="flex-1 overflow-y-auto">
        {error && <div className="mx-4 mt-4 text-sm px-4 py-3 rounded bg-red-50 border border-red-200 text-red-900">{error}</div>}

        <div className="flex items-center gap-3.5 px-4 py-5 border-b border-gray-100">
          <div className="w-11 h-11 bg-gray-900 rounded-full flex items-center justify-center text-white text-base font-bold flex-shrink-0">
            {email.charAt(0).toUpperCase()}
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-gray-900 font-semibold text-sm">{email}</div>
            <div className="text-gray-500 text-xs mt-0.5">{provider === 'KAKAO' ? '카카오 계정' : '메일 계정'}</div>
          </div>
        </div>

        <button onClick={() => navigate('/notifications')} className="w-full flex items-center justify-between px-4 py-4 border-b border-gray-100 text-left">
          <span className="text-gray-900 text-sm">알림 설정</span>
          <span className="text-gray-300 text-sm">›</span>
        </button>
        {provider === 'EMAIL' && (
          <button className="w-full flex items-center justify-between px-4 py-4 border-b border-gray-100 text-left">
            <span className="text-gray-900 text-sm">비밀번호 변경</span>
            <span className="text-gray-300 text-sm">›</span>
          </button>
        )}

        <div className="px-4 pt-6">
          <button onClick={handleLogout} disabled={loggingOut}
            className="w-full py-3 bg-white text-gray-700 border border-gray-300 rounded-lg font-semibold text-sm transition disabled:opacity-50 mb-5">
            {loggingOut ? '로그아웃 중...' : '로그아웃'}
          </button>
          <button onClick={handleWithdrawClick} className="w-full text-center text-gray-400 hover:text-red-600 text-xs transition">계정 삭제</button>
        </div>
      </main>

      {showWithdrawConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4">
          <div className="bg-white rounded-lg p-6 max-w-sm w-full border border-gray-200">
            <h2 className="text-lg font-bold text-gray-900 mb-2">계정 삭제</h2>
            <p className="text-gray-600 text-sm mb-6">정말 탈퇴하시겠어요? 모든 데이터가 삭제됩니다.</p>
            {error && <div className="mb-4 text-sm px-3 py-2 bg-red-50 border border-red-200 text-red-900 rounded">{error}</div>}
            <div className="flex gap-3">
              <button onClick={handleWithdrawCancel} disabled={withdrawing} className="flex-1 px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-900 text-sm rounded font-medium transition disabled:bg-gray-200">취소</button>
              <button onClick={handleWithdraw} disabled={withdrawing} className="flex-1 px-4 py-2 bg-red-500 hover:bg-red-600 text-white text-sm rounded font-medium transition disabled:bg-gray-400">
                {withdrawing ? '삭제 중...' : '탈퇴'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
