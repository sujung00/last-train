import { useState } from 'react'

/**
 * T-010 구현: 이모지 선택 모달
 *
 * FR-013: 즐겨찾기 등록 시 사용자가 직접 이모지를 선택해 설정할 수 있어야 한다
 *
 * Props:
 *   isOpen: boolean (모달 표시 여부)
 *   onSelect: (emoji) => void (이모지 선택 콜백)
 *   onClose: () => void (모달 닫기)
 *   destination: string (목적지명, 헤더에 표시)
 */
const EMOJI_OPTIONS = [
  '🏠', '🏢', '🏬', '🎓',
  '🏥', '⛪', '🏞️', '🎬',
  '🍔', '🏖️', '🎪', '🚆',
  '✈️', '🚇', '🎢', '🏋️',
]

export default function EmojiSelectorModal({ isOpen, onSelect, onClose, destination }) {
  const [loading, setLoading] = useState(false)

  if (!isOpen) return null

  const handleSelectEmoji = async (emoji) => {
    setLoading(true)
    try {
      await onSelect(emoji)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black bg-opacity-50 flex items-center justify-center px-4">
      <div className="bg-[#1a1a2e] rounded-lg p-6 max-w-sm w-full border border-gray-700">
        {/* 헤더 */}
        <h2 className="text-white text-lg font-bold mb-2">
          "{destination}" 즐겨찾기 아이콘
        </h2>
        <p className="text-gray-400 text-sm mb-6">
          이 목적지를 대표할 아이콘을 선택해주세요
        </p>

        {/* 이모지 그리드 */}
        <div className="grid grid-cols-4 gap-3 mb-6">
          {EMOJI_OPTIONS.map((emoji) => (
            <button
              key={emoji}
              onClick={() => handleSelectEmoji(emoji)}
              disabled={loading}
              className="text-3xl p-3 bg-gray-800 hover:bg-gray-700 disabled:bg-gray-600 rounded-lg transition border border-gray-700 hover:border-purple-600 disabled:cursor-not-allowed"
            >
              {emoji}
            </button>
          ))}
        </div>

        {/* 취소 버튼 */}
        <button
          onClick={onClose}
          disabled={loading}
          className="w-full px-4 py-2 bg-gray-800 hover:bg-gray-700 disabled:bg-gray-600 text-white rounded transition disabled:cursor-not-allowed"
        >
          취소
        </button>
      </div>
    </div>
  )
}