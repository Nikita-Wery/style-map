import { useEffect, useState } from 'react'
import { api } from '../api/client'

const TARGETS = ['RUB', 'USD', 'EUR']

interface CurrencyPriceProps {
  price: number
  currency: string
}

/**
 * Цена вещи с возможностью пересчитать её в другую валюту через /api/actions/convert-currency.
 */
export function CurrencyPrice({ price, currency }: CurrencyPriceProps) {
  const [target, setTarget] = useState(currency)
  const [display, setDisplay] = useState({ amount: price, currency })
  const [loading, setLoading] = useState(false)

  // Если пришла другая цена/валюта — показываем исходное значение.
  useEffect(() => {
    setTarget(currency)
    setDisplay({ amount: price, currency })
  }, [price, currency])

  async function handleChange(next: string) {
    setTarget(next)

    if (next.toUpperCase() === currency.toUpperCase()) {
      setDisplay({ amount: price, currency })
      return
    }

    setLoading(true)
    try {
      const result = await api.actions.convertCurrency(price, currency, next)
      if (result.status === 'success') {
        setDisplay({ amount: result.result, currency: result.currency })
      }
    } catch {
      setDisplay({ amount: price, currency })
      setTarget(currency)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex items-center gap-2.5">
      <span className={`font-semibold ${loading ? 'opacity-50' : ''}`}>
        {display.amount} {display.currency}
      </span>
      <select
        className="field w-auto px-2 py-1 text-xs"
        value={target}
        onChange={event => handleChange(event.target.value)}
      >
        {[...new Set([currency, ...TARGETS])].map(code => (
          <option key={code} value={code}>
            {code}
          </option>
        ))}
      </select>
    </div>
  )
}
