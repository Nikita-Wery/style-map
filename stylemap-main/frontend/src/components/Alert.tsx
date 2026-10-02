import type { ReactNode } from 'react'

type AlertVariant = 'error' | 'success' | 'info'

const VARIANT_CLASSES: Record<AlertVariant, string> = {
  error: 'border-danger/40 bg-[#fff5f5] text-danger',
  success: 'border-success/40 bg-[#f2fcf5] text-[#1f8f43]',
  info: 'border-line bg-white text-muted',
}

export function Alert({
  variant = 'info',
  children,
}: {
  variant?: AlertVariant
  children: ReactNode
}) {
  return (
    <div className={`rounded-xl border px-4 py-3 text-sm ${VARIANT_CLASSES[variant]}`}>
      {typeof children === 'string' ? <p className="m-0 whitespace-pre-line">{children}</p> : children}
    </div>
  )
}
