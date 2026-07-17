import { useState, useEffect, useRef } from 'react'

/**
 * 4b 리디자인: 박스형 카드 대신 구분선 목록
 * 로직은 기존 PlaceSearchModal.jsx와 동일. 마크업만 교체.
 */
export default function PlaceSearchModal({ mode, onSelect, onClose }) {
  const [searchText, setSearchText] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const debounceTimerId = useRef(null)
  const kakaoApiKey = import.meta.env.VITE_KAKAO_REST_API_KEY

  const [error, setError] = useState(() => {
    if (!kakaoApiKey) return '설정 오류: 카카오 API 키가 설정되지 않았어요.'
    return ''
  })

  const handleSearch = (text) => {
    setSearchText(text)
    setError('')
    if (debounceTimerId.current) clearTimeout(debounceTimerId.current)
    if (!text.trim()) { setResults([]); setLoading(false); return }
    if (!kakaoApiKey) { setError('카카오 API가 설정되지 않았어요. 관리자에게 문의해주세요.'); setLoading(false); return }
    setLoading(true)
    debounceTimerId.current = setTimeout(async () => {
      try {
        const response = await fetch(
          `https://dapi.kakao.com/v2/local/search/keyword.json?query=${encodeURIComponent(text)}&size=10`,
          { headers: { Authorization: `KakaoAK ${kakaoApiKey}` } }
        )
        if (!response.ok) {
          if (response.status === 401) throw new Error('API 키 인증 실패: VITE_KAKAO_REST_API_KEY를 확인해주세요.')
          if (response.status === 403) throw new Error('API 접근 권한 없음: 카카오 개발자 콘솔에서 설정을 확인해주세요.')
          if (response.status === 429) throw new Error('요청 제한: 잠시 후 다시 시도해주세요.')
          throw new Error(`카카오 API 오류 (${response.status})`)
        }
        const data = await response.json()
        if (data.documents && data.documents.length > 0) {
          setResults(data.documents.map((doc) => ({
            name: doc.place_name, lat: parseFloat(doc.y), lng: parseFloat(doc.x),
            address: doc.road_address_name || doc.address_name, phone: doc.phone || '', category: doc.category_name || '',
          })))
        } else {
          setResults([])
        }
        setError('')
      } catch (err) {
        setError(err.message || '검색 중 오류가 발생했어요. 다시 시도해주세요.')
        setResults([])
      } finally {
        setLoading(false)
      }
    }, 300)
  }

  const handleSelectPlace = (place) => {
    onSelect({ name: place.name, lat: place.lat, lng: place.lng, address: place.address || '' })
  }

  useEffect(() => () => { if (debounceTimerId.current) clearTimeout(debounceTimerId.current) }, [])

  const title = mode === 'origin' ? '출발지 검색' : '도착지 검색'

  return (
    <div className="phone-modal-backdrop">
      <div className="phone-modal-content" style={{ borderRadius: '16px 16px 0 0' }}>
        <div className="w-8 h-[3px] bg-gray-200 rounded-full mx-auto mt-2.5" />
        <div className="px-4 pt-3.5 pb-3 border-b border-gray-100">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-gray-900 text-base font-bold">{title}</h2>
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition text-base">✕</button>
          </div>
          <input
            type="text" placeholder="장소명 또는 주소 입력" value={searchText}
            onChange={(e) => handleSearch(e.target.value)} autoFocus
            className="w-full px-3.5 py-2.5 bg-gray-100 text-gray-900 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 transition placeholder-gray-400 text-sm"
          />
        </div>

        <div className="px-4 flex-1 overflow-y-auto">
          {loading && (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin rounded-full h-7 w-7 border-2 border-gray-300 border-t-gray-900"></div>
            </div>
          )}
          {error && !loading && (
            <div className="my-3 bg-red-50 border border-red-200 text-red-900 px-4 py-3 rounded text-sm">{error}</div>
          )}
          {!loading && !error && searchText.trim() && results.length === 0 && (
            <div className="text-center py-8"><p className="text-gray-500 text-sm">검색 결과가 없습니다</p></div>
          )}
          {!loading && results.length > 0 && (
            <div>
              {results.map((place, index) => (
                <button key={index} onClick={() => handleSelectPlace(place)} className="w-full text-left py-3.5 border-b border-gray-100 flex items-start gap-2.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-gray-900 mt-1.5 flex-shrink-0" />
                  <span className="flex-1 min-w-0">
                    <span className="block text-gray-900 font-semibold text-sm">{place.name}</span>
                    <span className="block text-gray-500 text-xs mt-0.5 truncate">{place.address}</span>
                  </span>
                </button>
              ))}
            </div>
          )}
          {!loading && !error && !searchText.trim() && results.length === 0 && (
            <div className="text-center py-12"><p className="text-gray-500 text-sm">위의 검색창에 장소명이나 주소를 입력하세요</p></div>
          )}
        </div>
      </div>
    </div>
  )
}
