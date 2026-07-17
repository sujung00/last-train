import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import api from '../api/axios'
import EmojiSelectorModal from '../components/EmojiSelectorModal'

/**
 * 2b 리디자인: 지도 + 환승 타임라인, 절제된 색/버튼
 * 로직은 기존 ResultPage.jsx와 동일. 마크업만 교체.
 */
function buildTimeline(transfers) {
  const stops = []
  transfers.forEach((t, i) => {
    stops.push({ name: t.boardAt, sub: `${t.line} 승차`, open: false })
    stops.push({
      name: t.alightAt,
      sub: i === transfers.length - 1 ? '하차' : `환승${t.lastBoardTime ? ' · 막차 탑승 ' + t.lastBoardTime : ''}`,
      open: i !== transfers.length - 1,
    })
  })
  return stops
}

export default function ResultPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const result = location.state?.result
  const destObject = location.state?.destination

  const [isFavorited, setIsFavorited] = useState(false)
  const [showEmojiSelector, setShowEmojiSelector] = useState(false)
  const [isSavingFavorite, setIsSavingFavorite] = useState(false)
  const [subscribedRoutes, setSubscribedRoutes] = useState(new Set())
  const [selectedRouteIndex, setSelectedRouteIndex] = useState(null)
  const [showMinutesSelector, setShowMinutesSelector] = useState(false)
  const [isSubscribingNotification, setIsSubscribingNotification] = useState(false)
  const [isLoggedIn] = useState(() => !!localStorage.getItem('accessToken'))

  const coordinateMissing = !destObject || !(typeof destObject.lat === 'number' && typeof destObject.lng === 'number')

  const sameOriginDest = result ? (() => {
    const responseData = result?.data || result
    return responseData.origin === responseData.destination
  })() : false

  useEffect(() => { if (sameOriginDest) console.warn('EC-007: 출발지와 도착지가 동일함') }, [sameOriginDest])
  useEffect(() => { if (destObject && coordinateMissing) console.warn('⚠️ 도착지 좌표 정보 누락:', destObject) }, [destObject, coordinateMissing])

  useEffect(() => {
    if (!isLoggedIn || !result) return
    const responseData = result?.data || result
    const { destination } = responseData
    const checkFavorite = async () => {
      try {
        const response = await api.get('/api/v1/favorites')
        const favorites = response.data?.data || []
        setIsFavorited(favorites.some((fav) => fav.name === destination))
      } catch (error) { console.error('즐겨찾기 목록 조회 실패:', error) }
    }
    checkFavorite()
  }, [isLoggedIn, result])

  if (!result) { navigate('/'); return null }

  const responseData = result?.data || result
  const { origin, destination, date, dayType, routes } = responseData

  if (!routes || !Array.isArray(routes) || routes.length === 0) { navigate('/'); return null }

  const limitedRoutes = routes.slice(0, 5)
  const primaryRoute = limitedRoutes[0]
  const timeline = buildTimeline(primaryRoute?.transfers || [])

  const handleAddFavorite = () => {
    if (coordinateMissing) return
    if (!isLoggedIn) { navigate('/login'); return }
    if (isFavorited) return
    setShowEmojiSelector(true)
  }

  const handleEmojiSelected = async (emoji) => {
    if (coordinateMissing || !destObject) {
      alert('도착지 정보를 가져올 수 없어요. 다시 검색해주세요.')
      setShowEmojiSelector(false)
      return
    }
    setShowEmojiSelector(false)
    setIsSavingFavorite(true)
    try {
      await api.post('/api/v1/favorites', { name: destination, emoji, lat: destObject.lat, lng: destObject.lng, address: null })
      setIsFavorited(true)
    } catch (error) {
      alert('즐겨찾기 등록에 실패했어요. 다시 시도해주세요.')
    } finally {
      setIsSavingFavorite(false)
    }
  }

  const handleNotificationClick = (routeIndex) => {
    if (!isLoggedIn) { navigate('/login'); return }
    setSelectedRouteIndex(routeIndex)
    setShowMinutesSelector(true)
  }

  const urlBase64ToUint8Array = (base64String) => {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
    const rawData = window.atob(base64)
    const outputArray = new Uint8Array(rawData.length)
    for (let i = 0; i < rawData.length; ++i) outputArray[i] = rawData.charCodeAt(i)
    return outputArray
  }

  const handleSubscribeNotification = async (minutesBefore) => {
    setShowMinutesSelector(false)
    setIsSubscribingNotification(true)
    try {
      if (selectedRouteIndex === null || !limitedRoutes[selectedRouteIndex]) {
        alert('경로 정보를 찾을 수 없습니다.')
        return
      }
      const selectedRoute = limitedRoutes[selectedRouteIndex]
      if ('serviceWorker' in navigator) await navigator.serviceWorker.register('/sw.js')
      if ('Notification' in window && Notification.permission === 'default') {
        const permission = await Notification.requestPermission()
        if (permission !== 'granted') { alert('알림 권한이 필요합니다.'); return }
      }
      const registration = await navigator.serviceWorker.ready
      const vapidPublicKey = import.meta.env.VITE_VAPID_PUBLIC_KEY
      const subscription = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(vapidPublicKey) })
      const { endpoint } = subscription
      const key = subscription.getKey('p256dh')
      const auth = subscription.getKey('auth')
      const departureDeadlineStr = selectedRoute?.departureDeadline || '00:00'
      const [hours, minutes] = departureDeadlineStr.split(':').map(Number)
      const departureDate = new Date()
      departureDate.setHours(hours, minutes, 0, 0)
      if (hours >= 0 && hours < 4) departureDate.setDate(departureDate.getDate() + 1)
      const y = departureDate.getFullYear(), m = String(departureDate.getMonth() + 1).padStart(2, '0'),
        d = String(departureDate.getDate()).padStart(2, '0'), h = String(departureDate.getHours()).padStart(2, '0'),
        mi = String(departureDate.getMinutes()).padStart(2, '0')
      await api.post('/api/v1/notifications/subscribe', {
        endpoint,
        auth: btoa(String.fromCharCode.apply(null, new Uint8Array(auth))),
        p256dh: btoa(String.fromCharCode.apply(null, new Uint8Array(key))),
        origin, destination, lastBoardTime: `${y}-${m}-${d}T${h}:${mi}:00`, notifyMinutesBefore: minutesBefore,
      })
      setSubscribedRoutes((prev) => new Set(prev).add(selectedRouteIndex))
      alert(`${minutesBefore}분 전 알림이 설정되었습니다!`)
    } catch (error) {
      console.error('알림 구독 실패:', error)
      alert('알림 설정에 실패했어요. 다시 시도해주세요.')
    } finally {
      setIsSubscribingNotification(false)
      setSelectedRouteIndex(null)
    }
  }

  return (
    <div className="min-h-screen bg-white flex flex-col">
      <div className="relative h-[260px] flex-shrink-0" style={{ backgroundImage: 'repeating-linear-gradient(135deg,#eceef0 0 12px,#e4e7ea 12px 24px)' }}>
        <button onClick={() => navigate('/')} className="absolute top-5 left-4 w-7 h-7 bg-white/90 rounded-md flex items-center justify-center text-gray-700 text-sm">✕</button>
        <div className="absolute left-4 bottom-3.5 font-mono text-[11px] text-gray-500">경로 지도 · 환승 지점 표시</div>
      </div>

      <div className="flex-1 bg-white rounded-t-2xl -mt-4 shadow-[0_-2px_10px_rgba(0,0,0,0.06)] border border-gray-200 border-b-0 flex flex-col overflow-hidden">
        <div className="w-8 h-[3px] bg-gray-200 rounded-full mx-auto mt-2.5 flex-shrink-0" />
        <main className="px-4 pt-4 pb-6 overflow-y-auto flex-1">
          {sameOriginDest && (
            <div className="mb-4 text-sm text-amber-900 bg-amber-50 px-4 py-3 rounded border border-amber-200">출발지와 도착지가 같습니다. 다시 확인해주세요.</div>
          )}

          <div className="font-bold text-base text-gray-900 mb-0.5">{origin} <span className="text-gray-400 font-normal">→</span> {destination}</div>
          <div className="text-gray-500 text-xs mb-4">{date} · {dayType === 'WEEKDAY' ? '평일' : dayType === 'SAT' ? '토요일' : '일요일'}</div>

          <div className="flex items-center justify-between border border-gray-200 rounded-lg px-4 py-3.5 mb-5">
            <div>
              <div className="text-gray-500 text-[11px] font-medium uppercase tracking-wide mb-1">출발 마감</div>
              <div className="text-gray-900 font-bold text-xl">{primaryRoute.departureDeadline}</div>
            </div>
            <div className="w-px self-stretch bg-gray-200" />
            <div className="text-right">
              <div className="text-gray-500 text-[11px] font-medium uppercase tracking-wide mb-1">남은 시간</div>
              <div className={`font-bold text-xl ${primaryRoute.currentStatus.canCatch ? 'text-green-600' : 'text-red-600'}`}>{primaryRoute.currentStatus.minutesLeft}분</div>
            </div>
          </div>

          {primaryRoute.transfers?.length > 0 && (
            <div className="mb-5">
              <div className="text-gray-500 text-[11px] font-medium mb-3 uppercase tracking-wide">경로</div>
              <div className="flex flex-col">
                {timeline.map((stop, i) => (
                  <div key={i} className="flex gap-3">
                    <div className="flex flex-col items-center w-2">
                      <div className={`w-2 h-2 rounded-full flex-shrink-0 mt-[3px] ${stop.open ? 'bg-white border-[1.5px] border-gray-900' : 'bg-gray-900'}`} />
                      {i < timeline.length - 1 && <div className="w-px flex-1 bg-gray-300 my-1" />}
                    </div>
                    <div className={i < timeline.length - 1 ? 'pb-4' : ''}>
                      <div className="text-[13px] font-semibold text-gray-900">{stop.name}</div>
                      <div className="text-xs text-gray-500 mt-0.5">{stop.sub}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="flex items-center justify-between py-3 border-t border-gray-200 mb-4">
            <span className="text-sm text-gray-700">막차 놓치면 미리 알려드려요</span>
            <button
              onClick={() => handleNotificationClick(0)}
              disabled={subscribedRoutes.has(0) || (isSubscribingNotification && selectedRouteIndex === 0)}
              className="text-xs text-blue-600 font-semibold whitespace-nowrap ml-3 px-3 py-1.5 border border-blue-200 rounded-full disabled:opacity-50"
            >
              {subscribedRoutes.has(0) ? '✓ 알림 설정됨' : '이 경로 알림받기'}
            </button>
          </div>

          {limitedRoutes.length > 1 && (
            <div className="mb-5">
              <div className="text-gray-500 text-[11px] font-medium mb-2 uppercase tracking-wide">대안 경로</div>
              <div className="flex flex-col">
                {limitedRoutes.slice(1).map((route, i) => {
                  const idx = i + 1
                  return (
                    <div key={idx} className="flex items-center justify-between py-2.5 border-b border-gray-100">
                      <div>
                        <div className="text-[13px] font-medium text-gray-900">선택지 {idx} · 출발 마감 {route.departureDeadline}</div>
                        <div className={`text-xs mt-0.5 ${route.currentStatus.canCatch ? 'text-green-600' : 'text-red-600'}`}>{route.currentStatus.canCatch ? '탑승 가능' : '탑승 불가'}</div>
                      </div>
                      <button
                        onClick={() => handleNotificationClick(idx)}
                        disabled={subscribedRoutes.has(idx)}
                        className="text-xs text-blue-600 font-semibold whitespace-nowrap px-3 py-1.5 border border-blue-200 rounded-full disabled:opacity-50"
                      >
                        {subscribedRoutes.has(idx) ? '✓ 설정됨' : '알림받기'}
                      </button>
                    </div>
                  )
                })}
              </div>
            </div>
          )}

          {coordinateMissing && (
            <div className="mb-4 text-sm text-red-900 bg-red-50 px-4 py-3 rounded border border-red-200">도착지 정보를 가져올 수 없습니다. 다시 검색해주세요.</div>
          )}

          <button
            onClick={handleAddFavorite}
            disabled={isFavorited || isSavingFavorite || coordinateMissing}
            className={`w-full py-3.5 font-semibold rounded-lg transition text-[15px] mb-2 ${coordinateMissing || isFavorited ? 'bg-gray-200 text-gray-400 cursor-not-allowed' : 'bg-cyan-600 hover:bg-cyan-700 text-white'}`}
          >
            {isSavingFavorite ? '등록 중...' : isFavorited ? '즐겨찾기 등록됨' : coordinateMissing ? '위치 정보 오류' : '즐겨찾기 추가'}
          </button>
          <button onClick={() => navigate('/')} className="w-full py-3.5 font-semibold rounded-lg text-[15px] bg-white text-gray-900 border border-gray-300">다시 검색</button>
        </main>
      </div>

      {showEmojiSelector && <EmojiSelectorModal onSelect={handleEmojiSelected} onClose={() => setShowEmojiSelector(false)} destination={destination} />}

      {showMinutesSelector && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4">
          <div className="bg-white rounded-lg p-6 max-w-sm w-full border border-gray-200">
            <h2 className="text-lg font-bold text-gray-900 mb-2">알림 시간 설정</h2>
            <p className="text-gray-600 text-sm mb-6">막차까지 몇 분 전에 알림을 받을지 선택해주세요.</p>
            <div className="space-y-3">
              {[10, 20, 30].map((minutes) => (
                <button key={minutes} onClick={() => handleSubscribeNotification(minutes)} disabled={isSubscribingNotification}
                  className="w-full px-4 py-3 bg-gray-900 hover:bg-black text-white rounded font-medium transition disabled:bg-gray-300">
                  {minutes}분 전에 알림 받기
                </button>
              ))}
            </div>
            <button onClick={() => setShowMinutesSelector(false)} disabled={isSubscribingNotification}
              className="w-full mt-4 px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-900 rounded font-medium transition disabled:bg-gray-200">취소</button>
          </div>
        </div>
      )}
    </div>
  )
}
