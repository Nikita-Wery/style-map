import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { HashtagSuggestion } from '../api/types'

interface HashtagSearchBoxProps {
  onSelect: (path: string) => void
  placeholder?: string
}

/** Строка поиска аутфитов по хештегу с подсказками из /api/hashtags/search. */
export function HashtagSearchBox({
  onSelect,
  placeholder = 'Искать аутфиты (например, anime.bleach)…',
}: HashtagSearchBoxProps) {
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState<HashtagSuggestion[]>([])
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const trimmed = query.trim()
    if (trimmed.length < 1) {
      setSuggestions([])
      setOpen(false)
      return
    }

    let cancelled = false
    const timer = window.setTimeout(async () => {
      try {
        const result = await api.hashtags.search(trimmed, 0, 5)
        if (cancelled) return
        setSuggestions(result.tags)
        setOpen(result.tags.length > 0)
      } catch {
        if (!cancelled) {
          setSuggestions([])
          setOpen(false)
        }
      }
    }, 300)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [query])

  useEffect(() => {
    function handleDocumentClick(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('click', handleDocumentClick)
    return () => document.removeEventListener('click', handleDocumentClick)
  }, [])

  function submit(tag: string) {
    const normalized = tag.trim().replaceAll('#', '')
    if (normalized) {
      onSelect(normalized)
    }
    setQuery('')
    setSuggestions([])
    setOpen(false)
  }

  return (
    <div ref={containerRef} className="relative mx-auto mb-7 max-w-[600px]">
      <input
        type="text"
        className="field rounded-full px-6 py-4 text-base shadow-[0_4px_12px_rgba(0,0,0,0.05)]"
        placeholder={placeholder}
        autoComplete="off"
        value={query}
        onChange={event => setQuery(event.target.value)}
        onKeyDown={event => {
          if (event.key === 'Enter') {
            event.preventDefault()
            submit(query)
          }
        }}
      />

      {open && (
        <div className="absolute left-0 right-0 top-full z-30 mt-2 overflow-hidden rounded-xl bg-white shadow-[0_10px_30px_rgba(0,0,0,0.1)]">
          {suggestions.map(suggestion => (
            <button
              key={suggestion.path}
              type="button"
              className="block w-full cursor-pointer border-b border-[#f5f5f7] bg-transparent px-5 py-3 text-left text-sm hover:bg-[#f5f5f7]"
              onClick={() => submit(suggestion.path)}
            >
              #{suggestion.path}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
