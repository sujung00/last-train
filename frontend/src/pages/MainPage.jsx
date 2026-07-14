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

  // T-011: 최근 검색 항목 탭 처리 (API 호출 추가)
  const handleRecentSearchSelect = async (search) => {
    const origin = {
      name: search.originName,
      lat: search.originLat,
      lng: search.originLng,
    }
    const destination = {
      name: search.destName,
      lat: search.destLat,
      lng: search.destLng,
    }

    setOrigin(origin)
    setDestination(destination)

    // lat, lng이 모두 있을 때만 막차 조회 API 호출
    if (
      origin.lat !== undefined &&
      origin.lng !== undefined &&
      destination.lat !== undefined &&
      destination.lng !== undefined
    ) {
      setTimeout(() => {
        handleQueryLastTrain(origin, destination)
      }, 100)
    }
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
  const handleQueryLastTrain = async (originParam = null, destParam = null) => {
    const queryOrigin = originParam || origin
    const queryDest = destParam || destination

    // EC-007: 출발지와 도착지가 동일한 경우 검사
    if (
      queryOrigin &&
      queryDest &&
      queryOrigin.lat === queryDest.lat &&
      queryOrigin.lng === queryDest.lng
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
        originLat: queryOrigin.lat,
        originLng: queryOrigin.lng,
        originName: queryOrigin.name,
        destLat: queryDest.lat,
        destLng: queryDest.lng,
        destName: queryDest.name,
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
      saveRecentSearch(queryOrigin, queryDest)

      // 결과 화면으로 이동 (T-008)
      // destination 객체도 함께 전달 (즐겨찾기 등록 시 필요)
      navigate('/result', { state: { result: data, destination: queryDest } })
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
    <div className="h-full bg-white flex flex-col">
      {/* 헤더 + 입력 영역 + 버튼 통합 섹션 */}
      <div className="bg-white border-b border-gray-200 pb-4">
        {/* 헤더 */}
        <header className="px-4 py-6">
          <h1 className="text-3xl font-bold text-gray-900">막차알리미</h1>
          <p className="text-gray-500 text-sm mt-2">마지막 한 대를 놓치지 마세요</p>
        </header>

        {/* 로딩 상태 */}
        {loading && (
          <div className="flex items-center justify-center py-8 px-4">
            <div className="animate-spin rounded-full h-6 w-6 border-2 border-gray-300 border-t-gray-900"></div>
            <span className="text-gray-600 ml-3 text-sm">위치 정보를 불러오는 중...</span>
          </div>
        )}

        {/* 입력 카드 + 버튼 (로딩 안 될 때만) */}
        {!loading && (
          <div className="px-4">
              {/* 출발지/도착지 통합 카드 */}
              <div className="mb-6 bg-gray-50 rounded-lg border border-gray-200 overflow-hidden">
              {/* 출발지 섹션 */}
              <div className="p-4">
                <div className="text-gray-600 text-xs font-medium mb-3 uppercase tracking-wide">출발지</div>
                <button
                  onClick={handleChangeOrigin}
                  className="w-full text-left"
                >
                  <div className="font-semibold text-base text-gray-900">
                    {origin?.name === '현재 위치 (GPS 자동)' ? (
                      '현재 위치'
                    ) : (
                      origin?.name || '위치를 선택하세요'
                    )}
                  </div>
                  {origin && origin.name === '현재 위치 (GPS 자동)' && (
                    <div className="text-gray-500 text-xs mt-1">GPS 기반</div>
                  )}
                </button>

                {/* GPS 복귀 버튼 (출발지가 GPS 자동이 아닐 때만 표시) */}
                {isOriginChangedFromGPS && (
                  <button
                    onClick={handleBackToGPS}
                    className="mt-3 px-3 py-2 bg-blue-50 hover:bg-blue-100 text-blue-600 font-medium rounded text-xs transition w-full border border-blue-200"
                    title="현재 위치(GPS)로 복귀"
                  >
                    현재 위치로 복귀
                  </button>
                )}

                {/* GPS 권한 거부 안내 (EC-001) */}
                {gpsError && (
                  <div className="mt-3 text-xs text-amber-800 bg-amber-50 px-3 py-2 rounded border border-amber-200">
                    {gpsError}
                  </div>
                )}
              </div>

              {/* 구분선 */}
              <div className="h-px bg-gray-200"></div>

              {/* 도착지 섹션 */}
              <div className="p-4">
                <div className="text-gray-600 text-xs font-medium mb-3 uppercase tracking-wide">도착지</div>
                <button
                  onClick={() => setShowDestSearch(true)}
                  className="w-full text-left"
                >
                  <div className="font-semibold text-base text-gray-900">
                    {destination?.name ? (
                      destination.name
                    ) : (
                      <span className="text-gray-400">위치를 선택하세요</span>
                    )}
                  </div>
                </button>
              </div>
            </div>

              {/* 막차 조회 버튼 */}
              {/* (AC-006: 출발지 + 도착지 모두 입력 시만 활성화) */}
              <button
                onClick={() => handleQueryLastTrain()}
                disabled={!isQueryable || querying}
                className={`w-full py-3 font-semibold rounded-lg transition text-base ${
                  isQueryable && !querying
                    ? 'bg-gray-900 text-white hover:bg-black cursor-pointer'
                    : 'bg-gray-300 text-gray-500 cursor-not-allowed'
                }`}
              >
                {querying ? '조회 중...' : '막차 조회'}
              </button>
            </div>
        )}
      </div>

      {/* 메인 콘텐츠 영역 */}
      <main className="flex-1 px-4 py-6 overflow-y-auto bg-white">
        {/* T-012: 막차 조회 에러 배너 (EC-003, EC-004) */}
        {queryError && (
          <div className="mb-6 text-sm px-4 py-3 rounded">
            {queryError === '오늘 막차는 종료됐어요' ? (
              // EC-003: 막차 종료 - 경고(주황색) 배너
              <div className="text-amber-900 bg-amber-50 border border-amber-200 rounded">
                {queryError}
              </div>
            ) : (
              // EC-004: API 오류 - 에러(빨강색) 배너
              <div className="text-red-900 bg-red-50 border border-red-200 rounded">
                {queryError}
              </div>
            )}
          </div>
        )}

        {/* 즐겨찾기 칩 섹션 (T-009: FR-007, AC-005) */}
        {isLoggedIn && (
          <div className="mb-8">
            <h2 className="block text-gray-900 text-sm font-semibold mb-4 uppercase tracking-wide">
              즐겨찾기
            </h2>

            {/* 로딩 중 */}
            {loadingFavorites && (
              <div className="flex items-center justify-center py-6">
                <div className="animate-spin rounded-full h-5 w-5 border-2 border-gray-300 border-t-gray-900"></div>
                <span className="text-gray-500 ml-2 text-sm">
                  불러오는 중...
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
                    className="px-4 py-2 bg-gray-100 text-gray-900 rounded-full hover:bg-gray-200 transition flex items-center gap-2 text-sm font-medium border border-gray-300"
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
                아직 즐겨찾기한 목적지가 없어요
              </p>
            )}
          </div>
        )}

        {/* 최근 검색 섹션 (T-011: FR-008, AC-010) */}
        {recentSearches.length > 0 && (
          <div className="mb-8">
            <h2 className="block text-gray-900 text-sm font-semibold mb-4 uppercase tracking-wide">
              최근 검색
            </h2>
            <div>
              {recentSearches.map((search, index) => (
                <div
                  key={index}
                  onClick={() => handleRecentSearchSelect(search)}
                  className="w-full flex items-center justify-between py-3 px-0 border-b border-gray-200 hover:bg-gray-50 transition text-left cursor-pointer"
                >
                  <div className="text-gray-900 font-medium text-sm flex-1 min-w-0">
                    <span className="text-gray-600">{search.originName}</span>
                    <span className="text-gray-400 mx-2">→</span>
                    <span>{search.destName}</span>
                  </div>
                  {/* 삭제 버튼 */}
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      handleRemoveRecentSearch(index)
                    }}
                    className="ml-2 p-2 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded transition flex-shrink-0"
                    title="이 항목 삭제"
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          </div>
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
