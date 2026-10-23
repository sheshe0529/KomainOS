import type { ReactNode } from 'react'
import { ChevronDown } from 'lucide-react'

interface SeccionAlcanceProps {
  /** Lugar en el orden de prioridad: 1 es la que gana */
  prioridad: number
  titulo: string
  /** Dato corto junto al título, visible aunque la sección esté plegada */
  resumen?: string
  /** Frase completa del resumen, como tooltip */
  resumenTitulo?: string
  abierta: boolean
  onAlternar: () => void
  children: ReactNode
}

/** Un bloque desplegable del detalle: las excepciones de un alcance (servidor, grupo, entorno o criticidad) */
export function SeccionAlcance({ prioridad, titulo, resumen, resumenTitulo, abierta, onAlternar, children }: SeccionAlcanceProps) {
  return (
    <section className="rounded-xl border border-line bg-panel">
      <h3>
        <button
          type="button"
          aria-expanded={abierta}
          onClick={onAlternar}
          className={`flex w-full items-center gap-3 px-5 py-3 transition-colors hover:bg-panel-muted ${abierta ? 'rounded-t-xl' : 'rounded-xl'}`}
        >
          <span
            className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-panel-muted text-xs font-semibold text-ink-soft"
            title={`Prioridad ${prioridad}`}
          >
            {prioridad}
          </span>
          <span className="min-w-0 flex-1 text-left text-base font-semibold text-ink">{titulo}</span>
          {resumen && (
            <span title={resumenTitulo} className="shrink-0 rounded-full bg-panel-muted px-2.5 py-0.5 text-xs font-medium tabular-nums text-ink-soft">
              {resumen}
            </span>
          )}
          <ChevronDown className={`h-4 w-4 shrink-0 text-ink-faint transition-transform ${abierta ? 'rotate-180' : ''}`} aria-hidden="true" />
        </button>
      </h3>
      {abierta && <div className="border-t border-line">{children}</div>}
    </section>
  )
}
