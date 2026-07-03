import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'
import PlaceSearchModal from '../components/PlaceSearchModal'

/**
 * T-001 + T-002 + T-009 구현: 메인 홈 화면
 *
 * T-001: 메인 홈 레이아웃 구성
 *   - 출발지/도착지 입력
 *   - 막차 조회 버튼 (활성화/비활성화)
 *   - 즐겨찾기 칩
 *   - 최근 검색 (향후 T-011)
 *
 * T-002: GPS 현재 위치 자동 설정
 *   - Geolocation API로 GPS 좌표 조회
 *   - 권한 거부 시 안내 (EC-001)
 *   - 출발지에 "현재 위치 (GPS 자동)" 표시
 *
 * T-009: 즐겨찾기 칩 표시
 *   - GET /api/v1/favorites로 즐겨찾기 목록 조회
 *   - 각 칩(이모지 + 이름)을 탭하면 도착지로 자동 입력 (AC-005)
 *   - 비로그인 상태에서는 섹션 숨김
 *
 * T-011: 최근 검색 localStorage
 *   - 막차 조회 성공 시 "출발지 → 도착지"를 localStorage에 저장 (AC-010)
 *   - 메인 홈에 최근 검색 목록 표시, 최대 5개까지 (FR-008)
 *   - 최근 검색 항목 탭 시 출발지/도착지 자동 입력
 *   - 중복 검색 시 맨 위로 갱신
 *
 * 관련 FR: FR-001, FR-002, FR-003, FR-009, FR-007, FR-008
 * 관련 AC: AC-001, AC-002, AC-006, AC-007, AC-005, AC-010
 */
export default function MainPage() {
  const navigate = useNavigate()

  // ── 상태 관리 ────────────────────────────────────────────────────────────
  // 출발지: {name, lat, lng}
  const [origin, setOrigin] = useState(null)
  // 도착지: {name, lat, lng}
  const [destination, setDestination] = useState(null)
  // GPS 원본 위치 (GPS 복귀용): {name, lat, lng}
  const [gpsLocation, setGpsLocation] = useState(null)
  // GPS 권한 거부 여부
  const [gpsError, setGpsError] = useState('')
  // 로딩 상태
  const [loading, setLoading] = useState(true)
  // 장소 검색 모달 표시 여부
  const [showOriginSearch, setShowOriginSearch] = useState(false)
  const [showDestSearch, setShowDestSearch] = useState(false)
  // API 호출 중 상태
  const [querying, setQuerying] = useState(false)
  // T-012: 막차 조회 에러 상태
  const [queryError, setQueryError] = useState('')

  // ── T-009: 즐겨찾기 관련 상태 ────────────────────────────────────────────
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [favorites, setFavorites] = useState([])
  const [loadingFavorites, setLoadingFavorites] = useState(false)

  // ── T-011: 최근 검색 관련 상태 ──────────────────────────────────────────
  // localStorage에 저장된 최근 검색: [{originName, originLat, originLng, destName, destLat, destLng}, ...]
  const [recentSearches, setRecentSearches] = useState([])

  // localStorage 키
  const RECENT_SEARCHES_KEY = 'recentSearches'

  // ── T-002: GPS 현재 위치 자동 설정 ─────────────────────────────────────
  // (AC-001: 앱이 처음 로드되면 GPS 위치를 요청하고 출발지에 표시)
  useEffect(() => {
    const requestGPS = () => {
      // Geolocation API 지원 여부 확인
      if (!navigator.geolocation) {
        setGpsError('위치 서비스를 사용할 수 없는 환경입니다.')
        setLoading(false)
        return
      }

      navigator.geolocation.getCurrentPosition(
        (position) => {
          // GPS 권한 허용: 좌표를 출발지로 설정
          const { latitude, longitude } = position.coords
          const gpsData = {
            name: '현재 위치 (GPS 자동)',
            lat: latitude,
            lng: longitude,
          }
          setOrigin(gpsData)
          setGpsLocation(gpsData)  // GPS 원본 위치 저장 (복귀용)
          setGpsError('')
          setLoading(false)
        },
        (error) => {
          // GPS 권한 거부 또는 위치 서비스 오류
          // (EC-001: 권한 거부 → 안내 후 수동 입력 유도)
          console.error('GPS 요청 오류:', error.message)
          setOrigin(null)
          setGpsError(
            '위치 권한이 없어요. 출발지를 직접 입력해주세요.'
          )
          setLoading(false)
        }
      )
    }

    requestGPS()
  }, [])

  // ── T-009: 로그인 상태 확인 및 즐겨찾기 목록 조회 ────────────────────────
  // (FR-007: 즐겨찾기 목적지 칩 탭 시 해당 장소가 도착지로 자동 입력)
  useEffect(() => {
    const checkLoginAndLoadFavorites = async () => {
      // ── Step 1: accessToken 존재 여부 확인 ──────────────────────────────────
      const accessToken = localStorage.getItem('accessToken')
      const isLoggedInUser = !!accessToken

      setIsLoggedIn(isLoggedInUser)

      // ── Step 2: accessToken 없으면 API 호출 자체를 하지 않음 ─────────────────
      if (!isLoggedInUser) {
        console.debug('비로그인 상태: 즐겨찾기 API 호출 생략')
        setFavorites([])
        return  // ← API 호출 하지 않고 조기 반환
      }

      // ── Step 3: accessToken 있음 → GET /api/v1/favorites 호출 ────────────────
      console.debug('로그인 상태 확인됨: 즐겨찾기 목록 조회 시작')
      setLoadingFavorites(true)
      try {
        const response = await api.get('/api/v1/favorites')
        // ApiResponse 형식: { code, data: [...] }
        const favoritesList = response.data?.data || []
        console.debug(`즐겨찾기 ${favoritesList.length}개 조회 완료`)
        setFavorites(favoritesList)
      } catch (error) {
        console.error('❌ 즐겨찾기 목록 조회 실패:', {
          status: error.response?.status,
          message: error.message,
        })
        setFavorites([])
      } finally {
        setLoadingFavorites(false)
      }
    }

    checkLoginAndLoadFavorites()
  }, [])

  // ── T-009: 즐겨찾기 칩 클릭 처리 (AC-005) ──────────────────────────────
  // 즐겨찾기 칩을 탭하면 해당 장소가 도착지로 자동 입력
  const handleFavoriteSelect = (favorite) => {
    setDestination({
      name: favorite.name,
      lat: favorite.lat,
      lng: favorite.lng,
    })
  }

  // ── T-011: 최근 검색 localStorage 관리 ────────────────────────────────────
  // 앱 로드 시 최근 검색 읽기
  useEffect(() => {
    const loadRecentSearches = () => {
      try {
        const stored = localStorage.getItem(RECENT_SEARCHES_KEY)
        if (stored) {
          const parsed = JSON.parse(stored)
          setRecentSearches(Array.isArray(parsed) ? parsed : [])
        }
      } catch (error) {
        console.error('최근 검색 로드 실패:', error)
        setRecentSearches([])
      }
    }

    loadRecentSearches()
  }, [])

  // T-011: 최근 검색에 저장 (AC-010)
  const saveRecentSearch = (origin, destination) => {
    const newSearch = {
      originName: origin.name,
      originLat: origin.lat,
      originLng: origin.lng,
      destName: destination.name,
      destLat: destination.lat,
      destLng: destination.lng,
    }

    setRecentSearches((prev) => {
      // 기존 항목 중 동일한 검색이 있는지 확인
      const filtered = prev.filter(
        (search) =>
          !(
            search.originName === newSearch.originName &&
            search.originLat === newSearch.originLat &&
            search.originLng === newSearch.originLng &&
            search.destName === newSearch.destName &&
            search.destLat === newSearch.destLat &&
            search.destLng === newSearch.destLng
          )
      )

      // 새로운 검색을 맨 앞에 추가
      const updated = [newSearch, ...filtered]

      // 최대 5개까지만 유지
      const limited = updated.slice(0, 5)

      // localStorage에 저장
      try {
        localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(limited))
      } catch (error) {
        console.error('최근 검색 저장 실패:', error)
      }

      return limited
    })
  }

  // T-011: 최근 검색 항목 탭 처리
  const handleRecentSearchSelect = (search) => {
    setOrigin({
      name: search.originName,
      lat: search.originLat,
      lng: search.originLng,
    })
    setDestination({
      name: search.destName,
      lat: search.destLat,
      lng: search.destLng,
    })
  }

  // T-011: 최근 검색 항목 삭제 처리
  const handleRemoveRecentSearch = (indexToRemove) => {
    setRecentSearches((prev) => {
      // 해당 인덱스의 항목 제거
      const updated = prev.filter((_, index) => index !== indexToRemove)

      // localStorage 업데이트
      try {
        localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(updated))
      } catch (error) {
        console.error('최근 검색 삭제 저장 실패:', error)
      }

      return updated
    })
  }

  // ── T-001: 출발지 검색 선택 처리 ───────────────────────────────────────
  // (FR-002, FR-006: 출발지 직접 검색 선택 시 GPS 자동 위치 대신 선택한 장소로 교체)
  const handleOriginSelect = (place) => {
    setOrigin({
      name: place.name,
      lat: place.lat,
      lng: place.lng,
    })
    setGpsError('')
    setShowOriginSearch(false)
  }

  // ── T-001: 도착지 검색 선택 처리 ───────────────────────────────────────
  // (FR-003: 도착지 입력창 탭 시 장소 검색으로 전환)
  const handleDestSelect = (place) => {
    setDestination({
      name: place.name,
      lat: place.lat,
      lng: place.lng,
    })
    setShowDestSearch(false)
  }

  // ── T-001: 출발지 변경 버튼 ────────────────────────────────────────────
  // (AC-002: 출발지 변경 버튼 탭 → 장소 검색 화면으로 전환)
  const handleChangeOrigin = () => {
    setShowOriginSearch(true)
  }

  // ── GPS 위치로 복귀 버튼 ─────────────────────────────────────────────────
  // (출발지를 직접 검색으로 변경한 후, 다시 GPS 자동으로 돌아가기)
  const handleBackToGPS = () => {
    if (gpsLocation) {
      setOrigin(gpsLocation)
    }
  }

  // ── GPS 복귀 버튼 표시 여부 판단 ─────────────────────────────────────────
  // (출발지가 GPS 자동이 아닐 때만 표시)
  const isOriginChangedFromGPS =
    origin &&
    gpsLocation &&
    origin.name !== gpsLocation.name &&
    origin.name !== '현재 위치 (GPS 자동)'

  // ── T-001: 막차 조회 버튼 클릭 ─────────────────────────────────────────
  // (AC-007: 막차 조회 버튼 탭 → GET /api/v1/last-train 호출)
  // T-012: EC-003, EC-004 에러 분기 처리
  const handleQueryLastTrain = async () => {
    // EC-007: 출발지와 도착지가 동일한 경우 검사
    if (
      origin &&
      destination &&
      origin.lat === destination.lat &&
      origin.lng === destination.lng
    ) {
      setQueryError('출발지와 도착지가 같아요')
      return
    }

    setQuerying(true)
    setQueryError('')

    try {
      // ── GET 쿼리 파라미터 방식으로 변경 ──────────────────────────────────
      // URL 인코딩된 쿼리 스트링 생성
      const queryParams = new URLSearchParams({
        originLat: origin.lat,
        originLng: origin.lng,
        originName: origin.name,
        destLat: destination.lat,
        destLng: destination.lng,
        destName: destination.name,
      }).toString()

      const response = await fetch(`/api/v1/last-train?${queryParams}`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
        },
      })

      // ── T-012: 에러 분기 처리 ────────────────────────────────────────────
      if (!response.ok) {
        // EC-003: 막차 종료 (HTTP 404)
        if (response.status === 404) {
          setQueryError('오늘 막차는 종료됐어요')
          return
        }

        // EC-004: API 오류 (503, 500 등)
        if (response.status >= 500) {
          setQueryError('잠시 후 다시 시도해주세요')
          console.error('서버 오류:', response.status)
          return
        }

        // 기타 오류 (400 등)
        setQueryError('막차 조회에 실패했어요. 다시 시도해주세요.')
        return
      }

      const data = await response.json()

      // AC-010: 조회 성공 시 최근 검색에 저장 (T-011)
      saveRecentSearch(origin, destination)

      // 결과 화면으로 이동 (T-008)
      // destination 객체도 함께 전달 (즐겨찾기 등록 시 필요)
      navigate('/result', { state: { result: data, destination } })
    } catch (error) {
      // EC-004: 네트워크 오류 등
      console.error('막차 조회 오류:', error)
      setQueryError('잠시 후 다시 시도해주세요')
    } finally {
      setQuerying(false)
    }
  }

  // ── AC-006: 조회 버튼 활성화 여부 판단 ─────────────────────────────────
  // (출발지 + 도착지 모두 입력된 상태에서만 활성화)
  const isQueryable = origin && destination

  return (
    <div className="min-h-screen bg-[#1a1a2e] flex flex-col">
      {/* 헤더 */}
      <header className="bg-[#1a1a2e] border-b border-gray-700 px-4 py-6 sticky top-0 z-10">
        <h1 className="text-2xl font-bold text-white">막차알리미 🚂</h1>
        <p className="text-gray-400 text-sm mt-1">오늘 막차 놓치지 마세요</p>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="flex-1 px-4 py-6 overflow-y-auto">
        {/* 로딩 상태 */}
        {loading && (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border border-gray-700 border-t-purple-500"></div>
            <span className="text-gray-400 ml-3">위치 정보를 불러오는 중...</span>
          </div>
        )}

        {!loading && (
          <>
            {/* 출발지 섹션 */}
            <div className="mb-6">
              <label className="block text-white text-sm font-medium mb-2">
                출발지
              </label>
              <div className="flex items-center gap-2">
                <div className="flex-1 px-4 py-3 bg-gray-800 rounded border border-gray-700">
                  <div className="text-white font-medium text-sm">
                    {origin?.name || '출발지 미설정'}
                  </div>
                  {origin && (
                    <div className="text-gray-400 text-xs mt-1">
                      {origin.lat.toFixed(4)}, {origin.lng.toFixed(4)}
                    </div>
                  )}
                </div>

                {/* GPS 복귀 버튼 (출발지가 GPS 자동이 아닐 때만 표시) */}
                {isOriginChangedFromGPS && (
                  <button
                    onClick={handleBackToGPS}
                    className="px-4 py-3 bg-blue-700 hover:bg-blue-600 text-white font-medium rounded transition whitespace-nowrap text-sm"
                    title="현재 위치(GPS)로 복귀"
                  >
                    📍 현재 위치로
                  </button>
                )}

                {/* 변경 버튼 */}
                <button
                  onClick={handleChangeOrigin}
                  className="px-4 py-3 bg-gray-700 hover:bg-gray-600 text-white font-medium rounded transition whitespace-nowrap"
                >
                  변경
                </button>
              </div>

              {/* GPS 권한 거부 안내 (EC-001) */}
              {gpsError && (
                <div className="mt-2 text-sm text-yellow-400 bg-yellow-500 bg-opacity-10 px-3 py-2 rounded border border-yellow-500 border-opacity-30">
                  {gpsError}
                </div>
              )}
            </div>

            {/* 도착지 섹션 */}
            <div className="mb-8">
              <label className="block text-white text-sm font-medium mb-2">
                도착지
              </label>
              <button
                onClick={() => setShowDestSearch(true)}
                className="w-full px-4 py-3 bg-gray-800 hover:bg-gray-700 rounded border border-gray-700 hover:border-gray-600 transition text-left"
              >
                <div className="text-white font-medium text-sm">
                  {destination?.name || '도착지를 입력하세요'}
                </div>
                {destination && (
                  <div className="text-gray-400 text-xs mt-1">
                    {destination.lat.toFixed(4)}, {destination.lng.toFixed(4)}
                  </div>
                )}
              </button>
            </div>

            {/* T-012: 막차 조회 에러 배너 (EC-003, EC-004) */}
            {queryError && (
              <div className="mb-6 text-sm px-4 py-3 rounded border">
                {queryError === '오늘 막차는 종료됐어요' ? (
                  // EC-003: 막차 종료 - 주황색 배너
                  <div className="text-yellow-400 bg-yellow-500 bg-opacity-10 border-yellow-500 border-opacity-30">
                    ⏰ {queryError}
                  </div>
                ) : (
                  // EC-004: API 오류 - 빨간색 배너
                  <div className="text-red-400 bg-red-500 bg-opacity-10 border-red-500 border-opacity-30">
                    ❌ {queryError}
                  </div>
                )}
              </div>
            )}

            {/* 즐겨찾기 칩 섹션 (T-009: FR-007, AC-005) */}
            {isLoggedIn && (
              <div className="mb-8">
                <label className="block text-white text-sm font-medium mb-3">
                  즐겨찾기 목적지
                </label>

                {/* 로딩 중 */}
                {loadingFavorites && (
                  <div className="flex items-center justify-center py-6">
                    <div className="animate-spin rounded-full h-6 w-6 border border-gray-700 border-t-purple-500"></div>
                    <span className="text-gray-400 ml-2 text-sm">
                      즐겨찾기를 불러오는 중...
                    </span>
                  </div>
                )}

                {/* 즐겨찾기 목록 */}
                {!loadingFavorites && favorites.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {favorites.map((favorite) => (
                      <button
                        key={favorite.id}
                        onClick={() => handleFavoriteSelect(favorite)}
                        className="px-4 py-2 bg-purple-900 bg-opacity-30 hover:bg-opacity-50 text-white rounded-full border border-purple-600 transition flex items-center gap-2 text-sm"
                      >
                        <span className="text-lg">{favorite.emoji}</span>
                        <span>{favorite.name}</span>
                      </button>
                    ))}
                  </div>
                )}

                {/* 즐겨찾기 없음 */}
                {!loadingFavorites && favorites.length === 0 && (
                  <p className="text-gray-500 text-sm">
                    즐겨찾기한 목적지가 없어요
                  </p>
                )}
              </div>
            )}

            {/* 최근 검색 섹션 (T-011: FR-008, AC-010) */}
            {recentSearches.length > 0 && (
              <div className="mb-8">
                <label className="block text-white text-sm font-medium mb-3">
                  최근 검색
                </label>
                <div className="space-y-2">
                  {recentSearches.map((search, index) => (
                    <div
                      key={index}
                      className="flex items-center gap-2 group"
                    >
                      <button
                        onClick={() => handleRecentSearchSelect(search)}
                        className="flex-1 text-left px-4 py-3 bg-gray-800 hover:bg-gray-700 rounded border border-gray-700 hover:border-gray-600 transition"
                      >
                        <div className="text-white font-medium text-sm">
                          {search.originName}
                          <span className="text-purple-400 mx-2">→</span>
                          {search.destName}
                        </div>
                      </button>
                      {/* 삭제 버튼 */}
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          handleRemoveRecentSearch(index)
                        }}
                        className="px-3 py-3 bg-gray-700 hover:bg-red-600 text-gray-300 hover:text-white rounded border border-gray-700 hover:border-red-600 transition"
                        title="이 항목 삭제"
                      >
                        ✕
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* 막차 조회 버튼 */}
            {/* (AC-006: 출발지 + 도착지 모두 입력 시만 활성화) */}
            <button
              onClick={handleQueryLastTrain}
              disabled={!isQueryable || querying}
              className={`w-full py-4 font-bold rounded-lg transition text-white text-lg ${
                isQueryable && !querying
                  ? 'bg-purple-600 hover:bg-purple-700 cursor-pointer'
                  : 'bg-gray-600 cursor-not-allowed'
              }`}
            >
              {querying ? '조회 중...' : '막차 조회'}
            </button>

            {/* 조회 불가 안내 */}
            {!isQueryable && !loading && (
              <p className="text-center text-gray-400 text-sm mt-3">
                출발지와 도착지를 모두 입력해주세요
              </p>
            )}
          </>
        )}
      </main>

      {/* 출발지 검색 모달 (T-001: FR-002, AC-002) */}
      {showOriginSearch && (
        <PlaceSearchModal
          mode="origin"
          onSelect={handleOriginSelect}
          onClose={() => setShowOriginSearch(false)}
        />
      )}

      {/* 도착지 검색 모달 (T-001: FR-003, AC-002) */}
      {showDestSearch && (
        <PlaceSearchModal
          mode="destination"
          onSelect={handleDestSelect}
          onClose={() => setShowDestSearch(false)}
        />
      )}
    </div>
  )
}
