import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center p-5">
      <div className="card max-w-[420px] p-10 text-center">
        <h1 className="mb-3 text-5xl">404</h1>
        <p className="mb-6 text-muted">Такой страницы не существует.</p>
        <Link to="/" className="btn">
          На главную
        </Link>
      </div>
    </div>
  )
}
