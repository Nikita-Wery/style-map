import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import type { CurrentUser } from '../api/types'

interface AuthContextValue {
  user: CurrentUser | null
  /** true, пока идёт первичная проверка сессии на бэкенде. */
  initializing: boolean
  login: (username: string, password: string) => Promise<CurrentUser>
  logout: () => Promise<void>
  refresh: () => Promise<CurrentUser | null>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [initializing, setInitializing] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const current = await api.auth.me()
      setUser(current)
      return current
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setUser(null)
        return null
      }
      throw error
    }
  }, [])

  // При старте приложения проверяем, есть ли активная сессия.
  // Этот же запрос заставляет бэкенд выставить cookie XSRF-TOKEN.
  useEffect(() => {
    let cancelled = false
    refresh()
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) {
          setInitializing(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [refresh])

  const login = useCallback(async (username: string, password: string) => {
    const current = await api.auth.login(username, password)
    setUser(current)
    return current
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.auth.logout()
    } finally {
      setUser(null)
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, initializing, login, logout, refresh }),
    [user, initializing, login, logout, refresh],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth должен использоваться внутри <AuthProvider>')
  }
  return context
}
