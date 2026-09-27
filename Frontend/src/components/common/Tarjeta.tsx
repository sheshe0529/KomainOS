import type { ReactNode } from 'react'

interface TarjetaProps {
  titulo: string
  acciones?: ReactNode
  children: ReactNode
  className?: string
}

/** Tarjeta de contenido con título, como en la ficha del servidor de las pantallas preliminares. */
export function Tarjeta({ titulo, acciones, children, className = '' }: TarjetaProps) {
  return (
    <section className={`rounded-xl border border-line bg-panel p-5 ${className}`}>
      <div className="mb-4 flex items-center justify-between gap-3">
        <h3 className="text-base font-semibold text-ink">{titulo}</h3>
        {acciones && <div className="flex items-center gap-1">{acciones}</div>}
      </div>
      {children}
    </section>
  )
}

interface DatoProps {
  etiqueta: string
  valor?: ReactNode
  mono?: boolean
}

export function Dato({ etiqueta, valor, mono }: DatoProps) {
  return (
    <div>
      <dt className="text-xs text-ink-faint">{etiqueta}</dt>
      <dd className={`mt-0.5 text-sm text-ink ${mono ? 'font-mono' : ''}`}>{valor ?? '—'}</dd>
    </div>
  )
}
