import { useLocation, useNavigate } from 'react-router-dom'
import { useState, useEffect } from 'react'

/**
 * 하단 탭바 컴포넌트
 *
 * 탭바가 표시되는 페이지: /, /favorites, /mypage
 * 탭바가 숨겨지는 페이지: /result, /login, /signup, /auth/kakao/callback
 *
 * 기능:
 * - useLocation()으로 현재 라우트 감지
 * - 로그인 상태에 따라 세 번째 탭 변경:
 *   - 비로그인: "로그인" → /login
 *   - 로그인: "마이" → /mypage
 * - 현재 페이지에 active 스타일 적용
 * - localStorage 변화를 실시간으로 감지
 */
export default function BottomTabBar() {
  const location = useLocation()
  const navigate = useNavigate()

  // ✅ 로그인 상태: 초기값은 현재 localStorage에서 읽음, 이후 실시간 업데이트
  const [isLoggedIn, setIsLoggedIn] = useState(() => {
    const token = localStorage.getItem('accessToken')
    return !!token
  })

  // authChange 커스텀 이벤트 + storage 이벤트 감지
  useEffect(() => {
    // 같은 탭 내 로그인/로그아웃 감지 (커스텀 이벤트)
    const handleAuthChange = () => {
      const token = localStorage.getItem('accessToken')
      setIsLoggedIn(!!token)
    }

    // 다른 탭에서의 로그인/로그아웃 감지 (storage 이벤트)
    const handleStorageChange = (event) => {
      if (event.key === 'accessToken' || event.key === null) {
        const token = localStorage.getItem('accessToken')
        setIsLoggedIn(!!token)
      }
    }

    window.addEventListener('authChange', handleAuthChange)
    window.addEventListener('storage', handleStorageChange)
    return () => {
      window.removeEventListener('authChange', handleAuthChange)
      window.removeEventListener('storage', handleStorageChange)
    }
  }, [])

  // 탭바가 필요한 페이지 확인
  const shouldShowTabBar = ['/', '/favorites', '/mypage'].includes(location.pathname)

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

  const handleTabClick = (tab) => {
    // 마이/로그인 탭은 매번 최신 localStorage 상태 확인
    if (tab.emoji === '👤' || tab.emoji === '🔐') {
      const token = localStorage.getItem('accessToken')
      navigate(token ? '/mypage' : '/login')
    } else {
      navigate(tab.path)
    }
  }

  return (
    <div className="fixed bottom-4 left-1/2 -translate-x-1/2 w-full max-w-[430px] bg-[#1a1a2e] border-t border-gray-700 flex justify-around items-center h-[60px] z-40 rounded-b-lg">
      {tabs.map((tab) => {
        const isActive = location.pathname === tab.path
        return (
          <button
            key={tab.path}
            onClick={() => handleTabClick(tab)}
            className={`flex-1 flex flex-col items-center justify-center h-full gap-1 transition ${
              isActive
                ? 'border-t-2 border-t-[#6366f1]'
                : 'text-gray-400 hover:text-gray-300'
            }`}
            style={isActive ? { color: '#6366f1' } : {}}
          >
            <span className="text-lg">{tab.emoji}</span>
            <span className="text-xs font-medium">{tab.label}</span>
          </button>
        )
      })}
    </div>
  )
}
