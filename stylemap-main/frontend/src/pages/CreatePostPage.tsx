import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { OutfitItem } from '../api/types'
import { Alert } from '../components/Alert'
import { HashtagInput } from '../components/HashtagInput'
import { OutfitItemEditor } from '../components/OutfitItemEditor'
import { TopNav } from '../components/TopNav'

type Status = { variant: 'error' | 'success' | 'info'; text: string } | null

export default function CreatePostPage() {
  const navigate = useNavigate()

  const [description, setDescription] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [hashtags, setHashtags] = useState<string[]>([])
  const [items, setItems] = useState<OutfitItem[]>([])
  const [status, setStatus] = useState<Status>(null)
  const [submitting, setSubmitting] = useState(false)

  // Освобождаем blob-URL предыдущего превью.
  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl)
      }
    }
  }, [previewUrl])

  const previewIsVideo = file?.type.startsWith('video/') ?? false

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()

    if (!file) {
      setStatus({ variant: 'error', text: 'Выберите фото или видео аутфита.' })
      return
    }

    setSubmitting(true)
    setStatus({ variant: 'info', text: 'Отправляем аутфит…' })

    try {
      const result = await api.posts.create(file, {
        description,
        hashtags,
        outfitItems: items,
      })
      setStatus({ variant: 'success', text: result.message ?? 'Аутфит успешно опубликован!' })
      window.setTimeout(() => navigate('/'), 400)
    } catch (caught) {
      setStatus({
        variant: 'error',
        text: caught instanceof ApiError ? caught.displayMessage : 'Ошибка сети. Попробуйте ещё раз.',
      })
      setSubmitting(false)
    }
  }

  return (
    <div className="p-5">
      <TopNav showBack />

      <div className="card mx-auto max-w-[800px] p-8">
        <h2 className="mb-8 text-center text-2xl">Опубликовать аутфит</h2>

        <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
          <div className="rounded-xl border border-line p-5">
            <label className="mb-2 block font-semibold">Описание</label>
            <textarea
              className="field"
              rows={3}
              required
              placeholder="Расскажите о своём образе…"
              value={description}
              onChange={event => setDescription(event.target.value)}
            />

            <label className="mb-2 mt-4 block font-semibold">Медиа (фото или видео)</label>
            <input
              type="file"
              accept="image/*,video/*"
              required
              className="field cursor-pointer border-dashed bg-white p-2.5"
              onChange={event => {
                const selected = event.target.files?.[0] ?? null
                setFile(selected)
                setPreviewUrl(selected ? URL.createObjectURL(selected) : null)
              }}
            />

            {previewUrl && (
              <div className="mt-4 text-center">
                {previewIsVideo ? (
                  <video
                    src={previewUrl}
                    controls
                    className="max-h-[400px] max-w-full rounded-xl shadow-[0_4px_12px_rgba(0,0,0,0.08)]"
                  />
                ) : (
                  <img
                    src={previewUrl}
                    alt="Превью аутфита"
                    className="max-h-[400px] max-w-full rounded-xl shadow-[0_4px_12px_rgba(0,0,0,0.08)]"
                  />
                )}
              </div>
            )}
          </div>

          <div className="rounded-xl border border-line p-5">
            <HashtagInput value={hashtags} onChange={setHashtags} />
          </div>

          <div className="rounded-xl border border-line p-5">
            <h3 className="mb-4 text-lg">Одежда на фото</h3>
            <OutfitItemEditor items={items} onChange={setItems} />
          </div>

          {status && <Alert variant={status.variant}>{status.text}</Alert>}

          <button type="submit" className="btn w-full py-4 text-base" disabled={submitting}>
            {submitting ? 'Публикуем…' : 'Опубликовать пост'}
          </button>
        </form>
      </div>
    </div>
  )
}
