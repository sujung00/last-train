import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'

/**
 * 알림 설정 페이지
 *
 * 기능:
 * - 구독 목록 조회 (GET /api/v1/notifications)
 * - 각 구독 항목 표시 (출발지 → 도착지, 막차 시간, 알림 시간)
 * - 구독 삭제 (DELETE /api/v1/notifications/{subscriptionId})
 * - 목록이 비어있으면 "설정된 알림이 없어요" 표시
 */
export default function NotificationSettingsPage() {
  const navigate = useNavigate()
  const [subscriptions, setSubscriptions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [deletingId, setDeletingId] = useState(null)

  // 구독 목록 조회
  useEffect(() => {
    const fetchSubscriptions = async () => {
      try {
        const response = await api.get('/api/v1/notifications')
        setSubscriptions(response.data?.data || [])
      } catch (err) {
        console.error('구독 목록 조회 실패:', err)
        setError('알림 설정을 불러올 수 없어요.')
      } finally {
        setLoading(false)
      }
    }

    fetchSubscriptions()
  }, [])

  // 구독 삭제
  const handleDeleteSubscription = async (subscriptionId) => {
    if (!window.confirm('이 알림을 삭제하시겠어요?')) {
      return
    }

    setDeletingId(subscriptionId)
    try {
      await api.delete(`/api/v1/notifications/${subscriptionId}`)
      setSubscriptions((prev) => prev.filter((sub) => sub.subscriptionId !== subscriptionId))
    } catch (err) {
      console.error('알림 삭제 실패:', err)
      setError('알림 삭제에 실패했어요.')
    } finally {
      setDeletingId(null)
    }
  }

  // HH:mm 형식으로 시간 변환
  const formatTime = (isoString) => {
    if (!isoString) return '--:--'
    const date = new Date(isoString)
    return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
  }

  return (
    <div className="min-h-screen bg-white flex flex-col">
      {/* 헤더 */}
      <header className="bg-white border-b border-gray-200 px-4 py-6 sticky top-0 z-10">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">알림 설정</h1>
            <p className="text-gray-600 text-sm mt-1">예약된 알림 목록</p>
          </div>
          <button
            onClick={() => navigate(-1)}
            className="px-3 py-2 text-sm text-gray-600 hover:text-gray-900 transition"
          >
            ← 뒤로
          </button>
        </div>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="flex-1 px-4 py-6 overflow-y-auto">
        {/* 에러 메시지 */}
        {error && (
          <div className="mb-6 text-sm px-4 py-3 rounded bg-red-50 border border-red-200 text-red-900">
            {error}
          </div>
        )}

        {/* 로딩 중 */}
        {loading && (
          <div className="flex flex-col items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-2 border-gray-300 border-t-gray-900 mb-4"></div>
            <span className="text-gray-600">불러오는 중...</span>
          </div>
        )}

        {/* 구독 목록이 비어있음 */}
        {!loading && subscriptions.length === 0 && (
          <div className="text-center py-12">
            <div className="text-gray-600 text-lg mb-2">설정된 알림이 없습니다</div>
            <p className="text-gray-500 text-sm">
              경로 검색 결과에서 "알림 받기"를 클릭해서 알림을 설정하세요.
            </p>
            <button
              onClick={() => navigate('/')}
              className="mt-6 px-6 py-3 bg-gray-900 hover:bg-black text-white rounded-lg font-medium transition"
            >
              경로 검색하기
            </button>
          </div>
        )}

        {/* 구독 목록 */}
        {!loading && subscriptions.length > 0 && (
          <div className="space-y-3 mb-8">
            {subscriptions.map((subscription) => (
              <div
                key={subscription.subscriptionId}
                className="bg-gray-50 rounded-lg p-4 border border-gray-200"
              >
                {/* 출발지 → 도착지 */}
                <div className="flex items-center justify-between mb-3">
                  <div className="text-gray-900 font-semibold">
                    {subscription.origin} <span className="text-gray-400">→</span> {subscription.destination}
                  </div>
                  <button
                    onClick={() => handleDeleteSubscription(subscription.subscriptionId)}
                    disabled={deletingId === subscription.subscriptionId}
                    className="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded transition disabled:opacity-50 disabled:cursor-not-allowed"
                    title="삭제"
                  >
                    ✕
                  </button>
                </div>

                {/* 막차 시간 및 알림 시간 */}
                <div className="space-y-2 text-sm">
                  <div className="text-gray-700">
                    <span className="text-gray-600">막차:</span>
                    <span className="ml-2 font-medium">{formatTime(subscription.lastBoardTime)}</span>
                  </div>
                  <div className="text-gray-700">
                    <span className="text-gray-600">알림:</span>
                    <span className="ml-2 font-medium text-gray-900">
                      {subscription.notifyMinutesBefore}분 전
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
