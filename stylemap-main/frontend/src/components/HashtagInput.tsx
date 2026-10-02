import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { HashtagSuggestion } from '../api/types'

interface HashtagInputProps {
  value: string[]
  onChange: (next: string[]) => void
  label?: string
}

/**
 * Автокомплит хештегов с бэкенда + возможность добавить свой тег вручную.
 * Выбранные теги показываются «пилюлями» с крестиком.
 */
export function HashtagInput({ value, onChange, label = 'Хештеги' }: HashtagInputProps) {
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState<HashtagSuggestion[]>([])
  const [hasNext, setHasNext] = useState(false)
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  // Дебаунс + загрузка подсказок.
  useEffect(() => {
    const trimmed = query.trim()
    if (trimmed.length < 1) {
      setSuggestions([])
      setOpen(false)
      return
    }

    let cancelled = false
    const timer = window.setTimeout(async () => {
      setLoading(true)
      try {
        const result = await api.hashtags.search(trimmed, page, 5)
        if (cancelled) return
        setSuggestions(previous => (page === 0 ? result.tags : [...previous, ...result.tags]))
        setHasNext(result.hasNext)
        setOpen(true)
      } catch {
        if (!cancelled) {
          setSuggestions([])
          setOpen(false)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }, 300)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [query, page])

  // Клик вне компонента закрывает список подсказок.
  useEffect(() => {
    function handleDocumentClick(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('click', handleDocumentClick)
    return () => document.removeEventListener('click', handleDocumentClick)
  }, [])

  function addTag(tag: string) {
    const normalized = tag.trim().replaceAll('#', '').toLowerCase()
    if (normalized && !value.includes(normalized)) {
      onChange([...value, normalized])
    }
    setQuery('')
    setPage(0)
    setSuggestions([])
    setOpen(false)
  }

  function removeTag(tag: string) {
    onChange(value.filter(item => item !== tag))
  }

  function handleQueryChange(next: string) {
    setQuery(next)
    setPage(0)
    setSuggestions([])
  }

  return (
    <div ref={containerRef} className="relative">
      <label className="mb-2 block font-semibold">{label}</label>

      <div className="flex gap-2.5">
        <input
          type="text"
          className="field flex-1"
          value={query}
          autoComplete="off"
          placeholder="Начни вводить, например anime"
          onChange={event => handleQueryChange(event.target.value)}
          onKeyDown={event => {
            if (event.key === 'Enter') {
              event.preventDefault()
              if (query.trim()) addTag(query)
            }
          }}
        />
        <button type="button" className="btn btn-ghost" onClick={() => addTag(query)}>
          Добавить
        </button>
      </div>

      {open && (suggestions.length > 0 || loading) && (
        <div className="scroll-thin absolute left-0 right-0 z-20 mt-2.5 max-h-56 overflow-y-auto rounded-xl border border-line bg-white shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
          {suggestions.map(suggestion => (
            <button
              key={suggestion.path}
              type="button"
              className="block w-full cursor-pointer border-b border-[#f5f5f7] bg-transparent px-4 py-3 text-left text-sm hover:bg-[#f5f5f7]"
              onClick={() => addTag(suggestion.path)}
            >
              #{suggestion.path}
            </button>
          ))}

          {hasNext && (
            <button
              type="button"
              className="w-full cursor-pointer border-none bg-[#e8e8ed] px-4 py-2.5 text-sm font-medium hover:bg-line"
              onClick={() => setPage(previous => previous + 1)}
            >
              Загрузить ещё…
            </button>
          )}
        </div>
      )}

      {value.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2">
          {value.map(tag => (
            <button
              key={tag}
              type="button"
              onClick={() => removeTag(tag)}
              className="cursor-pointer rounded-full border-none bg-ink px-3.5 py-1.5 text-[13px] text-white transition hover:opacity-80"
            >
              #{tag} ✕
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
