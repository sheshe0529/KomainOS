import type { LucideIcon } from 'lucide-react'
import { CircleCheck, CircleAlert, CircleX, CircleDashed } from 'lucide-react'

/** Recibe un tono semántico y una etiqueta ya traducida, no conoce el vocabulario de cada dominio */
export type StatusTone = 'success' | 'warning' | 'danger' | 'neutral'

const TONE_STYLES: Record<StatusTone, string> = {
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  neutral: 'bg-panel-muted text-ink-soft',
}

const TONE_ICONS: Record<StatusTone, LucideIcon> = {
  success: CircleCheck,
  warning: CircleAlert,
  danger: CircleX,
  neutral: CircleDashed,
}

interface StatusPillProps {
  tone: StatusTone
  label: string
}

export function StatusPill({ tone, label }: StatusPillProps) {
  const Icon = TONE_ICONS[tone]
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-medium ${TONE_STYLES[tone]}`}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {label}
    </span>
  )
}
