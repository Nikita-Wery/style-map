import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

interface TopNavProps {
  showBack?: boolean
  /** Дополнительные элементы в левой части навигации. */
  children?: ReactNode
}

export function TopNav({ showBack = false, children }: TopNavProps) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  // Закрываем выпадающее меню по клику вне него.
  useEffect(() => {
    function handleDocumentClick(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('click', handleDocumentClick)
    return () => document.removeEventListener('click', handleDocumentClick)
  }, [])

  async function handleLogout() {
    setOpen(false)
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="sticky top-2.5 z-50 mx-auto mb-8 flex max-w-[1200px] items-center justify-between rounded-2xl bg-white/80 px-5 py-3.5 shadow-[0_4px_24px_rgba(0,0,0,0.04)] backdrop-blur-xl">
      <div className="flex items-center gap-2.5">
        {showBack && (
          <button type="button" className="nav-btn" onClick={() => navigate(-1)}>
            ⬅ Назад
          </button>
        )}
        <Link to="/" className="px-4 py-2 text-lg font-semibold tracking-tight">
          StyleMap
        </Link>
        {children}
      </div>

      <div className="relative" ref={containerRef}>
        <button type="button" className="nav-btn" onClick={() => setOpen(previous => !previous)}>
          {user ? user.username : 'Профиль'} ▾
        </button>

        {open && (
          <div className="absolute right-0 top-[120%] flex min-w-[200px] flex-col overflow-hidden rounded-2xl border border-line bg-white shadow-[0_12px_40px_rgba(0,0,0,0.1)]">
            <Link
              to="/profile"
              className="border-b border-[#f5f5f7] px-5 py-3 text-sm hover:bg-[#f5f5f7]"
              onClick={() => setOpen(false)}
            >
              На страницу профиля
            </Link>
            <Link
              to="/create"
              className="border-b border-[#f5f5f7] px-5 py-3 text-sm hover:bg-[#f5f5f7]"
              onClick={() => setOpen(false)}
            >
              Создать аутфит
            </Link>
            {user?.role === 'ADMIN' && (
              <Link
                to="/admin"
                className="border-b border-[#f5f5f7] px-5 py-3 text-sm hover:bg-[#f5f5f7]"
                onClick={() => setOpen(false)}
              >
                Админ-панель
              </Link>
            )}
            <button
              type="button"
              onClick={handleLogout}
              className="cursor-pointer border-none bg-transparent px-5 py-3 text-left text-sm text-danger hover:bg-[#f5f5f7]"
            >
              Выйти из аккаунта
            </button>
          </div>
        )}
      </div>
    </header>
  )
}
