import { useLocation, useNavigate } from 'react-router-dom'
import { useState } from 'react'

/**
 * 하단 탭바 컴포넌트
 *
 * 탭바가 표시되는 페이지: /, /favorites
 * 탭바가 숨겨지는 페이지: /result, /login, /signup, /auth/kakao/callback
 *
 * 기능:
 * - useLocation()으로 현재 라우트 감지
 * - 로그인 상태에 따라 세 번째 탭 변경:
 *   - 비로그인: "로그인" → /login
 *   - 로그인: "마이" → /mypage (준비 중)
 * - 현재 페이지에 active 스타일 적용
 */
export default function BottomTabBar() {
  const location = useLocation()
  const navigate = useNavigate()

  // ✅ 로그인 상태: 초기값 계산 함수로 처리 (setState in effect 제거)
  const [isLoggedIn] = useState(() => {
    const token = localStorage.getItem('accessToken')
    return !!token
  })

  // 탭바가 필요한 페이지 확인
  const shouldShowTabBar = ['/', '/favorites'].includes(location.pathname)

  if (!shouldShowTabBar) return null

  const tabs = [
    { label: '홈', path: '/', emoji: '🏠' },
    { label: '즐겨찾기', path: '/favorites', emoji: '⭐' },
    {
      label: isLoggedIn ? '마이' : '로그인',
      path: isLoggedIn ? '/mypage' : '/login',
      emoji: isLoggedIn ? '👤' : '🔐',
    },
  ]

  return (
    <div className="fixed bottom-4 left-1/2 -translate-x-1/2 w-full max-w-[430px] bg-[#1a1a2e] border-t border-gray-700 flex justify-around items-center h-[60px] z-40 rounded-b-lg">
      {tabs.map((tab) => {
        const isActive = location.pathname === tab.path
        return (
          <button
            key={tab.path}
            onClick={() => navigate(tab.path)}
            className={`flex-1 flex flex-col items-center justify-center h-full gap-1 transition ${
              isActive
                ? 'text-purple-400 border-t-2 border-purple-600'
                : 'text-gray-400 hover:text-gray-300'
            }`}
          >
            <span className="text-lg">{tab.emoji}</span>
            <span className="text-xs font-medium">{tab.label}</span>
          </button>
        )
      })}
    </div>
  )
}
