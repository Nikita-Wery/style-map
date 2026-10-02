export function FullPageLoader({ label = 'Загрузка…' }: { label?: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="flex flex-col items-center gap-3">
        <span className="h-8 w-8 animate-spin rounded-full border-2 border-line border-t-ink" />
        <span className="text-sm font-medium text-muted">{label}</span>
      </div>
    </div>
  )
}
