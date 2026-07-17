import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'
import EmojiSelectorModal from '../components/EmojiSelectorModal'
import PlaceSearchModal from '../components/PlaceSearchModal'

/**
 * 3b 리디자인: 절제된 한 줄 행 목록 + 조회 액션 중심
 * 로직은 기존 FavoritePage.jsx와 동일. 마크업만 교체.
 */
export default function FavoritePage() {
  const navigate = useNavigate()
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [favorites, setFavorites] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [querying, setQuerying] = useState(false)
  const [queryError, setQueryError] = useState('')

  const [editingFavorite, setEditingFavorite] = useState(null)
  const [editName, setEditName] = useState('')
  const [editEmoji, setEditEmoji] = useState('')
  const [showEmojiSelector, setShowEmojiSelector] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [editError, setEditError] = useState('')

  const [confirmDelete, setConfirmDelete] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)

  const [showPlaceSearch, setShowPlaceSearch] = useState(false)
  const [selectedPlace, setSelectedPlace] = useState(null)
  const [showAddEmojiSelector, setShowAddEmojiSelector] = useState(false)
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    setIsLoggedIn(!!token)
    setLoading(false)
  }, [])

  useEffect(() => {
    if (!isLoggedIn) return
    const loadFavorites = async () => {
      try {
        setLoading(true)
        setError('')
        const response = await api.get('/api/v1/favorites')
        setFavorites(response.data?.data || [])
      } catch (err) {
        setError('목록을 불러오지 못했어요. 다시 시도해주세요')
        setFavorites([])
      } finally {
        setLoading(false)
      }
    }
    loadFavorites()
  }, [isLoggedIn])

  const handleAddFavorite = () => setShowPlaceSearch(true)
  const handleAddFavoriteEmpty = () => setShowPlaceSearch(true)

  const handleSelectPlace = (place) => {
    setSelectedPlace(place)
    setShowPlaceSearch(false)
    setShowAddEmojiSelector(true)
  }
  const handleClosePlaceSearch = () => { setShowPlaceSearch(false); setSelectedPlace(null) }

  const handleSelectAddEmoji = async (emoji) => {
    setShowAddEmojiSelector(false)
    setAdding(true)
    setError('')
    try {
      await api.post('/api/v1/favorites', { name: selectedPlace.name, emoji, lat: selectedPlace.lat, lng: selectedPlace.lng, address: selectedPlace.address || null })
      const listResponse = await api.get('/api/v1/favorites')
      setFavorites(listResponse.data?.data || [])
      setSelectedPlace(null)
    } catch (err) {
      setError('즐겨찾기 추가 중 오류가 발생했어요. 다시 시도해주세요.')
    } finally {
      setAdding(false)
    }
  }
  const handleCloseAddEmojiSelector = () => { setShowAddEmojiSelector(false); setSelectedPlace(null) }

  const handleViewFavorite = async (favorite) => {
    setQuerying(true)
    setQueryError('')
    try {
      const position = await new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve, reject))
      const { latitude, longitude } = position.coords
      const origin = { name: '현재 위치 (GPS 자동)', lat: latitude, lng: longitude }
      const queryParams = new URLSearchParams({
        originLat: origin.lat, originLng: origin.lng, originName: origin.name,
        destLat: favorite.lat, destLng: favorite.lng, destName: favorite.name,
      }).toString()
      const response = await fetch(`/api/v1/last-train?${queryParams}`, {
        method: 'GET', headers: { Authorization: `Bearer ${localStorage.getItem('accessToken')}` },
      })
      if (!response.ok) {
        if (response.status === 404) { setQueryError('오늘 막차는 종료됐어요'); return }
        if (response.status >= 500) { setQueryError('잠시 후 다시 시도해주세요'); return }
        setQueryError('막차 조회에 실패했어요. 다시 시도해주세요.')
        return
      }
      const data = await response.json()
      navigate('/result', { state: { result: data, destination: favorite } })
    } catch (err) {
      navigate('/')
    } finally {
      setQuerying(false)
    }
  }

  const handleEditFavorite = (favorite) => {
    setEditingFavorite(favorite)
    setEditName(favorite.name)
    setEditEmoji(favorite.emoji || '📍')
  }
  const handleCloseEditModal = () => { setEditingFavorite(null); setEditName(''); setEditEmoji(''); setShowEmojiSelector(false) }
  const handleSelectEditEmoji = (emoji) => { setEditEmoji(emoji); setShowEmojiSelector(false) }

  const handleSaveFavorite = async () => {
    setEditError('')
    if (!editName.trim()) { setEditError('이름을 입력해주세요'); return }
    setSaving(true)
    try {
      await api.patch(`/api/v1/favorites/${editingFavorite.id}`, { name: editName, emoji: editEmoji })
      const response = await api.get('/api/v1/favorites')
      setFavorites(response.data?.data || [])
      handleCloseEditModal()
    } catch (err) {
      setEditError('편집 중 오류가 발생했어요. 다시 시도해주세요.')
    } finally {
      setSaving(false)
    }
  }

  const handleQuickDeleteFavorite = (favorite) => { setDeleteTarget(favorite); setConfirmDelete(true) }
  const handleDeleteFavorite = () => { setDeleteTarget(editingFavorite); setConfirmDelete(true) }

  const handleConfirmDelete = async () => {
    setEditError('')
    setConfirmDelete(false)
    setDeleting(true)
    try {
      await api.delete(`/api/v1/favorites/${deleteTarget.id}`)
      const response = await api.get('/api/v1/favorites')
      setFavorites(response.data?.data || [])
      handleCloseEditModal()
    } catch (err) {
      setEditError('삭제 중 오류가 발생했어요. 다시 시도해주세요.')
    } finally {
      setDeleting(false)
      setDeleteTarget(null)
    }
  }
  const handleCancelDelete = () => { setConfirmDelete(false); setDeleteTarget(null) }
  const handleLoginClick = () => navigate('/login')

  if (!isLoggedIn) {
    return (
      <div className="h-full bg-white flex flex-col items-center justify-center px-4">
        <div className="text-center">
          <h2 className="text-gray-900 text-lg font-bold mb-2">즐겨찾기</h2>
          <p className="text-gray-600 text-sm mb-8 max-w-sm">로그인하면 즐겨찾기를 사용할 수 있어요</p>
          <button onClick={handleLoginClick} className="px-6 py-3 bg-gray-900 hover:bg-black text-white rounded-lg font-medium transition">로그인하기</button>
        </div>
      </div>
    )
  }

  return (
    <div className="h-full bg-white flex flex-col">
      <header className="bg-white border-b border-gray-200 px-4 py-6 sticky top-0 z-10 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">즐겨찾기</h1>
        <button onClick={handleAddFavorite} disabled={adding} className="text-sm text-blue-600 font-semibold disabled:text-gray-400">
          {adding ? '추가 중...' : '+ 추가'}
        </button>
      </header>

      <main className="flex-1 overflow-y-auto bg-white">
        {loading && (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-2 border-gray-300 border-t-gray-900"></div>
            <span className="text-gray-500 ml-3">불러오는 중...</span>
          </div>
        )}
        {(error || queryError) && !loading && (
          <div className="mx-4 mt-4 text-sm text-red-900 bg-red-50 px-4 py-3 rounded border border-red-200">{error || queryError}</div>
        )}

        {!loading && favorites.length > 0 && (
          <div>
            {favorites.map((favorite) => (
              <div key={favorite.id} className="flex items-center gap-3 px-4 py-3.5 border-b border-gray-100">
                <div className="w-9 h-9 rounded-full bg-gray-100 flex items-center justify-center text-lg flex-shrink-0">{favorite.emoji || '📍'}</div>
                <div className="flex-1 min-w-0">
                  <div className="text-gray-900 font-semibold text-sm">{favorite.name}</div>
                  <div className="text-gray-500 text-xs mt-0.5 truncate">{favorite.address}</div>
                </div>
                <button onClick={() => handleViewFavorite(favorite)} disabled={querying}
                  className="text-[13px] text-blue-600 font-semibold whitespace-nowrap px-3 py-1.5 border border-blue-200 rounded-full disabled:opacity-50">
                  {querying ? '조회 중' : '조회'}
                </button>
                <button onClick={() => handleEditFavorite(favorite)} className="text-gray-400 text-base px-1">⋯</button>
              </div>
            ))}
          </div>
        )}

        {!loading && favorites.length === 0 && !error && (
          <div className="text-center py-12 px-4">
            <p className="text-gray-500 text-sm mb-6">아직 즐겨찾기한 목적지가 없어요</p>
            <button onClick={handleAddFavoriteEmpty} disabled={adding} className="px-6 py-3 bg-gray-900 hover:bg-black text-white rounded-lg font-medium transition disabled:bg-gray-400">
              {adding ? '추가 중...' : '목적지 추가'}
            </button>
          </div>
        )}
      </main>

      {editingFavorite && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="w-full max-w-[430px] bg-white rounded-lg p-6 border border-gray-200">
            <h2 className="text-gray-900 text-lg font-bold mb-6">위치 편집</h2>
            {editError && <div className="mb-6 text-sm text-red-900 bg-red-50 px-4 py-3 rounded border border-red-200">{editError}</div>}
            <div className="mb-6">
              <label className="block text-gray-900 text-sm font-medium mb-2">이름</label>
              <input type="text" value={editName} onChange={(e) => setEditName(e.target.value)} disabled={saving || deleting}
                className="w-full px-4 py-3 bg-gray-50 text-gray-900 border border-gray-200 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 transition disabled:bg-gray-200" placeholder="이름을 입력하세요" />
            </div>
            <div className="mb-6">
              <label className="block text-gray-900 text-sm font-medium mb-2">아이콘</label>
              <button onClick={() => setShowEmojiSelector(true)} disabled={saving || deleting}
                className="w-full px-4 py-3 bg-gray-50 hover:bg-gray-100 text-gray-900 border border-gray-200 rounded transition disabled:bg-gray-200 flex items-center justify-center gap-2">
                <span className="text-2xl">{editEmoji}</span><span className="text-sm">변경</span>
              </button>
            </div>
            <div className="flex gap-3">
              <button onClick={handleCloseEditModal} disabled={saving || deleting} className="flex-1 py-3 bg-gray-100 hover:bg-gray-200 text-gray-900 rounded font-medium transition disabled:bg-gray-200">취소</button>
              <button onClick={handleSaveFavorite} disabled={saving || deleting} className="flex-1 py-3 bg-gray-900 hover:bg-black text-white rounded font-medium transition disabled:bg-gray-400">{saving ? '저장 중...' : '저장'}</button>
            </div>
            <button onClick={handleDeleteFavorite} disabled={saving || deleting} className="w-full mt-4 py-3 bg-white text-red-600 border border-red-200 rounded font-medium transition disabled:opacity-50">
              {deleting ? '삭제 중...' : '삭제'}
            </button>
          </div>
        </div>
      )}

      {confirmDelete && deleteTarget && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="w-full max-w-[430px] bg-white rounded-lg p-6 border border-gray-200">
            <h3 className="text-gray-900 text-lg font-bold mb-4">"{deleteTarget.name}"을 삭제하시겠어요?</h3>
            <p className="text-gray-600 text-sm mb-6">삭제된 항목은 복구할 수 없습니다.</p>
            <div className="flex gap-3">
              <button onClick={handleCancelDelete} disabled={deleting} className="flex-1 px-4 py-3 bg-gray-100 hover:bg-gray-200 text-gray-900 rounded-lg transition disabled:bg-gray-200">취소</button>
              <button onClick={handleConfirmDelete} disabled={deleting} className="flex-1 px-4 py-3 bg-red-500 hover:bg-red-600 text-white rounded-lg transition disabled:bg-gray-400">{deleting ? '삭제 중...' : '삭제'}</button>
            </div>
          </div>
        </div>
      )}

      {showEmojiSelector && editingFavorite && (
        <EmojiSelectorModal onSelect={handleSelectEditEmoji} onClose={() => setShowEmojiSelector(false)} destination={editingFavorite.name || ''} />
      )}
      {showPlaceSearch && <PlaceSearchModal mode="destination" onSelect={handleSelectPlace} onClose={handleClosePlaceSearch} />}
      {showAddEmojiSelector && selectedPlace && (
        <EmojiSelectorModal onSelect={handleSelectAddEmoji} onClose={handleCloseAddEmojiSelector} destination={selectedPlace.name?.trim() || '선택된 장소'} />
      )}
    </div>
  )
}
