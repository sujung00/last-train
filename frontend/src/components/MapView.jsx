import { useEffect, useRef, useState } from 'react'
import { useKakaoLoader } from 'react-kakao-maps-sdk'
import { Map, MapMarker } from 'react-kakao-maps-sdk'

/**
 * MapView 컴포넌트 (Phase 1 + 2: 기본 지도 + 현재 위치 + 출발지·도착지)
 *
 * 역할:
 *   - 카카오맵 SDK 로드 및 지도 렌더링
 *   - 현재 위치 마커 표시 (GPS 권한 허용 시, 파란색)
 *   - 출발지 마커 표시 (녹색, PlaceSearchModal에서 선택)
 *   - 도착지 마커 표시 (검은색, PlaceSearchModal에서 선택)
 *   - 범위 조정: 2개 모두 있으면 LatLngBounds, 1개만 있으면 center 이동
 *   - 서울시청 좌표로 fallback (GPS 권한 거부 시)
 *   - SDK 로드 실패/에러 처리
 *
 * Props:
 *   - gpsLocation: { lat, lng, name } — GPS 획득 성공 시 현재 위치
 *   - gpsError: string — GPS 획득 실패 시 에러 메시지
 *   - origin: { lat, lng, name } — 출발지 (PlaceSearchModal에서 선택)
 *   - destination: { lat, lng, name } — 도착지 (PlaceSearchModal에서 선택)
 *
 * 로딩 상태 흐름:
 *   1. SDK 로드 중 (loading = true)
 *      → "지도를 불러오는 중..." 스피너 표시
 *   2. SDK 로드 완료 (loading = false, window.kakao 존재)
 *      → Map 렌더링 (마커 표시, 범위 조정)
 *   3. SDK 로드 실패 (window.kakao 없음)
 *      → "카카오맵을 불러올 수 없습니다" 에러 메시지
 *
 * 높이: 부모에서 명시적 높이 필수 (예: h-[300px])
 */
export default function MapView({ gpsLocation, gpsError, origin, destination }) {
  // SDK 로드 상태 + 에러: useKakaoLoader()는 [loading, error] 배열 반환
  const [loading, error] = useKakaoLoader({
    appkey: import.meta.env.VITE_KAKAO_JS_KEY,
  })

  // SDK 로드 에러 상태 (훅 에러 또는 window.kakao 없음)
  const [loadError, setLoadError] = useState(false)

  // Map 인스턴스 ref (범위 조정 시 사용)
  const mapRef = useRef(null)

  // Fallback 좌표: 서울시청 (GPS 권한 거부 시 사용)
  const SEOUL_CITY_HALL = { lat: 37.5665, lng: 126.9780 }

  // 지도 center 결정: 도착지 > 출발지 > 현재 위치 > 서울시청
  const mapCenter = destination
    ? { lat: destination.lat, lng: destination.lng }
    : origin
    ? { lat: origin.lat, lng: origin.lng }
    : gpsLocation
    ? { lat: gpsLocation.lat, lng: gpsLocation.lng }
    : SEOUL_CITY_HALL

  // SDK 로드 완료 후 error 또는 window.kakao 존재 여부 확인
  useEffect(() => {
    if (!loading) {
      // SDK 로드 완료 시점에 error 또는 window.kakao 체크
      if (error || !window.kakao || !window.kakao.maps) {
        setLoadError(true)
      }
    }
  }, [loading, error])

  // 출발지·도착지 모두 있을 때 LatLngBounds로 범위 조정
  // 하나만 있을 때는 center가 자동으로 그 지점으로 이동
  useEffect(() => {
    if (!mapRef.current || !window.kakao) return

    if (origin && destination) {
      // 두 지점 모두 있음: LatLngBounds 계산
      const bounds = new window.kakao.maps.LatLngBounds(
        new window.kakao.maps.LatLng(origin.lat, origin.lng),
        new window.kakao.maps.LatLng(destination.lat, destination.lng)
      )
      // 지도 범위를 bounds로 설정
      mapRef.current.setBounds(bounds)
    }
    // 한 지점만 있으면 center props가 자동으로 처리함 (추가 처리 불필요)
  }, [origin, destination])

  // 마커 이미지: SVG 기반 다른 색상
  const markerImages = {
    current: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iNDIiIHZpZXdCb3g9IjAgMCAzMiA0MiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iNDIiIGZpbGw9IndoaXRlIiBmaWxsLW9wYWNpdHk9IjAiLz48Y2lyY2xlIGN4PSIxNiIgY3k9IjE2IiByPSI4IiBmaWxsPSIjMjU2M2ViIi8+PC9zdmc+',
    origin: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iNDIiIHZpZXdCb3g9IjAgMCAzMiA0MiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iNDIiIGZpbGw9IndoaXRlIiBmaWxsLW9wYWNpdHk9IjAiLz48Y2lyY2xlIGN4PSIxNiIgY3k9IjE2IiByPSI4IiBmaWxsPSIjMTBiOTgxIi8+PC9zdmc+',
    destination: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iNDIiIHZpZXdCb3g9IjAgMCAzMiA0MiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iNDIiIGZpbGw9IndoaXRlIiBmaWxsLW9wYWNpdHk9IjAiLz48Y2lyY2xlIGN4PSIxNiIgY3k9IjE2IiByPSI4IiBmaWxsPSIjMWYyOTM3Ii8+PC9zdmc+',
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 상태 1: SDK 로드 중 (VITE_KAKAO_JS_KEY 로드, SDK 파싱 중)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  if (loading) {
    return (
      <div className="w-full h-full bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-6 w-6 border-2 border-gray-300 border-t-gray-900 mx-auto mb-2"></div>
          <p className="text-gray-500 text-sm">지도를 불러오는 중...</p>
        </div>
      </div>
    )
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 상태 2: SDK 로드 실패 (VITE_KAKAO_JS_KEY 무효 또는 네트워크 실패)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  if (loadError || !window.kakao || !window.kakao.maps) {
    return (
      <div className="w-full h-full bg-gray-50 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-gray-600 text-sm mb-2">카카오맵을 불러올 수 없습니다</p>
          <p className="text-gray-400 text-xs">
            JavaScript 키를 확인해주세요
            <br />
            (.env의 VITE_KAKAO_JS_KEY)
          </p>
        </div>
      </div>
    )
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 상태 3: SDK 로드 완료 → 지도 렌더링 (Phase 1 + Phase 2)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  return (
    <div className="w-full h-full">
      <Map
        center={mapCenter}
        level={4}
        style={{ width: '100%', height: '100%' }}
        className="w-full h-full"
        ref={mapRef}
      >
        {/* Phase 1: 현재 위치 마커 (파란색, GPS 권한 허용 시) */}
        {gpsLocation && (
          <MapMarker
            position={{ lat: gpsLocation.lat, lng: gpsLocation.lng }}
            title="현재 위치"
            image={{
              src: markerImages.current,
              size: { width: 32, height: 42 },
              options: { offset: { x: 16, y: 42 } },
            }}
          />
        )}

        {/* Phase 2: 출발지 마커 (녹색) */}
        {origin && (
          <MapMarker
            position={{ lat: origin.lat, lng: origin.lng }}
            title="출발지"
            image={{
              src: markerImages.origin,
              size: { width: 32, height: 42 },
              options: { offset: { x: 16, y: 42 } },
            }}
          />
        )}

        {/* Phase 2: 도착지 마커 (검은색) */}
        {destination && (
          <MapMarker
            position={{ lat: destination.lat, lng: destination.lng }}
            title="도착지"
            image={{
              src: markerImages.destination,
              size: { width: 32, height: 42 },
              options: { offset: { x: 16, y: 42 } },
            }}
          />
        )}
      </Map>
    </div>
  )
}
