import type { StatusTone } from '@/components/ui/StatusPill'

const PUNTO_TONO: Record<StatusTone, string> = {
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  neutral: 'bg-ink-faint',
}

interface ContadorProps {
  etiqueta: string
  valor: number
  tono?: StatusTone
  activo?: boolean
  /** Si se indica, el contador filtra la tabla */
  onClick?: () => void
}

export function Contador({ etiqueta, valor, tono = 'neutral', activo, onClick }: ContadorProps) {
  const contenido = (
    <>
      <span className="flex items-center gap-1.5 text-xs text-ink-soft">
        <span className={`h-2 w-2 rounded-full ${PUNTO_TONO[tono]}`} aria-hidden="true" />
        {etiqueta}
      </span>
      <span className="font-mono text-xl font-semibold tabular-nums text-ink">{valor}</span>
    </>
  )
  const clases = `flex flex-col items-start gap-1 rounded-lg border px-3 py-2 text-left transition ${
    activo ? 'border-accent bg-accent-soft' : 'border-line'
  }`
  return onClick ? (
    <button type="button" onClick={onClick} aria-pressed={activo} className={`${clases} hover:bg-panel-muted`}>
      {contenido}
    </button>
  ) : (
    <div className={clases}>{contenido}</div>
  )
}
