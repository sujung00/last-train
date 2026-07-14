import { useState } from 'react'

/**
 * T-010 구현: 이모지 선택 모달
 *
 * FR-013: 즐겨찾기 등록 시 사용자가 직접 이모지를 선택해 설정할 수 있어야 한다
 *
 * Props:
 *   onSelect: (emoji) => void (이모지 선택 콜백)
 *   onClose: () => void (모달 닫기)
 *   destination: string (목적지명, 헤더에 표시)
 *
 * 참고: 조건부 렌더링으로 제어되므로 isOpen prop 제거 (PlaceSearchModal과 동일)
 */
const EMOJI_OPTIONS = [
  '🏠', '🏢', '🏬', '🎓',
  '🏥', '⛪', '🏞️', '🎬',
  '🍔', '🏖️', '🎪', '🚆',
  '✈️', '🚇', '🎢', '🏋️',
]

export default function EmojiSelectorModal({ onSelect, onClose, destination }) {
  const [loading, setLoading] = useState(false)

  const handleSelectEmoji = async (emoji) => {
    setLoading(true)
    try {
      await onSelect(emoji)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black bg-opacity-50 flex items-center justify-center p-4">
      {/* 430px 기준으로 중앙 정렬되는 모달 */}
      <div className="w-full max-w-[430px] bg-white rounded-lg p-6 border border-gray-200">
        {/* 헤더 */}
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-gray-900 text-lg font-bold">
            "{destination}" 아이콘 선택
          </h2>
          <button
            onClick={onClose}
            disabled={loading}
            className="text-gray-500 hover:text-gray-700 transition text-2xl disabled:cursor-not-allowed"
          >
            ✕
          </button>
        </div>
        <p className="text-gray-600 text-sm mb-6">
          이 위치를 대표할 아이콘을 선택해주세요
        </p>

        {/* 이모지 그리드 */}
        <div className="grid grid-cols-4 gap-3 mb-6">
          {EMOJI_OPTIONS.map((emoji) => (
            <button
              key={emoji}
              onClick={() => handleSelectEmoji(emoji)}
              disabled={loading}
              className="text-3xl p-3 bg-gray-50 hover:bg-gray-100 disabled:bg-gray-200 rounded-lg transition border border-gray-200 hover:border-blue-500 disabled:cursor-not-allowed"
            >
              {emoji}
            </button>
          ))}
        </div>

        {/* 취소 버튼 */}
        <button
          onClick={onClose}
          disabled={loading}
          className="w-full px-4 py-2 bg-gray-100 hover:bg-gray-200 disabled:bg-gray-200 text-gray-900 rounded transition disabled:cursor-not-allowed"
        >
          취소
        </button>
      </div>
    </div>
  )
}