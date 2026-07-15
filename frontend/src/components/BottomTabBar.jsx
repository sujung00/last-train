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
 * - iOS safe area 처리 (padding-bottom)
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

  // 탭 설정
  const tabs = [
    { label: '홈', path: '/', icon: 'home' },
    { label: '즐겨찾기', path: '/favorites', icon: 'star' },
    {
      label: isLoggedIn ? '마이' : '로그인',
      path: isLoggedIn ? '/mypage' : '/login',
      icon: isLoggedIn ? 'user' : 'login',
    },
  ]

  const getIconSVG = (icon) => {
    switch (icon) {
      case 'home':
        return (
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
            <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z" />
          </svg>
        )
      case 'star':
        return (
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2l-2.81 6.63L2 9.24l5.46 4.73L5.82 21z" />
          </svg>
        )
      case 'user':
        return (
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
          </svg>
        )
      case 'login':
        return (
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h2V15h-2zm0-8h2V13h-2z" />
          </svg>
        )
      default:
        return null
    }
  }

  const handleTabClick = (tab) => {
    // 마이/로그인 탭은 매번 최신 localStorage 상태 확인
    if (tab.icon === 'user' || tab.icon === 'login') {
      const token = localStorage.getItem('accessToken')
      navigate(token ? '/mypage' : '/login')
    } else {
      navigate(tab.path)
    }
  }

  return (
    <div className="absolute bottom-0 left-0 right-0 w-full bg-white border-t border-gray-200 flex justify-around items-center h-[60px] z-40">
      {tabs.map((tab) => {
        const isActive = location.pathname === tab.path
        return (
          <button
            key={tab.path}
            onClick={() => handleTabClick(tab)}
            className={`flex-1 flex flex-col items-center justify-center h-full gap-1 transition-colors ${
              isActive
                ? 'text-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
            title={tab.label}
          >
            {getIconSVG(tab.icon)}
            <span className="text-xs font-medium">{tab.label}</span>
          </button>
        )
      })}
    </div>
  )
}
