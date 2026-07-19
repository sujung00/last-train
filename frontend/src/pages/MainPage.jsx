import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'
import PlaceSearchModal from '../components/PlaceSearchModal'
import MapView from '../components/MapView'

/**
 * 1c 리디자인: 지도 우선 레이아웃 + 하단 바텀시트
 * 로직(state/handlers)은 기존 MainPage.jsx와 동일. 마크업만 교체.
 */
export default function MainPage() {
  const navigate = useNavigate()

  const [origin, setOrigin] = useState(null)
  const [destination, setDestination] = useState(null)
  const [gpsLocation, setGpsLocation] = useState(null)
  const [gpsError, setGpsError] = useState('')
  const [loading, setLoading] = useState(true)
  const [showOriginSearch, setShowOriginSearch] = useState(false)
  const [showDestSearch, setShowDestSearch] = useState(false)
  const [querying, setQuerying] = useState(false)
  const [queryError, setQueryError] = useState('')

  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [favorites, setFavorites] = useState([])
  const [loadingFavorites, setLoadingFavorites] = useState(false)

  const [recentSearches, setRecentSearches] = useState([])
  const RECENT_SEARCHES_KEY = 'recentSearches'

  useEffect(() => {
    const requestGPS = () => {
      if (!navigator.geolocation) {
        setGpsError('위치 서비스를 사용할 수 없는 환경입니다.')
        setLoading(false)
        return
      }
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const { latitude, longitude } = position.coords
          const gpsData = { name: '현재 위치 (GPS 자동)', lat: latitude, lng: longitude }
          setOrigin(gpsData)
          setGpsLocation(gpsData)
          setGpsError('')
          setLoading(false)
        },
        (error) => {
          console.error('GPS 요청 오류:', error.message)
          setOrigin(null)
          setGpsError('위치 권한이 없어요. 출발지를 직접 입력해주세요.')
          setLoading(false)
        }
      )
    }
    requestGPS()
  }, [])

  useEffect(() => {
    const checkLoginAndLoadFavorites = async () => {
      const accessToken = localStorage.getItem('accessToken')
      const isLoggedInUser = !!accessToken
      setIsLoggedIn(isLoggedInUser)
      if (!isLoggedInUser) {
        setFavorites([])
        return
      }
      setLoadingFavorites(true)
      try {
        const response = await api.get('/api/v1/favorites')
        setFavorites(response.data?.data || [])
      } catch (error) {
        console.error('즐겨찾기 목록 조회 실패:', error)
        setFavorites([])
      } finally {
        setLoadingFavorites(false)
      }
    }
    checkLoginAndLoadFavorites()
  }, [])

  const handleFavoriteSelect = (favorite) => {
    setDestination({ name: favorite.name, lat: favorite.lat, lng: favorite.lng })
  }

  useEffect(() => {
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
  }, [])

  const saveRecentSearch = (origin, destination) => {
    const newSearch = {
      originName: origin.name, originLat: origin.lat, originLng: origin.lng,
      destName: destination.name, destLat: destination.lat, destLng: destination.lng,
    }
    setRecentSearches((prev) => {
      const filtered = prev.filter((s) => !(
        s.originName === newSearch.originName && s.originLat === newSearch.originLat &&
        s.originLng === newSearch.originLng && s.destName === newSearch.destName &&
        s.destLat === newSearch.destLat && s.destLng === newSearch.destLng
      ))
      const limited = [newSearch, ...filtered].slice(0, 5)
      try { localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(limited)) } catch (e) { console.error(e) }
      return limited
    })
  }

  const handleRecentSearchSelect = async (search) => {
    const o = { name: search.originName, lat: search.originLat, lng: search.originLng }
    const d = { name: search.destName, lat: search.destLat, lng: search.destLng }
    setOrigin(o)
    setDestination(d)
    if (o.lat !== undefined && o.lng !== undefined && d.lat !== undefined && d.lng !== undefined) {
      setTimeout(() => handleQueryLastTrain(o, d), 100)
    }
  }

  const handleRemoveRecentSearch = (indexToRemove) => {
    setRecentSearches((prev) => {
      const updated = prev.filter((_, i) => i !== indexToRemove)
      try { localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(updated)) } catch (e) { console.error(e) }
      return updated
    })
  }

  const handleOriginSelect = (place) => {
    setOrigin({ name: place.name, lat: place.lat, lng: place.lng })
    setGpsError('')
    setShowOriginSearch(false)
  }

  const handleDestSelect = (place) => {
    setDestination({ name: place.name, lat: place.lat, lng: place.lng })
    setShowDestSearch(false)
  }

  const handleChangeOrigin = () => setShowOriginSearch(true)

  const handleBackToGPS = () => { if (gpsLocation) setOrigin(gpsLocation) }

  const isOriginChangedFromGPS = origin && gpsLocation &&
    origin.name !== gpsLocation.name && origin.name !== '현재 위치 (GPS 자동)'

  const handleQueryLastTrain = async (originParam = null, destParam = null) => {
    const queryOrigin = originParam || origin
    const queryDest = destParam || destination
    if (queryOrigin && queryDest && queryOrigin.lat === queryDest.lat && queryOrigin.lng === queryDest.lng) {
      setQueryError('출발지와 도착지가 같아요')
      return
    }
    setQuerying(true)
    setQueryError('')
    try {
      const queryParams = new URLSearchParams({
        originLat: queryOrigin.lat, originLng: queryOrigin.lng, originName: queryOrigin.name,
        destLat: queryDest.lat, destLng: queryDest.lng, destName: queryDest.name,
      }).toString()
      const response = await fetch(`/api/v1/last-train?${queryParams}`, {
        method: 'GET',
        headers: { Authorization: `Bearer ${localStorage.getItem('accessToken')}` },
      })
      if (!response.ok) {
        if (response.status === 404) { setQueryError('오늘 막차는 종료됐어요'); return }
        if (response.status >= 500) { setQueryError('잠시 후 다시 시도해주세요'); return }
        setQueryError('막차 조회에 실패했어요. 다시 시도해주세요.')
        return
      }
      const data = await response.json()
      saveRecentSearch(queryOrigin, queryDest)
      navigate('/result', { state: { result: data, destination: queryDest } })
    } catch (error) {
      console.error('막차 조회 오류:', error)
      setQueryError('잠시 후 다시 시도해주세요')
    } finally {
      setQuerying(false)
    }
  }

  const isQueryable = origin && destination

  return (
    <div className="h-full bg-[#eef1f4] flex flex-col relative overflow-hidden">
      {/* 지도 영역 — Phase 1 + 2: 기본 지도 + 현재 위치 + 출발지·도착지 마커 */}
      <div className="relative flex-shrink-0 h-[300px]">
        <MapView
          gpsLocation={gpsLocation}
          gpsError={gpsError}
          origin={origin}
          destination={destination}
        />
        <div className="absolute top-4 left-4 bg-white/90 px-3.5 py-2 rounded-full font-bold text-[15px] text-gray-900 shadow z-10 pointer-events-none">막차알리미</div>
      </div>

      <div className="flex-1 bg-white rounded-t-[20px] -mt-5 shadow-[0_-4px_16px_rgba(0,0,0,0.08)] flex flex-col overflow-hidden">
        <div className="w-9 h-1 bg-gray-200 rounded-full mx-auto mt-2.5 mb-1 flex-shrink-0" />
        <div className="px-4 pt-3 pb-6 overflow-y-auto flex-1">
          {loading ? (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin rounded-full h-6 w-6 border-2 border-gray-300 border-t-gray-900" />
              <span className="text-gray-600 ml-3 text-sm">위치 정보를 불러오는 중...</span>
            </div>
          ) : (
            <>
              <div className="bg-gray-50 border border-gray-200 rounded-lg overflow-hidden mb-3">
                <button onClick={handleChangeOrigin} className="w-full flex items-center gap-2.5 px-3.5 py-3 text-left">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 flex-shrink-0" />
                  <span className="flex-1 min-w-0">
                    <span className="block text-[10px] font-medium text-gray-400 uppercase tracking-wide">출발지</span>
                    <span className="block font-semibold text-sm text-gray-900 mt-0.5 truncate">
                      {origin?.name === '현재 위치 (GPS 자동)' ? '현재 위치' : origin?.name || '위치를 선택하세요'}
                      {origin?.name === '현재 위치 (GPS 자동)' && <span className="text-gray-500 font-normal text-xs"> · GPS</span>}
                    </span>
                  </span>
                </button>
                <div className="h-px bg-gray-200 ml-3.5" />
                <button onClick={() => setShowDestSearch(true)} className="w-full flex items-center gap-2.5 px-3.5 py-3 text-left">
                  <span className="w-2 h-2 rounded-full bg-gray-900 flex-shrink-0" />
                  <span className="flex-1 min-w-0">
                    <span className="block text-[10px] font-medium text-gray-400 uppercase tracking-wide">도착지</span>
                    <span className={`block font-semibold text-sm mt-0.5 truncate ${destination?.name ? 'text-gray-900' : 'text-gray-400'}`}>
                      {destination?.name || '위치를 선택하세요'}
                    </span>
                  </span>
                </button>
              </div>

              {isOriginChangedFromGPS && (
                <button onClick={handleBackToGPS} className="w-full mb-3 px-3 py-2 bg-blue-50 hover:bg-blue-100 text-blue-600 font-medium rounded text-xs border border-blue-200 transition">현재 위치로 복귀</button>
              )}
              {gpsError && <div className="mb-3 text-xs text-amber-800 bg-amber-50 px-3 py-2 rounded border border-amber-200">{gpsError}</div>}
              {queryError && (
                <div className={`mb-3 text-sm px-3.5 py-2.5 rounded border ${queryError === '오늘 막차는 종료됐어요' ? 'text-amber-900 bg-amber-50 border-amber-200' : 'text-red-900 bg-red-50 border-red-200'}`}>{queryError}</div>
              )}

              <button
                onClick={() => handleQueryLastTrain()}
                disabled={!isQueryable || querying}
                className={`w-full py-3.5 font-semibold rounded-lg transition text-[15px] mb-5 ${isQueryable && !querying ? 'bg-gray-900 text-white hover:bg-black cursor-pointer' : 'bg-gray-200 text-gray-400 cursor-not-allowed'}`}
              >
                {querying ? '조회 중...' : '막차 조회'}
              </button>

              {isLoggedIn && (
                <div className="mb-4">
                  <div className="text-gray-900 text-xs font-semibold mb-2.5 uppercase tracking-wide">즐겨찾기</div>
                  {loadingFavorites ? (
                    <div className="flex items-center py-2"><div className="animate-spin rounded-full h-4 w-4 border-2 border-gray-300 border-t-gray-900" /><span className="text-gray-500 ml-2 text-xs">불러오는 중...</span></div>
                  ) : favorites.length > 0 ? (
                    <div className="flex gap-2 overflow-x-auto pb-1">
                      {favorites.map((favorite) => (
                        <button key={favorite.id} onClick={() => handleFavoriteSelect(favorite)} className="px-3.5 py-2 bg-gray-100 rounded-full flex items-center gap-1.5 text-[13px] font-medium text-gray-900 flex-shrink-0">
                          <span className="text-base">{favorite.emoji}</span><span>{favorite.name}</span>
                        </button>
                      ))}
                    </div>
                  ) : (
                    <p className="text-gray-500 text-xs">아직 즐겨찾기한 목적지가 없어요</p>
                  )}
                </div>
              )}

              {recentSearches.length > 0 && (
                <div className="mb-2">
                  <div className="text-gray-900 text-xs font-semibold mb-2 uppercase tracking-wide">최근 검색</div>
                  {recentSearches.map((search, index) => (
                    <div key={index} onClick={() => handleRecentSearchSelect(search)} className="flex items-center justify-between py-2.5 border-b border-gray-100 cursor-pointer">
                      <div className="text-[13px] font-medium text-gray-900 flex-1 min-w-0 truncate">
                        <span className="text-gray-500">{search.originName}</span><span className="text-gray-300 mx-1.5">→</span><span>{search.destName}</span>
                      </div>
                      <button onClick={(e) => { e.stopPropagation(); handleRemoveRecentSearch(index) }} className="ml-2 p-1.5 text-gray-300 hover:text-red-500 text-xs flex-shrink-0">✕</button>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {showOriginSearch && <PlaceSearchModal mode="origin" onSelect={handleOriginSelect} onClose={() => setShowOriginSearch(false)} />}
      {showDestSearch && <PlaceSearchModal mode="destination" onSelect={handleDestSelect} onClose={() => setShowDestSearch(false)} />}
    </div>
  )
}
