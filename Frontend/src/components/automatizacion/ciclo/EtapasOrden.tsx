import { Check, X } from 'lucide-react'
import type { EstadoOrden, EtapaOrden } from '@/api/dominio'
import { ETIQUETA_ESTADO_ORDEN, ETIQUETA_ETAPA } from '@/utils/etiquetas'

interface EtapasOrdenProps {
  etapa: EtapaOrden
  estado: EstadoOrden
  /** Estados por los que pasó la orden, para no marcar como cumplidas las etapas que un cierre anticipado saltó */
  recorrido?: EstadoOrden[]
}

const PASOS: EtapaOrden[] = ['PLANIFICACION', 'EVALUACION', 'AUTORIZACION', 'EJECUCION', 'VALIDACION', 'CIERRE']

/** Misma correspondencia que EstadoOrden.etapa() del backend */
const ETAPA_DE: Record<EstadoOrden, EtapaOrden> = {
  PROGRAMADA: 'PLANIFICACION',
  REPROGRAMADA: 'PLANIFICACION',
  EN_EVALUACION: 'EVALUACION',
  SIN_IMPLEMENTACION: 'EVALUACION',
  PENDIENTE_AUTORIZACION: 'AUTORIZACION',
  AUTORIZADA: 'AUTORIZACION',
  RECHAZADA: 'AUTORIZACION',
  AUTORIZACION_VENCIDA: 'AUTORIZACION',
  EN_COLA: 'EJECUCION',
  EN_EJECUCION: 'EJECUCION',
  PENDIENTE_VALIDACION: 'VALIDACION',
  VALIDACION_VENCIDA: 'VALIDACION',
  INCIDENCIA: 'INCIDENCIA',
  CANCELADA: 'CIERRE',
  CERRADA: 'CIERRE',
}

/** Estados que detienen el ciclo en su etapa sin completarla */
const DETENIDOS: EstadoOrden[] = ['SIN_IMPLEMENTACION', 'RECHAZADA', 'AUTORIZACION_VENCIDA', 'VALIDACION_VENCIDA', 'INCIDENCIA']

/** Ciclo de la orden (R2.1): etapas cumplidas, la actual con su estado y las que faltan */
export function EtapasOrden({ etapa, estado, recorrido = [] }: EtapasOrdenProps) {
  // La incidencia ocurre al ejecutar o al validar: ocupa el lugar de la validación
  const actual = etapa === 'INCIDENCIA' ? PASOS.indexOf('VALIDACION') : PASOS.indexOf(etapa)
  const cerrada = estado === 'CERRADA'
  // Una orden cancelada no recorrió las etapas: solo se marca el cierre
  const cancelada = estado === 'CANCELADA'
  const detenida = DETENIDOS.includes(estado)
  const alcanzadas = new Set<EtapaOrden>(['PLANIFICACION', ...recorrido.map((e) => ETAPA_DE[e])])
  if (alcanzadas.has('INCIDENCIA')) alcanzadas.add('VALIDACION')

  return (
    <ol className="flex flex-wrap items-start gap-y-3" aria-label="Etapas del ciclo de mantenimiento">
      {PASOS.map((paso, i) => {
        const hecho = cerrada ? alcanzadas.has(paso) || paso === 'CIERRE' : !cancelada && i < actual
        const enCurso = !cerrada && i === actual
        const fallo = enCurso && detenida
        const etiqueta = etapa === 'INCIDENCIA' && i === actual ? 'Incidencia' : ETIQUETA_ETAPA[paso]
        return (
          <li key={paso} className="flex min-w-[6.5rem] flex-1 items-start" aria-current={enCurso ? 'step' : undefined}>
            <div className="flex flex-col items-center gap-1.5 px-1 text-center">
              <span
                className={`flex h-7 w-7 items-center justify-center rounded-full border-2 text-xs font-semibold ${
                  fallo
                    ? 'border-danger bg-danger text-white'
                    : hecho
                      ? 'border-success bg-success text-white'
                      : enCurso
                        ? 'border-accent bg-accent-soft text-ink'
                        : 'border-line bg-panel text-ink-faint'
                }`}
              >
                {fallo || (enCurso && cancelada) ? (
                  <X className="h-4 w-4" aria-hidden="true" />
                ) : hecho ? (
                  <Check className="h-4 w-4" aria-hidden="true" />
                ) : (
                  i + 1
                )}
              </span>
              <span className={`text-xs ${enCurso ? 'font-semibold text-ink' : hecho ? 'text-ink' : 'text-ink-faint'}`}>
                {etiqueta}
              </span>
              {enCurso && <span className={`text-xs ${fallo ? 'text-danger' : 'text-ink-soft'}`}>{ETIQUETA_ESTADO_ORDEN[estado]}</span>}
            </div>
            {i < PASOS.length - 1 && (
              <span
                aria-hidden="true"
                className={`mt-3.5 h-0.5 flex-1 rounded ${hecho ? 'bg-success' : 'bg-line'}`}
              />
            )}
          </li>
        )
      })}
    </ol>
  )
}
