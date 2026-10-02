import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { Alert } from '../components/Alert'

interface LocationState {
  email?: string
}

export default function ConfirmPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state ?? {}) as LocationState

  // Email приходит либо в state (после регистрации), либо в query (?email=...).
  const emailFromQuery = new URLSearchParams(location.search).get('email') ?? ''
  const email = state.email ?? emailFromQuery

  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!email) {
    return <Navigate to="/login" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      await api.auth.confirm(email, code.trim())
      navigate('/login', {
        replace: true,
        state: { message: 'Аккаунт успешно подтверждён! Теперь вы можете войти.' },
      })
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.displayMessage
          : 'Не удалось подтвердить аккаунт. Попробуйте ещё раз.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-5">
      <div className="card w-full max-w-[400px] p-10 text-center">
        <h2 className="mb-3 text-2xl">Проверьте почту</h2>
        <p className="mb-6 text-sm text-muted">
          Мы отправили код подтверждения на <b className="text-ink">{email}</b>.
        </p>

        {error && (
          <div className="mb-4">
            <Alert variant="error">{error}</Alert>
          </div>
        )}

        <form className="flex flex-col gap-3.5" onSubmit={handleSubmit}>
          <input
            type="text"
            className="field text-center text-2xl tracking-[8px]"
            placeholder="000000"
            inputMode="numeric"
            pattern="\d{6}"
            maxLength={6}
            required
            value={code}
            onChange={event => setCode(event.target.value.replace(/\D/g, ''))}
          />
          <button type="submit" className="btn w-full" disabled={submitting}>
            {submitting ? 'Проверяем…' : 'Подтвердить'}
          </button>
        </form>
      </div>
    </div>
  )
}
