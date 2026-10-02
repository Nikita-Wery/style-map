import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { FullPageLoader } from './FullPageLoader'

interface ProtectedRouteProps {
  children: ReactNode
  requireAdmin?: boolean
}

/**
 * Пускает дальше только аутентифицированных пользователей.
 * Пока сессия проверяется — показываем лоадер, чтобы не мигать формой логина.
 */
export function ProtectedRoute({ children, requireAdmin = false }: ProtectedRouteProps) {
  const { user, initializing } = useAuth()
  const location = useLocation()

  if (initializing) {
    return <FullPageLoader label="Проверяем сессию…" />
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
  }

  if (requireAdmin && user.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
