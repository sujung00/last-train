import { useState, useEffect, useRef } from 'react'

/**
 * T-001: 장소 검색 공통 컴포넌트
 *
 * 출발지/도착지 검색에서 재사용되는 모달 컴포넌트.
 * 카카오 Local API를 호출해 장소 검색 결과를 표시합니다.
 *
 * 요구사항 (FR):
 *   FR-004: 카카오 Local API 호출해 결과를 실시간으로 표시
 *   FR-005: 출발지/도착지 검색에 동일한 컴포넌트 재사용
 *
 * Props:
 *   mode: "origin" | "destination" (출발지/도착지 구분)
 *   onSelect: (place) => void (선택 결과: {name, lat, lng})
 *   onClose: () => void (모달 닫기)
 */
export default function PlaceSearchModal({ mode, onSelect, onClose }) {
  const [searchText, setSearchText] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)

  // 디바운스 타이머 ID (300ms)
  const debounceTimerId = useRef(null)

  // 카카오 API 키 (환경변수에서 로드)
  const kakaoApiKey = import.meta.env.VITE_KAKAO_REST_API_KEY

  // ✅ 에러 상태: 초기값 계산 함수로 이동 (setState in effect 제거)
  const [error, setError] = useState(() => {
    if (!kakaoApiKey) {
      console.error(
        '❌ VITE_KAKAO_REST_API_KEY 환경변수가 설정되지 않았습니다.',
        'frontend/.env.local 파일을 확인해주세요.'
      )
      return '설정 오류: 카카오 API 키가 설정되지 않았어요.'
    }
    return ''
  })

  /**
   * 검색 입력 시: 300ms 디바운스 적용
   * (비기능 요구사항: FR-004 관련)
   */
  const handleSearch = (text) => {
    setSearchText(text)
    setError('')

    // 기존 타이머 취소
    if (debounceTimerId.current) {
      clearTimeout(debounceTimerId.current)
    }

    // 입력이 비어있으면 결과 초기화
    if (!text.trim()) {
      setResults([])
      setLoading(false)
      return
    }

    // API KEY 미설정 검사
    if (!kakaoApiKey) {
      setError('카카오 API가 설정되지 않았어요. 관리자에게 문의해주세요.')
      setLoading(false)
      return
    }

    // 300ms 후 API 호출
    setLoading(true)
    debounceTimerId.current = setTimeout(async () => {
      try {
        // ── 로그: Authorization 헤더 형식 확인 (개발 환경) ──────────────────────
        console.debug(
          '🔐 카카오 API 호출 시작:',
          `Authorization: KakaoAK ${kakaoApiKey.substring(0, 8)}...`
        )

        const response = await fetch(
          `https://dapi.kakao.com/v2/local/search/keyword.json?query=${encodeURIComponent(
            text
          )}&size=10`,
          {
            headers: {
              Authorization: `KakaoAK ${kakaoApiKey}`,
            },
          }
        )

        // ── 상태 코드별 상세 에러 처리 ────────────────────────────────────────
        if (!response.ok) {
          const errorDetails = await response.text().catch(() => '')
          console.error(
            `❌ 카카오 API 오류 [${response.status}]:`,
            errorDetails || response.statusText
          )

          if (response.status === 401) {
            throw new Error('API 키 인증 실패: VITE_KAKAO_REST_API_KEY를 확인해주세요.')
          } else if (response.status === 403) {
            throw new Error('API 접근 권한 없음: 카카오 개발자 콘솔에서 설정을 확인해주세요.')
          } else if (response.status === 429) {
            throw new Error('요청 제한: 잠시 후 다시 시도해주세요.')
          } else {
            throw new Error(`카카오 API 오류 (${response.status})`)
          }
        }

        // ── 성공 로그 ──────────────────────────────────────────────────────────
        console.debug('✅ 카카오 API 호출 성공')

        const data = await response.json()

        if (data.documents && data.documents.length > 0) {
          // 검색 결과를 우리 포맷으로 변환
          const places = data.documents.map((doc) => ({
            name: doc.place_name,
            lat: parseFloat(doc.y),
            lng: parseFloat(doc.x),
            address: doc.road_address_name || doc.address_name,
            phone: doc.phone || '',
            category: doc.category_name || '',
          }))
          setResults(places)
        } else {
          setResults([])
        }
        setError('')
      } catch (err) {
        // ── 에러 로깅 ────────────────────────────────────────────────────────
        console.error('❌ 장소 검색 오류:', {
          message: err.message,
          stack: err.stack,
        })
        setError(err.message || '검색 중 오류가 발생했어요. 다시 시도해주세요.')
        setResults([])
      } finally {
        setLoading(false)
      }
    }, 300)
  }

  /**
   * 검색 결과 선택 시: onSelect 콜백만 호출
   * (FavoritePage에서 상태 관리 - onClose는 X 버튼 클릭시만)
   */
  const handleSelectPlace = (place) => {
    onSelect({
      name: place.name,
      lat: place.lat,
      lng: place.lng,
      address: place.address || '',
    })
    // onClose는 여기서 호출하지 않음 - FavoritePage의 handleSelectPlace에서 상태 관리
  }

  // 언마운트 시 디바운스 타이머 정리
  useEffect(() => {
    return () => {
      if (debounceTimerId.current) {
        clearTimeout(debounceTimerId.current)
      }
    }
  }, [])

  const title = mode === 'origin' ? '출발지 검색' : '도착지 검색'

  return (
    <div className="phone-modal-backdrop">
      {/* 모달 콘텐츠 (430px 기준으로 중앙 정렬) */}
      <div className="phone-modal-content">
        {/* 헤더 */}
        <div className="phone-modal-header">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-gray-900 text-lg font-bold">{title}</h2>
            <button
              onClick={onClose}
              className="text-gray-500 hover:text-gray-700 transition text-2xl"
            >
              ✕
            </button>
          </div>

          {/* 검색 입력 */}
          <div>
            <input
              type="text"
              placeholder="장소명 또는 주소 입력"
              value={searchText}
              onChange={(e) => handleSearch(e.target.value)}
              autoFocus
              className="w-full px-4 py-3 bg-gray-50 text-gray-900 border border-gray-200 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition placeholder-gray-400"
            />
          </div>
        </div>

        {/* 결과 영역 */}
        <div className="phone-modal-body">
          {/* 로딩 상태 */}
          {loading && (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-2 border-gray-300 border-t-gray-900"></div>
            </div>
          )}

          {/* 에러 메시지 */}
          {error && !loading && (
            <div className="bg-red-50 border border-red-200 text-red-900 px-4 py-3 rounded">
              {error}
            </div>
          )}

          {/* 검색 결과 없음 */}
          {!loading && !error && searchText.trim() && results.length === 0 && (
            <div className="text-center py-8">
              <p className="text-gray-500 text-sm">검색 결과가 없습니다</p>
            </div>
          )}

          {/* 검색 결과 목록 */}
          {!loading && results.length > 0 && (
            <div className="space-y-2">
              {results.map((place, index) => (
                <button
                  key={index}
                  onClick={() => handleSelectPlace(place)}
                  className="w-full text-left px-4 py-4 bg-gray-50 hover:bg-gray-100 rounded transition border border-gray-200 hover:border-gray-300"
                >
                  <div className="text-gray-900 font-medium text-sm">{place.name}</div>
                  <div className="text-gray-500 text-xs mt-1">{place.address}</div>
                  {place.phone && (
                    <div className="text-gray-400 text-xs mt-1">{place.phone}</div>
                  )}
                </button>
              ))}
            </div>
          )}

          {/* 초기 상태 (검색 전) */}
          {!loading && !error && !searchText.trim() && results.length === 0 && (
            <div className="text-center py-12">
              <p className="text-gray-500 text-sm">
                위의 검색창에 장소명이나 주소를 입력하세요
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
