import type { Links, LinkDto, OutfitItem } from '../api/types'

interface OutfitItemEditorProps {
  items: OutfitItem[]
  onChange: (items: OutfitItem[]) => void
}

const COUNTRIES = ['RU', 'US', 'EU', 'CN']
const CURRENCIES = ['RUB', 'USD', 'EUR', 'CNY', 'KZT', 'BYN']

/**
 * Редактор списка вещей «где купить»: вещь -> несколько ссылок по странам.
 * Используется и при создании поста, и при редактировании.
 */
export function OutfitItemEditor({ items, onChange }: OutfitItemEditorProps) {
  function addItem() {
    onChange([...items, { name: '', brand: '', links: {} }])
  }

  function removeItem(index: number) {
    onChange(items.filter((_, itemIndex) => itemIndex !== index))
  }

  function updateItem(index: number, patch: Partial<OutfitItem>) {
    onChange(items.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)))
  }

  function addLink(index: number) {
    const item = items[index]
    const taken = Object.keys(item.links)
    const country = COUNTRIES.find(candidate => !taken.includes(candidate)) ?? `C${taken.length + 1}`
    updateItem(index, {
      links: { ...item.links, [country]: { url: '', price: 0, currency: 'RUB' } },
    })
  }

  function updateLink(index: number, country: string, patch: Partial<LinkDto>) {
    const item = items[index]
    updateItem(index, {
      links: { ...item.links, [country]: { ...item.links[country], ...patch } },
    })
  }

  function removeLink(index: number, country: string) {
    const item = items[index]
    const links: Links = { ...item.links }
    delete links[country]
    updateItem(index, { links })
  }

  return (
    <div>
      {items.map((item, index) => (
        <div key={index} className="mb-4 rounded-xl border border-line p-4">
          <div className="mb-3 flex items-center gap-2.5">
            <input
              type="text"
              className="field flex-1"
              placeholder="Название (напр. Чёрный плащ)"
              value={item.name}
              onChange={event => updateItem(index, { name: event.target.value })}
            />
            <input
              type="text"
              className="field flex-1"
              placeholder="Бренд"
              value={item.brand ?? ''}
              onChange={event => updateItem(index, { brand: event.target.value })}
            />
            <button
              type="button"
              className="btn btn-danger btn-sm"
              title="Удалить вещь"
              onClick={() => removeItem(index)}
            >
              ✕
            </button>
          </div>

          {Object.entries(item.links).map(([country, link]) => (
            <div
              key={country}
              className="mt-2.5 flex flex-wrap items-center gap-2 border-t border-line pt-2.5"
            >
              <select
                className="field w-[90px]"
                value={country}
                onChange={event => {
                  const nextCountry = event.target.value
                  if (nextCountry === country || item.links[nextCountry]) return
                  const links: Links = {}
                  Object.entries(item.links).forEach(([key, value]) => {
                    links[key === country ? nextCountry : key] = value
                  })
                  updateItem(index, { links })
                }}
              >
                {[...new Set([...COUNTRIES, country])].map(code => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
              </select>

              <input
                type="url"
                className="field flex-1"
                placeholder="Ссылка (https://…)"
                value={link.url}
                onChange={event => updateLink(index, country, { url: event.target.value })}
              />

              <input
                type="number"
                step="0.01"
                className="field w-[110px]"
                placeholder="Цена"
                value={Number.isFinite(link.price) ? link.price : ''}
                onChange={event =>
                  updateLink(index, country, { price: Number.parseFloat(event.target.value) || 0 })
                }
              />

              <select
                className="field w-[100px]"
                value={link.currency}
                onChange={event => updateLink(index, country, { currency: event.target.value })}
              >
                {[...new Set([...CURRENCIES, link.currency])].map(code => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
              </select>

              <button
                type="button"
                className="btn btn-danger btn-sm"
                title="Удалить ссылку"
                onClick={() => removeLink(index, country)}
              >
                ✕
              </button>
            </div>
          ))}

          <button
            type="button"
            className="btn btn-ghost btn-sm mt-3"
            onClick={() => addLink(index)}
          >
            + Добавить ссылку (где купить)
          </button>
        </div>
      ))}

      <button type="button" className="btn btn-ghost w-full" onClick={addItem}>
        + Добавить вещь
      </button>
    </div>
  )
}
