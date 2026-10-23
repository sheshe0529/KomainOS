import { Info } from 'lucide-react'
import { ETIQUETA_ORIGEN, PRIORIDAD } from '@/utils/umbrales'

/** Una sola línea: la explicación completa queda en el tooltip */
export function ReglaDePrecedencia() {
  return (
    <div
      className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ink-soft"
      title="Cada servidor usa la primera excepción activa que lo alcance. El grupo cuenta solo en las órdenes de ese grupo. Una excepción solo cambia los valores o la lista; el operador y los niveles son los del umbral."
    >
      <Info className="h-4 w-4 text-ink-faint" aria-hidden="true" />
      <span>Prioridad</span>
      <ol className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
        {PRIORIDAD.map((origen, i) => (
          <li key={origen} className="flex items-center gap-1.5">
            <span className="flex items-center gap-1.5 rounded-md bg-panel-muted px-2 py-0.5 text-xs font-medium text-ink">
              <span className="text-ink-faint">{i + 1}</span>
              {ETIQUETA_ORIGEN[origen]}
            </span>
            {i < PRIORIDAD.length - 1 && <span aria-hidden="true">›</span>}
          </li>
        ))}
      </ol>
    </div>
  )
}
