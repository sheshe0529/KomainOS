import type { ButtonHTMLAttributes, ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { LoaderCircle } from 'lucide-react'

type Variante = 'primario' | 'secundario' | 'peligro' | 'fantasma'

const ESTILOS: Record<Variante, string> = {
  primario: 'bg-accent text-accent-ink hover:opacity-90',
  secundario: 'border border-line bg-panel text-ink hover:bg-panel-muted',
  peligro: 'bg-danger text-white hover:opacity-90',
  fantasma: 'text-ink-soft hover:bg-panel-muted hover:text-ink',
}

interface BotonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: Variante
  icono?: LucideIcon
  cargando?: boolean
  children?: ReactNode
}

export function Boton({ variante = 'secundario', icono: Icono, cargando, children, className = '', disabled, ...resto }: BotonProps) {
  return (
    <button
      type="button"
      disabled={disabled || cargando}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${ESTILOS[variante]} ${className}`}
      {...resto}
    >
      {cargando ? (
        <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
      ) : (
        Icono && <Icono className="h-4 w-4" aria-hidden="true" />
      )}
      {children}
    </button>
  )
}

interface BotonIconoProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icono: LucideIcon
  etiqueta: string
}

/** La etiqueta queda como texto accesible y tooltip */
export function BotonIcono({ icono: Icono, etiqueta, className = '', ...resto }: BotonIconoProps) {
  return (
    <button
      type="button"
      title={etiqueta}
      aria-label={etiqueta}
      className={`inline-flex h-8 w-8 items-center justify-center rounded-lg text-ink-soft transition-colors hover:bg-panel-muted hover:text-ink disabled:cursor-not-allowed disabled:opacity-40 ${className}`}
      {...resto}
    >
      <Icono className="h-4 w-4" aria-hidden="true" />
    </button>
  )
}
