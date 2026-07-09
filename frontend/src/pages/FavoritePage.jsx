import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'
import EmojiSelectorModal from '../components/EmojiSelectorModal'
import PlaceSearchModal from '../components/PlaceSearchModal'

/**
 * T-001 + T-002: FavoritePage 레이아웃 + 목록 조회 + 비로그인 처리
 *
 * 요구사항 (FR/AC):
 *   T-001:
 *     FR-001: 즐겨찾기 목록을 테이블/카드 형식으로 표시
 *     FR-002: 각 즐겨찾기에서 "조회" 액션 제공
 *     AC-001: 빈 목록일 때 "아직 즐겨찾기한 목적지가 없어요" 메시지 + 추가 버튼 표시
 *   T-002:
 *     AC-002: 비로그인 시 페이지 안에서 로그인 유도 화면 표시
 *     EC-002: 비로그인 상태 → 로그인 안내 + [로그인하기] 버튼
 *
 * 화면 구성:
 *   - 비로그인: 로그인 유도 화면
 *   - 로그인: 헤더 + 목록 + 로딩/빈 상태
 */
export default function FavoritePage() {
  const navigate = useNavigate()
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [favorites, setFavorites] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [querying, setQuerying] = useState(false)
  const [queryError, setQueryError] = useState('')

  // T-004, T-005: 편집 모달 상태
  const [editingFavorite, setEditingFavorite] = useState(null)
  const [editName, setEditName] = useState('')
  const [editEmoji, setEditEmoji] = useState('')
  const [showEmojiSelector, setShowEmojiSelector] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [editError, setEditError] = useState('')

  // T-007: 삭제 확인 모달 상태
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)

  // T-006: 목적지 추가 상태
  const [showPlaceSearch, setShowPlaceSearch] = useState(false)
  const [selectedPlace, setSelectedPlace] = useState(null)
  const [showAddEmojiSelector, setShowAddEmojiSelector] = useState(false)
  const [adding, setAdding] = useState(false)

  // T-002: 로그인 상태 확인
  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    setIsLoggedIn(!!token)
    setLoading(false)
  }, [])

  // T-001: GET /api/v1/favorites 호출 (로그인 시에만)
  useEffect(() => {
    if (!isLoggedIn) return

    const loadFavorites = async () => {
      try {
        setLoading(true)
        setError('')
        const response = await api.get('/api/v1/favorites')
        // ApiResponse 형식: { code, data: [...] }
        const favoritesList = response.data?.data || []
        setFavorites(favoritesList)
      } catch (err) {
        console.error('즐겨찾기 목록 조회 실패:', err)
        setError('목록을 불러오지 못했어요. 다시 시도해주세요')
        setFavorites([])
      } finally {
        setLoading(false)
      }
    }

    loadFavorites()
  }, [isLoggedIn])

  // T-006: "+ 추가" 버튼 클릭 (PlaceSearchModal 표시)
  const handleAddFavorite = () => {
    setShowPlaceSearch(true)
  }

  // T-006: "+ 목적지 추가" 버튼 클릭 (PlaceSearchModal 표시)
  const handleAddFavoriteEmpty = () => {
    setShowPlaceSearch(true)
  }

  // T-006: PlaceSearchModal에서 장소 선택 (모달 닫기 포함)
  const handleSelectPlace = (place) => {
    setSelectedPlace(place)
    setShowPlaceSearch(false)  // PlaceSearchModal 닫기
    setShowAddEmojiSelector(true)  // EmojiSelectorModal 표시
  }

  // T-006: PlaceSearchModal 닫기 (X 버튼)
  const handleClosePlaceSearch = () => {
    setShowPlaceSearch(false)
    setSelectedPlace(null)
  }

  // T-006: EmojiSelectorModal에서 이모지 선택 후 즐겨찾기 추가 (T-007: alert 제거)
  const handleSelectAddEmoji = async (emoji) => {
    const token = localStorage.getItem('accessToken')
    setShowAddEmojiSelector(false)
    setAdding(true)
    setError('')

    try {
      // POST /api/v1/favorites 호출
      // (ResultPage.jsx와 동일한 형식)
      const response = await api.post('/api/v1/favorites', {
        name: selectedPlace.name,
        emoji: emoji,
        lat: selectedPlace.lat,
        lng: selectedPlace.lng,
        address: selectedPlace.address || null,
      })

      // 목록 갱신
      const listResponse = await api.get('/api/v1/favorites')
      const favoritesList = listResponse.data?.data || []
      setFavorites(favoritesList)

      // 상태 초기화
      setSelectedPlace(null)
    } catch (err) {
      console.error('[즐겨찾기 추가 실패]', {
        message: err.message,
        statusCode: err.response?.status,
        responseData: err.response?.data,
        headers: err.response?.headers,
        fullError: err,
      })
      setError('즐겨찾기 추가 중 오류가 발생했어요. 다시 시도해주세요.')
    } finally {
      setAdding(false)
    }
  }

  // T-006: EmojiSelectorModal 닫기 (X 버튼)
  const handleCloseAddEmojiSelector = () => {
    setShowAddEmojiSelector(false)
    setSelectedPlace(null)
  }

  // T-003: "조회 →" 버튼 클릭
  // (FR-003, AC-003, EC-003)
  const handleViewFavorite = async (favorite) => {
    setQuerying(true)
    setQueryError('')

    try {
      // T-003-1: GPS 현재 위치 취득
      const position = await new Promise((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject)
      })

      const { latitude, longitude } = position.coords
      const origin = {
        name: '현재 위치 (GPS 자동)',
        lat: latitude,
        lng: longitude,
      }

      // T-003-2: GET /api/v1/last-train 호출
      // (MainPage와 동일한 형식)
      const queryParams = new URLSearchParams({
        originLat: origin.lat,
        originLng: origin.lng,
        originName: origin.name,
        destLat: favorite.lat,
        destLng: favorite.lng,
        destName: favorite.name,
      }).toString()

      const response = await fetch(`/api/v1/last-train?${queryParams}`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
        },
      })

      // MainPage와 동일한 에러 분기 처리
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

      // T-003-2: /result로 이동
      // (destination 객체도 함께 전달 - MainPage와 동일)
      navigate('/result', { state: { result: data, destination: favorite } })
    } catch (err) {
      console.error('조회 실패:', err)
      // T-003-3: GPS 실패 시 메인 홈(/)으로 이동 (EC-003)
      navigate('/')
    } finally {
      setQuerying(false)
    }
  }

  // T-004: "편집" 버튼 클릭 (편집 모달 표시)
  const handleEditFavorite = (favorite) => {
    setEditingFavorite(favorite)
    setEditName(favorite.name)
    setEditEmoji(favorite.emoji || '📍')
  }

  // T-004: 편집 모달 닫기
  const handleCloseEditModal = () => {
    setEditingFavorite(null)
    setEditName('')
    setEditEmoji('')
    setShowEmojiSelector(false)
  }

  // T-004: 이모지 선택
  const handleSelectEditEmoji = (emoji) => {
    setEditEmoji(emoji)
    setShowEmojiSelector(false)
  }

  // T-004, T-005: 저장 버튼 클릭 (T-007: alert 제거)
  const handleSaveFavorite = async () => {
    setEditError('')

    if (!editName.trim()) {
      setEditError('이름을 입력해주세요')
      return
    }

    setSaving(true)
    try {
      // PATCH /api/v1/favorites/{id} 호출 (name, emoji만 전송)
      await api.patch(`/api/v1/favorites/${editingFavorite.id}`, {
        name: editName,
        emoji: editEmoji,
      })

      // 목록 갱신
      const response = await api.get('/api/v1/favorites')
      const favoritesList = response.data?.data || []
      setFavorites(favoritesList)

      handleCloseEditModal()
    } catch (err) {
      console.error('편집 실패:', err)
      setEditError('편집 중 오류가 발생했어요. 다시 시도해주세요.')
    } finally {
      setSaving(false)
    }
  }

  // T-005-추가: 항목에서 직접 삭제 버튼 클릭 (확인 모달 표시)
  const handleQuickDeleteFavorite = (favorite) => {
    setDeleteTarget(favorite)
    setConfirmDelete(true)
  }

  // T-005: 삭제 버튼 클릭 (T-007: confirm 제거, 삭제 확인 모달 표시)
  const handleDeleteFavorite = () => {
    setDeleteTarget(editingFavorite)
    setConfirmDelete(true)
  }

  // T-007: 삭제 확인 모달에서 확인 버튼 클릭
  const handleConfirmDelete = async () => {
    setEditError('')
    setConfirmDelete(false)
    setDeleting(true)

    try {
      // DELETE /api/v1/favorites/{id} 호출
      await api.delete(`/api/v1/favorites/${deleteTarget.id}`)

      // 목록 갱신
      const response = await api.get('/api/v1/favorites')
      const favoritesList = response.data?.data || []
      setFavorites(favoritesList)

      handleCloseEditModal()
    } catch (err) {
      console.error('삭제 실패:', err)
      setEditError('삭제 중 오류가 발생했어요. 다시 시도해주세요.')
    } finally {
      setDeleting(false)
      setDeleteTarget(null)
    }
  }

  // T-007: 삭제 확인 모달에서 취소 버튼 클릭
  const handleCancelDelete = () => {
    setConfirmDelete(false)
    setDeleteTarget(null)
  }

  // T-002: 비로그인 로그인 유도 버튼
  const handleLoginClick = () => {
    navigate('/login')
  }

  // T-002: 비로그인 상태 화면 (AC-002, EC-002)
  if (!isLoggedIn) {
    return (
      <div className="h-full bg-[#1a1a2e] flex flex-col items-center justify-center px-4">
        <div className="text-center">
          <div className="text-5xl mb-4">⭐</div>
          <h2 className="text-white text-lg font-bold mb-2">즐겨찾기</h2>
          <p className="text-gray-300 text-sm mb-8 max-w-sm">
            로그인하면 즐겨찾기를 사용할 수 있어요
          </p>
          <button
            onClick={handleLoginClick}
            className="px-6 py-3 bg-[#6366f1] hover:bg-[#4338ca] text-white rounded-lg font-medium transition"
          >
            로그인하기
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="h-full bg-[#1a1a2e] flex flex-col">
      {/* 헤더 */}
      <header className="bg-[#1a1a2e] border-b border-gray-700 px-4 py-6 sticky top-0 z-10">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold text-white">즐겨찾기</h1>
          <button
            onClick={handleAddFavorite}
            disabled={adding}
            className="px-3 py-2 text-sm bg-[#6366f1] hover:bg-[#4338ca] text-white rounded transition disabled:bg-gray-600 disabled:cursor-not-allowed"
          >
            {adding ? '추가 중...' : '+ 추가'}
          </button>
        </div>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="flex-1 px-4 py-6 overflow-y-auto">
        {/* 로딩 상태 */}
        {loading && (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border border-gray-700 border-t-[#6366f1]"></div>
            <span className="text-gray-400 ml-3">즐겨찾기를 불러오는 중...</span>
          </div>
        )}

        {/* 목록 조회 에러 메시지 */}
        {error && !loading && (
          <div className="mb-6 text-sm text-red-200 bg-red-900 bg-opacity-50 px-4 py-3 rounded border border-red-600">
            {error}
          </div>
        )}

        {/* 조회 에러 메시지 */}
        {queryError && (
          <div className="mb-6 text-sm text-red-200 bg-red-900 bg-opacity-50 px-4 py-3 rounded border border-red-600">
            {queryError}
          </div>
        )}

        {/* 목록 표시 */}
        {!loading && favorites.length > 0 && (
          <div className="space-y-4">
            {favorites.map((favorite) => (
              <div
                key={favorite.id}
                className="bg-gray-800 rounded-lg p-4 border border-gray-700 hover:border-gray-600 transition"
              >
                {/* 항목: 이모지 + 이름 + 주소 */}
                <div className="flex items-start gap-3 mb-4">
                  <div className="text-2xl">{favorite.emoji || '📍'}</div>
                  <div className="flex-1">
                    <div className="text-white font-medium text-sm">{favorite.name}</div>
                    {favorite.address && (
                      <div className="text-gray-400 text-xs mt-1">{favorite.address}</div>
                    )}
                    <div className="text-gray-500 text-xs mt-2">
                      위치: {favorite.lat?.toFixed(4)}, {favorite.lng?.toFixed(4)}
                    </div>
                  </div>
                </div>

                {/* 버튼: 조회, 편집, 삭제 */}
                <div className="flex gap-2">
                  <button
                    onClick={() => handleViewFavorite(favorite)}
                    disabled={querying}
                    className="flex-1 py-2 bg-[#6366f1] hover:bg-[#4338ca] text-white text-sm rounded transition font-medium disabled:bg-gray-600 disabled:cursor-not-allowed"
                  >
                    {querying ? '조회 중...' : '조회 →'}
                  </button>
                  <button
                    onClick={() => handleEditFavorite(favorite)}
                    disabled={querying}
                    className="flex-1 py-2 bg-gray-700 hover:bg-gray-600 text-white text-sm rounded transition font-medium disabled:bg-gray-700 disabled:cursor-not-allowed"
                  >
                    편집
                  </button>
                  <button
                    onClick={() => handleQuickDeleteFavorite(favorite)}
                    disabled={querying || deleting}
                    className="py-2 px-3 bg-red-900 hover:bg-red-800 text-red-200 text-sm rounded transition font-medium disabled:bg-gray-600 disabled:cursor-not-allowed"
                    title="즐겨찾기 삭제"
                  >
                    🗑️
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* 빈 목록 상태 (AC-001, EC-001) */}
        {!loading && favorites.length === 0 && !error && (
          <div className="text-center py-12">
            <div className="text-gray-400 mb-6">
              <div className="text-5xl mb-3">📍</div>
              <p className="text-sm">아직 즐겨찾기한 목적지가 없어요</p>
            </div>
            <button
              onClick={handleAddFavoriteEmpty}
              disabled={adding}
              className="px-6 py-3 bg-[#6366f1] hover:bg-[#4338ca] text-white rounded-lg font-medium transition disabled:bg-gray-600 disabled:cursor-not-allowed"
            >
              {adding ? '추가 중...' : '+ 목적지 추가'}
            </button>
          </div>
        )}
      </main>

      {/* T-004: 편집 모달 */}
      {editingFavorite && (
        <div className="fixed inset-0 z-50 bg-black bg-opacity-50 flex items-center justify-center p-4">
          <div className="w-full max-w-[430px] bg-[#1a1a2e] rounded-lg p-6 border border-gray-700">
            {/* 헤더 */}
            <h2 className="text-white text-lg font-bold mb-6">즐겨찾기 편집</h2>

            {/* T-007: 에러 배너 */}
            {editError && (
              <div className="mb-6 text-sm text-red-200 bg-red-900 bg-opacity-50 px-4 py-3 rounded border border-red-600">
                {editError}
              </div>
            )}

            {/* 이름 입력 */}
            <div className="mb-6">
              <label className="block text-white text-sm font-medium mb-2">이름</label>
              <input
                type="text"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                disabled={saving || deleting}
                className="w-full px-4 py-3 bg-gray-700 text-white rounded focus:outline-none focus:bg-gray-600 transition disabled:bg-gray-600 disabled:cursor-not-allowed"
                placeholder="이름을 입력하세요"
              />
            </div>

            {/* 이모지 선택 */}
            <div className="mb-6">
              <label className="block text-white text-sm font-medium mb-2">이모지</label>
              <button
                onClick={() => setShowEmojiSelector(true)}
                disabled={saving || deleting}
                className="w-full px-4 py-3 bg-gray-700 hover:bg-gray-600 text-white rounded transition disabled:bg-gray-600 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                <span className="text-2xl">{editEmoji}</span>
                <span className="text-sm">변경</span>
              </button>
            </div>

            {/* 버튼 */}
            <div className="flex gap-3">
              <button
                onClick={handleCloseEditModal}
                disabled={saving || deleting}
                className="flex-1 py-3 bg-gray-700 hover:bg-gray-600 text-white rounded font-medium transition disabled:bg-gray-600 disabled:cursor-not-allowed"
              >
                취소
              </button>
              <button
                onClick={handleSaveFavorite}
                disabled={saving || deleting}
                className="flex-1 py-3 bg-[#6366f1] hover:bg-[#4338ca] text-white rounded font-medium transition disabled:bg-gray-600 disabled:cursor-not-allowed"
              >
                {saving ? '저장 중...' : '저장'}
              </button>
            </div>

            {/* T-005: 삭제 버튼 */}
            <button
              onClick={handleDeleteFavorite}
              disabled={saving || deleting}
              className="w-full mt-4 py-3 bg-red-900 hover:bg-red-800 text-red-200 rounded font-medium transition disabled:bg-gray-600 disabled:cursor-not-allowed disabled:text-gray-400"
            >
              {deleting ? '삭제 중...' : '삭제'}
            </button>
          </div>
        </div>
      )}

      {/* T-007: 삭제 확인 모달 */}
      {confirmDelete && deleteTarget && (
        <div className="fixed inset-0 z-50 bg-black bg-opacity-50 flex items-center justify-center p-4">
          <div className="w-full max-w-[430px] bg-[#1a1a2e] rounded-lg p-6 border border-gray-700">
            <h3 className="text-white text-lg font-bold mb-4">
              "{deleteTarget.name}" 즐겨찾기를 삭제하시겠어요?
            </h3>
            <p className="text-gray-400 text-sm mb-6">삭제된 즐겨찾기는 복구할 수 없습니다.</p>

            <div className="flex gap-3">
              <button
                onClick={handleCancelDelete}
                disabled={deleting}
                className="flex-1 px-4 py-3 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition disabled:bg-gray-600 disabled:cursor-not-allowed"
              >
                취소
              </button>
              <button
                onClick={handleConfirmDelete}
                disabled={deleting}
                className="flex-1 px-4 py-3 bg-red-700 hover:bg-red-600 text-white rounded-lg transition disabled:bg-gray-600 disabled:cursor-not-allowed"
              >
                {deleting ? '삭제 중...' : '삭제'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* T-004: EmojiSelectorModal 재활용 (편집용) - 조건부 렌더링 통일 */}
      {showEmojiSelector && editingFavorite && (
        <EmojiSelectorModal
          onSelect={handleSelectEditEmoji}
          onClose={() => setShowEmojiSelector(false)}
          destination={editingFavorite.name || ''}
        />
      )}

      {/* T-006: PlaceSearchModal 재활용 (목적지 추가) - 조건부 렌더링 추가 */}
      {showPlaceSearch && (
        <PlaceSearchModal
          mode="destination"
          onSelect={handleSelectPlace}
          onClose={handleClosePlaceSearch}
        />
      )}

      {/* T-006: EmojiSelectorModal 재활용 (이모지 선택) - PlaceSearchModal과 동일한 방식 */}
      {showAddEmojiSelector && selectedPlace && (
        <EmojiSelectorModal
          onSelect={handleSelectAddEmoji}
          onClose={handleCloseAddEmojiSelector}
          destination={selectedPlace.name?.trim() || '선택된 장소'}
        />
      )}
    </div>
  )
}
