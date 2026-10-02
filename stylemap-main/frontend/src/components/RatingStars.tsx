import { useState } from 'react'

interface RatingStarsProps {
  average: number | null
  current: number | null
  onRate: (score: number) => void
  disabled?: boolean
}

const SCORES = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

export function RatingStars({ average, current, onRate, disabled = false }: RatingStarsProps) {
  const [hovered, setHovered] = useState<number | null>(null)
  const active = hovered ?? current ?? 0

  return (
    <div className="text-center">
      <div className="mb-2 text-sm text-muted">
        Средний рейтинг: <b className="text-ink">{average ?? 0}</b>/10
      </div>

      <div className="flex justify-center gap-0.5" onMouseLeave={() => setHovered(null)}>
        {SCORES.map(score => (
          <button
            key={score}
            type="button"
            disabled={disabled}
            onMouseEnter={() => setHovered(score)}
            onClick={() => onRate(score)}
            title={`Оценить на ${score}`}
            className={`cursor-pointer border-none bg-transparent p-0 text-2xl leading-none transition-colors disabled:cursor-not-allowed ${
              score <= active ? 'text-[#ffcc00]' : 'text-[#e8e8ed]'
            }`}
          >
            ★
          </button>
        ))}
      </div>

      {current != null && <div className="mt-2 text-xs text-muted">Ваша оценка: {current}/10</div>}
    </div>
  )
}
