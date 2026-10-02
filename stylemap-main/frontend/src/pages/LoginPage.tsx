import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert } from '../components/Alert'
import { FullPageLoader } from '../components/FullPageLoader'

interface LocationState {
  from?: string
  message?: string
}

export default function LoginPage() {
  const { user, initializing, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state ?? {}) as LocationState

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const message = state.message ?? null
  const loggedOut = new URLSearchParams(location.search).get('logout') !== null

  if (initializing) {
    return <FullPageLoader label="Проверяем сессию…" />
  }

  if (user) {
    return <Navigate to={state.from ?? '/'} replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(username.trim(), password)
      navigate(state.from ?? '/', { replace: true })
    } catch (caught) {
      setError(
        caught instanceof ApiError ? caught.displayMessage : 'Не удалось войти. Попробуйте ещё раз.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-5">
      <div className="card w-full max-w-[400px] p-10 text-center">
        <h2 className="mb-6 text-2xl">Вход в аккаунт</h2>

        {message && (
          <div className="mb-4">
            <Alert variant="success">{message}</Alert>
          </div>
        )}

        {loggedOut && (
          <div className="mb-4">
            <Alert variant="success">Вы вышли из аккаунта.</Alert>
          </div>
        )}

        {error && (
          <div className="mb-4">
            <Alert variant="error">{error}</Alert>
          </div>
        )}

        <form className="flex flex-col gap-3.5" onSubmit={handleSubmit}>
          <input
            type="text"
            className="field"
            placeholder="Логин"
            autoComplete="username"
            required
            value={username}
            onChange={event => setUsername(event.target.value)}
          />
          <input
            type="password"
            className="field"
            placeholder="Пароль"
            autoComplete="current-password"
            required
            value={password}
            onChange={event => setPassword(event.target.value)}
          />
          <button type="submit" className="btn w-full" disabled={submitting}>
            {submitting ? 'Входим…' : 'Войти'}
          </button>
        </form>

        <a
          href="/oauth2/authorization/google"
          className="btn btn-ghost mt-3.5 w-full"
          style={{ width: '100%' }}
        >
          <img
            src="https://www.gstatic.com/images/branding/product/1x/gsa_512dp.png"
            width={20}
            height={20}
            alt=""
          />
          Войти через Google
        </a>

        <p className="mt-6 text-sm text-muted">
          Нет аккаунта?{' '}
          <Link to="/register" className="font-semibold text-ink underline">
            Регистрация
          </Link>
        </p>
      </div>
    </div>
  )
}
