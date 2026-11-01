import { Check, X } from 'lucide-react'
import type { EstadoOrden, EtapaOrden } from '@/api/dominio'
import { DETENIDOS, etapasAlcanzadas, PASOS, pasoDe } from '@/utils/etapasOrden'
import { ETIQUETA_ESTADO_ORDEN, ETIQUETA_ETAPA } from '@/utils/etiquetas'

interface EtapasOrdenProps {
  etapa: EtapaOrden
  estado: EstadoOrden
  /** Estados por los que pasó la orden, para no marcar como cumplidas las etapas que un cierre anticipado saltó */
  recorrido?: EstadoOrden[]
  /** Con selección, cada etapa alcanzada se abre al hacer clic */
  seleccionada?: EtapaOrden
  onElegir?: (etapa: EtapaOrden) => void
}

/** Ciclo de la orden (R2.1): etapas cumplidas, la actual con su estado y las que faltan */
export function EtapasOrden({ etapa, estado, recorrido = [], seleccionada, onElegir }: EtapasOrdenProps) {
  const actual = PASOS.indexOf(pasoDe(etapa))
  const cerrada = estado === 'CERRADA'
  // Una orden cancelada no recorrió las etapas: solo se marca el cierre
  const cancelada = estado === 'CANCELADA'
  const detenida = DETENIDOS.includes(estado)
  const alcanzadas = etapasAlcanzadas(etapa, recorrido)

  return (
    <ol className="flex flex-wrap items-start gap-y-3" aria-label="Etapas del ciclo de mantenimiento">
      {PASOS.map((paso, i) => {
        const hecho = cerrada ? alcanzadas.has(paso) || paso === 'CIERRE' : !cancelada && i < actual
        const enCurso = !cerrada && i === actual
        const fallo = enCurso && detenida
        const etiqueta = etapa === 'INCIDENCIA' && i === actual ? 'Incidencia' : ETIQUETA_ETAPA[paso]
        const elegible = !!onElegir && (alcanzadas.has(paso) || enCurso)
        const elegida = elegible && seleccionada === paso

        const contenido = (
          <>
            <span
              className={`flex h-7 w-7 items-center justify-center rounded-full border-2 text-xs font-semibold ${
                fallo
                  ? 'border-danger bg-danger text-white'
                  : hecho
                    ? 'border-success bg-success text-white'
                    : enCurso
                      ? 'border-accent bg-accent-soft text-ink'
                      : 'border-line bg-panel text-ink-faint'
              } ${elegida ? 'ring-2 ring-accent ring-offset-2 ring-offset-panel' : ''}`}
            >
              {fallo || (enCurso && cancelada) ? (
                <X className="h-4 w-4" aria-hidden="true" />
              ) : hecho ? (
                <Check className="h-4 w-4" aria-hidden="true" />
              ) : (
                i + 1
              )}
            </span>
            <span
              className={`text-xs ${
                elegida ? 'font-semibold text-accent underline underline-offset-4' : enCurso ? 'font-semibold text-ink' : hecho ? 'text-ink' : 'text-ink-faint'
              }`}
            >
              {etiqueta}
            </span>
            {enCurso && ETIQUETA_ESTADO_ORDEN[estado] !== etiqueta && (
              <span className={`text-xs ${fallo ? 'text-danger' : 'text-ink-soft'}`}>{ETIQUETA_ESTADO_ORDEN[estado]}</span>
            )}
          </>
        )

        return (
          <li key={paso} className="flex min-w-[6.5rem] flex-1 items-start" aria-current={enCurso ? 'step' : undefined}>
            {elegible ? (
              <button
                type="button"
                onClick={() => onElegir?.(paso)}
                aria-pressed={elegida}
                title={`Ver la etapa de ${ETIQUETA_ETAPA[paso].toLowerCase()}`}
                className="flex flex-col items-center gap-1.5 rounded-lg px-1 py-1 text-center transition-colors hover:bg-panel-muted"
              >
                {contenido}
              </button>
            ) : (
              <div className="flex flex-col items-center gap-1.5 px-1 py-1 text-center">{contenido}</div>
            )}
            {i < PASOS.length - 1 && (
              <span aria-hidden="true" className={`mt-[1.15rem] h-0.5 flex-1 rounded ${hecho ? 'bg-success' : 'bg-line'}`} />
            )}
          </li>
        )
      })}
    </ol>
  )
}
