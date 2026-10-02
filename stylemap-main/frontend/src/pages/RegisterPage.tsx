import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { api } from '../api/client'
import { Alert } from '../components/Alert'

export default function RegisterPage() {
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setErrors([])

    try {
      await api.auth.register({ username: username.trim(), email: email.trim(), password })
      navigate('/confirm', { state: { email: email.trim() }, replace: true })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setErrors(
          caught.validationErrors.length > 0 ? caught.validationErrors : [caught.displayMessage],
        )
      } else {
        setErrors(['Не удалось зарегистрироваться. Попробуйте ещё раз.'])
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-5">
      <div className="card w-full max-w-[400px] p-10">
        <h2 className="mb-6 text-center text-2xl">Создать аккаунт</h2>

        {errors.length > 0 && (
          <div className="mb-4">
            <Alert variant="error">
              <ul className="m-0 list-disc pl-5">
                {errors.map((message, index) => (
                  <li key={index}>{message}</li>
                ))}
              </ul>
            </Alert>
          </div>
        )}

        <form className="flex flex-col gap-3.5" onSubmit={handleSubmit}>
          <input
            type="email"
            className="field"
            placeholder="Электронная почта"
            autoComplete="email"
            required
            value={email}
            onChange={event => setEmail(event.target.value)}
          />
          <input
            type="text"
            className="field"
            placeholder="Имя пользователя"
            autoComplete="username"
            required
            minLength={4}
            maxLength={100}
            value={username}
            onChange={event => setUsername(event.target.value)}
          />
          <input
            type="password"
            className="field"
            placeholder="Пароль"
            autoComplete="new-password"
            required
            minLength={4}
            maxLength={50}
            value={password}
            onChange={event => setPassword(event.target.value)}
          />
          <button type="submit" className="btn mt-2.5 w-full" disabled={submitting}>
            {submitting ? 'Создаём…' : 'Зарегистрироваться'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-muted">
          Уже есть аккаунт?{' '}
          <Link to="/login" className="font-semibold text-ink underline">
            Войти
          </Link>
        </p>
      </div>
    </div>
  )
}
