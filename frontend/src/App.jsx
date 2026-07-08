import { BrowserRouter, Routes, Route } from 'react-router-dom'
import MainPage from './pages/MainPage'
import ResultPage from './pages/ResultPage'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import FavoritePage from './pages/FavoritePage'
import KakaoCallbackPage from './pages/KakaoCallbackPage'
import BottomTabBar from './components/BottomTabBar'

function App() {
  return (
    <div className="phone-app">
      {/* 핸드폰 화면 컨테이너 (430x844px 고정, 내부 스크롤) */}
      <div className="phone-app-inner">
        <BrowserRouter>
          <div className="phone-app-routes">
            <Routes>
              <Route path="/" element={<MainPage />} />
              <Route path="/result" element={<ResultPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/signup" element={<SignupPage />} />
              <Route path="/favorites" element={<FavoritePage />} />
              <Route path="/auth/kakao/callback" element={<KakaoCallbackPage />} />
            </Routes>
          </div>
          <BottomTabBar />
        </BrowserRouter>
      </div>
    </div>
  )
}

export default App
