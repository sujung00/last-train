import { BrowserRouter, Routes, Route } from 'react-router-dom'
import MainPage from './pages/MainPage'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import FavoritePage from './pages/FavoritePage'
import KakaoCallbackPage from './pages/KakaoCallbackPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<MainPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/favorites" element={<FavoritePage />} />
        <Route path="/auth/kakao/callback" element={<KakaoCallbackPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
