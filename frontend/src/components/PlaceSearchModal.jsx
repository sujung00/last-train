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
  const [error, setError] = useState('')

  // 디바운스 타이머 ID (300ms)
  const debounceTimerId = useRef(null)

  // 카카오 API 키 (환경변수에서 로드)
  const kakaoApiKey = import.meta.env.VITE_KAKAO_API_KEY

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

    // 300ms 후 API 호출
    setLoading(true)
    debounceTimerId.current = setTimeout(async () => {
      try {
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

        if (!response.ok) {
          throw new Error('카카오 API 호출 실패')
        }

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
        console.error('장소 검색 오류:', err)
        setError('검색 중 오류가 발생했어요. 다시 시도해주세요.')
        setResults([])
      } finally {
        setLoading(false)
      }
    }, 300)
  }

  /**
   * 검색 결과 선택 시: 콜백 호출 후 모달 닫기
   * (AC-004: 선택한 장소를 설정하고 메인 홈으로)
   */
  const handleSelectPlace = (place) => {
    onSelect({
      name: place.name,
      lat: place.lat,
      lng: place.lng,
    })
    onClose()
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
    <div className="fixed inset-0 z-50 bg-black bg-opacity-50 flex items-end">
      {/* 모달 콘텐츠 */}
      <div className="w-full bg-[#1a1a2e] rounded-t-lg max-h-[90vh] overflow-y-auto">
        {/* 헤더 */}
        <div className="sticky top-0 bg-[#1a1a2e] border-b border-gray-700 px-4 py-4">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-white text-lg font-bold">{title}</h2>
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-200 transition text-2xl"
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
              className="w-full px-4 py-3 bg-gray-700 text-white rounded focus:outline-none focus:bg-gray-600 transition placeholder-gray-400"
            />
          </div>
        </div>

        {/* 결과 영역 */}
        <div className="px-4 py-4">
          {/* 로딩 상태 */}
          {loading && (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border border-gray-700 border-t-purple-500"></div>
            </div>
          )}

          {/* 에러 메시지 */}
          {error && !loading && (
            <div className="bg-red-500 bg-opacity-20 border border-red-500 text-red-200 px-4 py-3 rounded">
              {error}
            </div>
          )}

          {/* 검색 결과 없음 */}
          {!loading && !error && searchText.trim() && results.length === 0 && (
            <div className="text-center py-8">
              <p className="text-gray-400 text-sm">검색 결과가 없어요</p>
            </div>
          )}

          {/* 검색 결과 목록 */}
          {!loading && results.length > 0 && (
            <div className="space-y-2">
              {results.map((place, index) => (
                <button
                  key={index}
                  onClick={() => handleSelectPlace(place)}
                  className="w-full text-left px-4 py-4 bg-gray-800 hover:bg-gray-700 rounded transition border border-gray-700 hover:border-gray-600"
                >
                  <div className="text-white font-medium text-sm">{place.name}</div>
                  <div className="text-gray-400 text-xs mt-1">{place.address}</div>
                  {place.phone && (
                    <div className="text-gray-500 text-xs mt-1">{place.phone}</div>
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
