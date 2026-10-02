import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { FullPageLoader } from './components/FullPageLoader'
import { ProtectedRoute } from './components/ProtectedRoute'
import AdminPage from './pages/AdminPage'
import ConfirmPage from './pages/ConfirmPage'
import CreatePostPage from './pages/CreatePostPage'
import FeedPage from './pages/FeedPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import PostDetailsPage from './pages/PostDetailsPage'
import ProfilePage from './pages/ProfilePage'
import RegisterPage from './pages/RegisterPage'

/** /profile без имени — это профиль текущего пользователя. */
function MyProfileRedirect() {
  const { user } = useAuth()
  if (!user) {
    return <FullPageLoader />
  }
  return <Navigate to={`/user/${encodeURIComponent(user.username)}`} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/confirm" element={<ConfirmPage />} />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <FeedPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/create"
        element={
          <ProtectedRoute>
            <CreatePostPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/post/:postId"
        element={
          <ProtectedRoute>
            <PostDetailsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/user/:username"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <MyProfileRedirect />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin"
        element={
          <ProtectedRoute requireAdmin>
            <AdminPage />
          </ProtectedRoute>
        }
      />

      {/* /showroom/* — прежние адреса Thymeleaf-страниц, ведём на SPA-эквиваленты */}
      <Route path="/showroom/home" element={<Navigate to="/" replace />} />
      <Route path="/showroom/createPost" element={<Navigate to="/create" replace />} />
      <Route path="/showroom/profile" element={<Navigate to="/profile" replace />} />

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
