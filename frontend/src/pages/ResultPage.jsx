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

  return (
    <div className="min-h-screen bg-[#1a1a2e] flex flex-col">
      {/* 헤더 */}
      <header className="bg-[#1a1a2e] border-b border-gray-700 px-4 py-6 sticky top-0 z-10">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-white">막차알리미 🚂</h1>
            <p className="text-gray-400 text-sm mt-1">조회 결과</p>
          </div>
          <button
            onClick={() => navigate('/')}
            className="px-3 py-2 text-sm text-gray-300 hover:text-white transition"
          >
            ✕
          </button>
        </div>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="flex-1 px-4 py-6 overflow-y-auto">
        {/* 출발지 = 도착지 경고 배너 (EC-007) */}
        {sameOriginDest && (
          <div className="mb-6 text-sm text-yellow-200 bg-yellow-900 bg-opacity-50 px-4 py-3 rounded border border-yellow-600">
            ⚠️ 출발지와 도착지가 같아요
          </div>
        )}

        {/* 출발지/도착지 요약 헤더 */}
        <div className="mb-6 bg-gray-800 rounded-lg p-4 border border-gray-700">
          <div className="text-gray-400 text-xs mb-1">경로</div>
          <div className="text-white font-bold text-lg">
            {origin} <span className="text-purple-400">→</span> {destination}
          </div>
          <div className="text-gray-400 text-xs mt-2">
            {date} ({dayType === 'WEEKDAY' ? '평일' : dayType === 'SAT' ? '토요일' : '일요일'})
          </div>
        </div>

        {/* 막차까지 남은 시간 배너 (AC-008, FR-011) */}
        <div className="mb-8 bg-gradient-to-r from-purple-900 to-purple-800 rounded-lg p-6 border border-purple-700">
          <div className="text-center">
            <div className="text-purple-300 text-sm font-medium mb-2">막차까지 남은 시간</div>
            <div className="text-4xl font-bold text-white mb-2">{minutesLeft}분</div>
            <div className="text-purple-200 text-sm">{primaryMessage}</div>
          </div>
        </div>

        {/* 경로 카드 목록 (FR-010) */}
        <div className="space-y-4 mb-8">
          {limitedRoutes.map((route, index) => (
            <RouteCard
              key={index}
              route={route}
              index={index}
              isRecommended={index === 0}
            />
          ))}
        </div>

        {/* 경로가 없는 경우 */}
        {limitedRoutes.length === 0 && (
          <div className="text-center py-12">
            <div className="text-gray-400 text-lg">조회된 경로가 없어요</div>
            <button
              onClick={() => navigate('/')}
              className="mt-4 px-6 py-3 bg-purple-600 hover:bg-purple-700 text-white rounded-lg font-medium transition"
            >
              다시 검색하기
            </button>
          </div>
        )}

        {/* 좌표 정보 누락 에러 */}
        {limitedRoutes.length > 0 && coordinateMissing && (
          <div className="mb-4 text-sm text-red-200 bg-red-900 bg-opacity-50 px-4 py-3 rounded border border-red-600">
            ❌ 도착지 정보를 가져올 수 없습니다. 다시 검색해주세요.
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
                  ? 'bg-gray-600 text-gray-400 cursor-not-allowed'
                  : isFavorited
                  ? 'bg-gray-600 text-gray-400 cursor-not-allowed'
                  : 'bg-purple-600 hover:bg-purple-700 text-white'
              }`}
            >
              {isSavingFavorite
                ? '등록 중...'
                : isFavorited
                ? '즐겨찾기 등록됨'
                : coordinateMissing
                ? '위치 정보 오류'
                : '이 목적지 즐겨찾기 추가'}
            </button>
          </div>
        )}

        {/* 다시 검색 버튼 (경로가 있는 경우) */}
        {limitedRoutes.length > 0 && (
          <button
            onClick={() => navigate('/')}
            className="w-full py-3 bg-gray-800 hover:bg-gray-700 text-white font-medium rounded-lg transition border border-gray-700"
          >
            다시 검색
          </button>
        )}
      </main>

      {/* 이모지 선택 모달 (T-010: FR-013) */}
      <EmojiSelectorModal
        isOpen={showEmojiSelector}
        onSelect={handleEmojiSelected}
        onClose={() => setShowEmojiSelector(false)}
        destination={destination}
      />
    </div>
  )
}

/**
 * 경로 카드 컴포넌트
 * 추천 경로(index 0)와 대안 경로(index > 0)를 시각적으로 구분
 */
function RouteCard({ route, index, isRecommended }) {
  const { departureDeadline, currentStatus, transfers } = route
  const { canCatch, minutesLeft, message } = currentStatus

  return (
    <div
      className={`rounded-lg p-4 border transition ${
        isRecommended
          ? 'bg-purple-900 bg-opacity-20 border-purple-600 border-opacity-50'
          : 'bg-gray-800 border-gray-700 hover:border-gray-600'
      }`}
    >
      {/* 추천/대안 라벨 */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          {isRecommended && (
            <span className="inline-block px-2 py-1 bg-purple-600 text-white text-xs font-bold rounded">
              추천
            </span>
          )}
          {!isRecommended && (
            <span className="inline-block px-2 py-1 bg-gray-700 text-gray-300 text-xs font-medium rounded">
              대안 {index}
            </span>
          )}
        </div>
        <div
          className={`text-sm font-medium ${
            canCatch ? 'text-green-400' : 'text-red-400'
          }`}
        >
          {canCatch ? '탑승 가능' : '탑승 불가'}
        </div>
      </div>

      {/* 출발 마감 시각 */}
      <div className="mb-3 pb-3 border-b border-gray-700">
        <div className="text-gray-400 text-xs mb-1">출발 마감</div>
        <div className="text-white font-bold text-xl">{departureDeadline}</div>
      </div>

      {/* 환승 정보 */}
      {transfers && transfers.length > 0 && (
        <div className="space-y-2">
          <div className="text-gray-400 text-xs mb-2">경로</div>
          {transfers.map((transfer, idx) => (
            <TransferSection key={idx} transfer={transfer} />
          ))}
        </div>
      )}

      {/* 추가 정보 */}
      <div className="mt-3 pt-3 border-t border-gray-700 space-y-2">
        {minutesLeft > 0 && (
          <div className="text-gray-300 text-sm">
            <span className="text-purple-400 font-medium">{minutesLeft}분</span> 내에 탑승 필요
          </div>
        )}
        {message && (
          <div className="text-gray-300 text-sm">{message}</div>
        )}
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
    <div className="bg-gray-900 bg-opacity-50 rounded p-3">
      <div className="flex items-center gap-2 mb-2">
        <span className="text-lg">{typeIcon}</span>
        <span className="text-white font-medium">{line}</span>
        <span className="text-gray-500 text-xs">({typeLabel})</span>
      </div>
      <div className="text-gray-300 text-sm">
        <div>{boardAt}</div>
        <div className="text-gray-500 text-xs my-1">↓</div>
        <div>{alightAt}</div>
      </div>
      {lastBoardTime && (
        <div className="text-gray-400 text-xs mt-2">
          막차 탑승: {lastBoardTime}
        </div>
      )}
    </div>
  )
}
