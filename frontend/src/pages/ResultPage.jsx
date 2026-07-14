import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import api from '../api/axios'
import EmojiSelectorModal from '../components/EmojiSelectorModal'

/**
 * T-008 구현: 결과 화면 구성
 * T-010 추가 구현: 즐겨찾기 추가 버튼 + 이모지 선택
 *
 * ✅ FIX: 조건부 hooks 호출 제거
 *   - 모든 useState/useEffect를 컴포넌트 최상단에 정의
 *   - early return은 JSX에서 처리 (hooks 호출 이후)
 *   - useEffect 내에서 result 체크하여 필요한 로직만 실행
 *
 * FR-010: 막차 조회 결과 화면에 추천 경로와 대안 경로를 최대 5개까지 표시해야 한다
 * FR-011: 결과 화면 상단에 막차까지 남은 분(分)을 표시해야 한다
 * FR-012: 결과 화면에서 "즐겨찾기 추가" 버튼으로 해당 목적지를 즐겨찾기에 등록할 수 있어야 한다
 * FR-013: 즐겨찾기 등록 시 사용자가 직접 이모지를 선택해 설정할 수 있어야 한다
 *
 * AC-008: WHEN 결과 화면이 로드되면, THE 시스템 SHALL 막차까지 남은 시간을 분 단위로 표시한다.
 * AC-009: IF 비로그인 사용자가 즐겨찾기 추가 버튼을 탭하면, THE 시스템 SHALL 로그인 페이지로 이동한다.
 *
 * EC-005: 비로그인 즐겨찾기 추가 탭 → 로그인 페이지로 이동
 * EC-006: 이미 즐겨찾기 등록된 목적지 → 버튼 텍스트를 "즐겨찾기 등록됨"으로 변경, 비활성화
 * EC-007: 출발지와 도착지가 동일 → 방어적 검사 (MainPage에서 이미 차단)
 *
 * 데이터 흐름:
 * - MainPage.jsx에서 navigate('/result', { state: { result: data } })로 전달
 * - data: {origin, destination, date, dayType, routes: [{departureDeadline, currentStatus, transfers}]}
 */
export default function ResultPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const result = location.state?.result
  const destObject = location.state?.destination // MainPage에서 전달받은 destination 객체 {name, lat, lng}

  // ── 모든 State를 최상단에 정의 (early return보다 먼저) ─────────────────────
  const [isFavorited, setIsFavorited] = useState(false)
  const [showEmojiSelector, setShowEmojiSelector] = useState(false)
  const [isSavingFavorite, setIsSavingFavorite] = useState(false)
  const [subscribedRoutes, setSubscribedRoutes] = useState(new Set()) // 구독된 경로 인덱스
  const [selectedRouteIndex, setSelectedRouteIndex] = useState(null) // 현재 구독 중인 경로
  const [showMinutesSelector, setShowMinutesSelector] = useState(false)
  const [isSubscribingNotification, setIsSubscribingNotification] = useState(false)

  // ✅ 로그인 상태: 초기값 계산 함수로 이동 (setState in effect 제거)
  const [isLoggedIn] = useState(() => !!localStorage.getItem('accessToken'))

  // ✅ 좌표 유효성: derived state로 계산 (setState in effect 제거)
  const coordinateMissing = !destObject ||
    !(typeof destObject.lat === 'number' && typeof destObject.lng === 'number')

  // ✅ 출발지=도착지: derived state로 계산 (setState in effect 제거)
  const sameOriginDest = result ?
    (() => {
      const responseData = result?.data || result
      return responseData.origin === responseData.destination
    })()
    : false

  // ── 모든 useEffect를 최상단에 정의 (early return보다 먼저) ───────────────────

  // EC-007: 출발지 = 도착지 검사 (로그만 남김)
  useEffect(() => {
    if (!sameOriginDest) return
    console.warn('EC-007: 출발지와 도착지가 동일함 (방어적 검사)')
  }, [sameOriginDest])

  // 좌표 유효성 검사 (로그만 남김)
  useEffect(() => {
    if (!destObject) return
    if (!coordinateMissing) return

    console.warn('⚠️ 도착지 좌표 정보 누락:', destObject)
  }, [destObject, coordinateMissing])

  // 이미 즐겨찾기에 등록된 목적지인지 확인 (EC-006)
  useEffect(() => {
    if (!isLoggedIn || !result) return

    const responseData = result?.data || result
    const { destination } = responseData

    const checkFavorite = async () => {
      try {
        const response = await api.get('/api/v1/favorites')
        // ApiResponse 형식: { code, data: [...] }
        const favorites = response.data?.data || []

        // destination 이름이 이미 등록되어 있는지 확인
        const alreadyFavorited = favorites.some(
          (fav) => fav.name === destination
        )
        setIsFavorited(alreadyFavorited)
      } catch (error) {
        console.error('즐겨찾기 목록 조회 실패:', error)
        // 에러는 무시하고 계속 진행 (사용자가 즐겨찾기 추가는 가능)
      }
    }

    checkFavorite()
  }, [isLoggedIn, result])

  // ── Early return은 JSX에서 처리 (모든 hooks 호출 이후) ─────────────────────
  // 데이터 미존재 시 메인으로 리다이렉트
  if (!result) {
    navigate('/')
    return null
  }

  // ── ApiResponse 구조 처리 ──────────────────────────────────────────────
  // 백엔드에서 { code, data: { origin, destination, date, dayType, routes } } 형식으로 반환
  const responseData = result?.data || result
  const { origin, destination, date, dayType, routes } = responseData

  // ── routes 안전 처리 (undefined/null 체크) ───────────────────────────────
  // 경로 없음 또는 데이터 불완전한 경우 메인으로 리다이렉트
  if (!routes || !Array.isArray(routes) || routes.length === 0) {
    console.warn('⚠️ routes 데이터 없음:', { responseData, routes })
    navigate('/')
    return null
  }

  // 경로를 최대 5개로 제한 (FR-010)
  const limitedRoutes = routes.slice(0, 5)

  // 첫 번째 경로의 남은 시간 (AC-008)
  const primaryRoute = limitedRoutes[0]
  const minutesLeft = primaryRoute?.currentStatus?.minutesLeft || 0
  const primaryMessage = primaryRoute?.currentStatus?.message || ''

  // ── T-010: 즐겨찾기 추가 버튼 클릭 처리 (FR-012, AC-009, EC-005) ─────────
  const handleAddFavorite = () => {
    // 좌표 누락: 버튼 비활성화 (클릭 불가)
    if (coordinateMissing) {
      return
    }

    // AC-009, EC-005: 비로그인 사용자는 로그인 페이지로
    if (!isLoggedIn) {
      navigate('/login')
      return
    }

    // 이미 등록된 경우는 버튼이 비활성화되어야 함
    if (isFavorited) {
      return
    }

    // 이모지 선택 모달 표시
    setShowEmojiSelector(true)
  }

  // ── T-010: 이모지 선택 후 즐겨찾기 등록 (FR-012, FR-013) ────────────────
  const handleEmojiSelected = async (emoji) => {
    // 좌표 유효성 재확인
    if (coordinateMissing || !destObject) {
      console.error('❌ 도착지 좌표 정보 누락')
      alert('도착지 정보를 가져올 수 없어요. 다시 검색해주세요.')
      setShowEmojiSelector(false)
      return
    }

    setShowEmojiSelector(false)
    setIsSavingFavorite(true)

    try {
      // POST /api/v1/favorites 호출 (FavoriteRequest 스펙에 맞춰 필수 필드 포함)
      await api.post('/api/v1/favorites', {
        name: destination,
        emoji: emoji,
        lat: destObject.lat,  // ✅ 좌표 필수 (폴백 제거)
        lng: destObject.lng,  // ✅ 좌표 필수 (폴백 제거)
        address: null,        // 주소는 현재 없음 (선택사항)
      })

      // 성공 시 즐겨찾기 등록 상태 표시
      setIsFavorited(true)
    } catch (error) {
      console.error('즐겨찾기 등록 실패:', error)
      alert('즐겨찾기 등록에 실패했어요. 다시 시도해주세요.')
    } finally {
      setIsSavingFavorite(false)
    }
  }

  // ── 경로별 알림 받기 버튼 클릭 처리 ────────────────────────────────────────
  const handleNotificationClick = (routeIndex) => {
    // 비로그인 사용자는 로그인 페이지로
    if (!isLoggedIn) {
      navigate('/login')
      return
    }

    // 현재 선택된 경로 저장 후 분 선택 모달 표시
    setSelectedRouteIndex(routeIndex)
    setShowMinutesSelector(true)
  }

  // ── 알림 구독 로직 (선택된 경로 기준) ──────────────────────────────────────
  const handleSubscribeNotification = async (minutesBefore) => {
    setShowMinutesSelector(false)
    setIsSubscribingNotification(true)

    try {
      // 선택된 경로 확인
      if (selectedRouteIndex === null || !limitedRoutes[selectedRouteIndex]) {
        alert('경로 정보를 찾을 수 없습니다.')
        setIsSubscribingNotification(false)
        return
      }

      const selectedRoute = limitedRoutes[selectedRouteIndex]

      // 1️⃣ ServiceWorker 등록
      if ('serviceWorker' in navigator) {
        await navigator.serviceWorker.register('/sw.js')
      }

      // 2️⃣ Notification 권한 요청
      if ('Notification' in window && Notification.permission === 'default') {
        const permission = await Notification.requestPermission()
        if (permission !== 'granted') {
          alert('알림 권한이 필요합니다.')
          setIsSubscribingNotification(false)
          return
        }
      }

      // 3️⃣ Push 구독 (PushManager.subscribe)
      const registration = await navigator.serviceWorker.ready
      const vapidPublicKey = import.meta.env.VITE_VAPID_PUBLIC_KEY
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
      })

      // 4️⃣ 구독 정보 추출
      const { endpoint } = subscription
      const key = subscription.getKey('p256dh')
      const auth = subscription.getKey('auth')

      // 5️⃣ departureDeadline (HH:mm) → LocalDateTime (ISO 형식) 변환
      const departureDeadlineStr = selectedRoute?.departureDeadline || '00:00'
      const [hours, minutes] = departureDeadlineStr.split(':').map(Number)

      // 현재 날짜 기준으로 시작
      const departureDate = new Date()
      departureDate.setHours(hours, minutes, 0, 0)

      // 자정 넘김 시간(00~03시)은 다음날로 처리
      if (hours >= 0 && hours < 4) {
        departureDate.setDate(departureDate.getDate() + 1)
      }

      // ISO 형식으로 변환 ("2026-07-09T01:08:00")
      const year = departureDate.getFullYear()
      const month = String(departureDate.getMonth() + 1).padStart(2, '0')
      const day = String(departureDate.getDate()).padStart(2, '0')
      const hour = String(departureDate.getHours()).padStart(2, '0')
      const min = String(departureDate.getMinutes()).padStart(2, '0')
      const lastBoardTimeISO = `${year}-${month}-${day}T${hour}:${min}:00`

      // 6️⃣ 백엔드에 구독 정보 전송
      await api.post('/api/v1/notifications/subscribe', {
        endpoint: endpoint,
        auth: btoa(String.fromCharCode.apply(null, new Uint8Array(auth))),
        p256dh: btoa(String.fromCharCode.apply(null, new Uint8Array(key))),
        origin: origin,
        destination: destination,
        lastBoardTime: lastBoardTimeISO,
        notifyMinutesBefore: minutesBefore,
      })

      // 7️⃣ 성공 시 구독된 경로 목록 업데이트
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

  // ── Base64 URL을 Uint8Array로 변환 (VAPID 공개키 변환용) ──────────────────
  const urlBase64ToUint8Array = (base64String) => {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
    const base64 = (base64String + padding)
      .replace(/\-/g, '+')
      .replace(/_/g, '/')

    const rawData = window.atob(base64)
    const outputArray = new Uint8Array(rawData.length)

    for (let i = 0; i < rawData.length; ++i) {
      outputArray[i] = rawData.charCodeAt(i)
    }

    return outputArray
  }

  return (
    <div className="min-h-screen bg-white flex flex-col">
      {/* 헤더 */}
      <header className="bg-white border-b border-gray-200 px-4 py-6 sticky top-0 z-10">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">조회 결과</h1>
            <p className="text-gray-500 text-sm mt-1">지금 출발하면 탑승 가능합니다</p>
          </div>
          <button
            onClick={() => navigate('/')}
            className="px-3 py-2 text-sm text-gray-500 hover:text-gray-900 transition"
            title="닫기"
          >
            ✕
          </button>
        </div>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="flex-1 px-4 py-6 overflow-y-auto">
        {/* 출발지 = 도착지 경고 배너 (EC-007) */}
        {sameOriginDest && (
          <div className="mb-6 text-sm text-amber-900 bg-amber-50 px-4 py-3 rounded border border-amber-200">
            출발지와 도착지가 같습니다. 다시 확인해주세요.
          </div>
        )}

        {/* 출발지/도착지 요약 헤더 */}
        <div className="mb-6 bg-gray-50 rounded-lg p-4 border border-gray-200">
          <div className="text-gray-600 text-xs font-semibold mb-2 uppercase tracking-wide">경로</div>
          <div className="text-gray-900 font-bold text-lg">
            {origin} <span className="text-gray-400">→</span> {destination}
          </div>
          <div className="text-gray-500 text-xs mt-2">
            {date} • {dayType === 'WEEKDAY' ? '평일' : dayType === 'SAT' ? '토요일' : '일요일'}
          </div>
        </div>

        {/* 막차까지 남은 시간 (AC-008, FR-011) - 시그니처 요소 */}
        <div className="mb-8 bg-gray-900 rounded-lg p-6 text-center">
          <div className="text-gray-400 text-sm font-medium mb-2">막차까지 남은 시간</div>
          <div className="text-6xl font-bold text-white mb-3">{minutesLeft}</div>
          <div className="text-gray-300 text-base">{primaryMessage}</div>
        </div>

        {/* 막차 시간 안내 배너 */}
        <div className="mb-8 text-sm text-blue-900 bg-blue-50 px-4 py-3 rounded border border-blue-200">
          막차 시간은 기점 기준 예측값으로 실제와 다를 수 있습니다. 5~10분 여유를 두고 이동하세요.
        </div>

        {/* 경로 카드 목록 (FR-010) */}
        <div className="space-y-3 mb-8">
          {limitedRoutes.map((route, index) => (
            <RouteCard
              key={index}
              route={route}
              index={index}
              isRecommended={index === 0}
              isSubscribed={subscribedRoutes.has(index)}
              onNotificationClick={() => handleNotificationClick(index)}
              isSubscribing={isSubscribingNotification && selectedRouteIndex === index}
            />
          ))}
        </div>

        {/* 경로가 없는 경우 */}
        {limitedRoutes.length === 0 && (
          <div className="text-center py-12">
            <div className="text-gray-600 text-lg font-medium">조회된 경로가 없습니다</div>
            <button
              onClick={() => navigate('/')}
              className="mt-4 px-6 py-3 bg-gray-900 hover:bg-black text-white rounded-lg font-medium transition"
            >
              다시 검색하기
            </button>
          </div>
        )}

        {/* 좌표 정보 누락 에러 */}
        {limitedRoutes.length > 0 && coordinateMissing && (
          <div className="mb-4 text-sm text-red-900 bg-red-50 px-4 py-3 rounded border border-red-200">
            도착지 정보를 가져올 수 없습니다. 다시 검색해주세요.
          </div>
        )}

        {/* 즐겨찾기 추가 버튼 (T-010: FR-012, FR-213, AC-009, EC-005, EC-006) */}
        {limitedRoutes.length > 0 && (
          <div className="mb-4">
            <button
              onClick={handleAddFavorite}
              disabled={isFavorited || isSavingFavorite || coordinateMissing}
              className={`w-full py-3 font-medium rounded-lg transition ${
                coordinateMissing
                  ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                  : isFavorited
                  ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                  : 'bg-cyan-600 hover:bg-cyan-700 text-white'
              }`}
            >
              {isSavingFavorite
                ? '등록 중...'
                : isFavorited
                ? '즐겨찾기 등록됨'
                : coordinateMissing
                ? '위치 정보 오류'
                : '자주 가는 곳에 추가'}
            </button>
          </div>
        )}

        {/* 다시 검색 버튼 (경로가 있는 경우) */}
        {limitedRoutes.length > 0 && (
          <button
            onClick={() => navigate('/')}
            className="w-full py-3 bg-gray-100 hover:bg-gray-200 text-gray-900 font-medium rounded-lg transition border border-gray-200"
          >
            다시 검색
          </button>
        )}
      </main>

      {/* 이모지 선택 모달 (T-010: FR-013) - 조건부 렌더링으로 통일 */}
      {showEmojiSelector && (
        <EmojiSelectorModal
          onSelect={handleEmojiSelected}
          onClose={() => setShowEmojiSelector(false)}
          destination={destination}
        />
      )}

      {/* 알림 분 선택 모달 */}
      {showMinutesSelector && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 px-4">
          <div className="bg-white rounded-lg p-6 max-w-sm w-full border border-gray-200">
            <h2 className="text-lg font-bold text-gray-900 mb-2">알림 시간 설정</h2>
            <p className="text-gray-600 text-sm mb-6">
              막차까지 몇 분 전에 알림을 받을지 선택해주세요.
            </p>

            <div className="space-y-3">
              {[10, 20, 30].map((minutes) => (
                <button
                  key={minutes}
                  onClick={() => handleSubscribeNotification(minutes)}
                  disabled={isSubscribingNotification}
                  className="w-full px-4 py-3 bg-cyan-600 hover:bg-cyan-700 text-white rounded font-medium transition disabled:bg-gray-400 disabled:cursor-not-allowed"
                >
                  {minutes}분 전에 알림 받기
                </button>
              ))}
            </div>

            <button
              onClick={() => setShowMinutesSelector(false)}
              disabled={isSubscribingNotification}
              className="w-full mt-4 px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-900 rounded font-medium transition disabled:bg-gray-300 disabled:cursor-not-allowed"
            >
              취소
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * 경로 카드 컴포넌트
 * 추천 경로(index 0)와 대안 경로(index > 0)를 시각적으로 구분
 */
function RouteCard({ route, index, isRecommended, isSubscribed, onNotificationClick, isSubscribing }) {
  const { departureDeadline, currentStatus, transfers } = route
  const { canCatch, minutesLeft, message } = currentStatus

  return (
    <div
      className={`rounded-lg p-4 border transition ${
        isRecommended
          ? 'bg-gray-50 border-gray-300'
          : 'bg-white border-gray-200 hover:border-gray-300'
      }`}
    >
      {/* 추천/대안 라벨 */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          {isRecommended && (
            <span className="inline-block px-2 py-1 bg-gray-900 text-white text-xs font-bold rounded">
              추천
            </span>
          )}
          {!isRecommended && (
            <span className="inline-block px-2 py-1 bg-gray-200 text-gray-700 text-xs font-medium rounded">
              선택지 {index}
            </span>
          )}
        </div>
        <div
          className={`text-sm font-semibold ${
            canCatch ? 'text-green-600' : 'text-red-600'
          }`}
        >
          {canCatch ? '탑승 가능' : '탑승 불가'}
        </div>
      </div>

      {/* 출발 마감 시각 */}
      <div className="mb-3 pb-3 border-b border-gray-200">
        <div className="text-gray-600 text-xs font-medium mb-1 uppercase tracking-wide">출발 마감</div>
        <div className="text-gray-900 font-bold text-2xl">{departureDeadline}</div>
      </div>

      {/* 환승 정보 */}
      {transfers && transfers.length > 0 && (
        <div className="space-y-2">
          <div className="text-gray-600 text-xs font-medium mb-2 uppercase tracking-wide">경로</div>
          {transfers.map((transfer, idx) => (
            <TransferSection key={idx} transfer={transfer} />
          ))}
        </div>
      )}

      {/* 추가 정보 */}
      <div className="mt-3 pt-3 border-t border-gray-200 space-y-2">
        {minutesLeft > 0 && (
          <div className="text-gray-700 text-sm">
            <span className="text-gray-900 font-semibold">{minutesLeft}분</span> 내에 탑승 필요
          </div>
        )}
        {message && (
          <div className="text-gray-600 text-sm">{message}</div>
        )}
      </div>

      {/* 알림 받기 버튼 */}
      <div className="mt-4">
        <button
          onClick={onNotificationClick}
          disabled={isSubscribed || isSubscribing}
          className={`w-full py-2 text-sm font-medium rounded transition ${
            isSubscribed
              ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
              : 'bg-green-600 hover:bg-green-700 text-white'
          }`}
        >
          {isSubscribing
            ? '알림 설정 중...'
            : isSubscribed
            ? '✓ 알림 설정됨'
            : '알림 받기'}
        </button>
      </div>
    </div>
  )
}

/**
 * 환승 구간 컴포넌트
 */
function TransferSection({ transfer }) {
  const { type, line, boardAt, alightAt, lastBoardTime } = transfer

  // 교통 수단 아이콘
  const typeIcon = type === 'SUBWAY' ? '🚇' : '🚌'
  const typeLabel = type === 'SUBWAY' ? '지하철' : '버스'

  return (
    <div className="bg-gray-50 border border-gray-200 rounded p-3">
      <div className="flex items-center gap-2 mb-2">
        <span className="text-lg">{typeIcon}</span>
        <span className="text-gray-900 font-semibold">{line}</span>
        <span className="text-gray-500 text-xs">({typeLabel})</span>
      </div>
      <div className="text-gray-700 text-sm">
        <div className="text-gray-900">{boardAt}</div>
        <div className="text-gray-400 text-xs my-1">↓</div>
        <div className="text-gray-900">{alightAt}</div>
      </div>
      {lastBoardTime && (
        <div className="text-gray-500 text-xs mt-2">
          막차 탑승: {lastBoardTime}
        </div>
      )}
    </div>
  )
}
