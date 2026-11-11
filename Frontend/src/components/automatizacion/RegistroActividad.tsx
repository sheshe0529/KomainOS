import { ChevronDown, CircleCheck, CircleX, Dot, LoaderCircle, TriangleAlert } from 'lucide-react'
import { operacionApi } from '@/api/automatizacion'
import type { TareaMopRespuesta } from '@/api/types'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { useRefrescoConsulta } from '@/hooks/useRefrescoConsulta'
import type { EventoActividad } from '@/utils/actividadEjecucion'
import { deRespuestas } from '@/utils/actividadEjecucion'
import { diagnosticarFallo } from '@/utils/automatizacion'
import type { DiagnosticoFallo } from '@/utils/automatizacion'
import { SalidaTerminal } from './SalidaTerminal'

const ESTADOS = {
  EN_CURSO: { icono: LoaderCircle, tono: 'bg-accent-soft text-accent' },
  COMPLETADO: { icono: CircleCheck, tono: 'bg-success-soft text-success' },
  FALLIDO: { icono: CircleX, tono: 'bg-danger-soft text-danger' },
}

const FECHA = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'medium', hour12: false })

/**
 * Qué falló, el detalle y cómo resolverlo. Con causas, además, dónde buscar si la sugerencia no basta; el mensaje
 * técnico original queda plegado
 */
export function DiagnosticoActividad({ fallo, conCausas = false }: { fallo: DiagnosticoFallo; conCausas?: boolean }) {
  return (
    <div className="space-y-1.5 rounded-lg border border-danger/20 bg-danger-soft/40 px-3 py-2.5 text-xs leading-relaxed">
      <p className="font-semibold text-danger">{fallo.titulo}</p>
      {fallo.detalle && <p className="whitespace-pre-wrap break-words font-mono text-ink">{fallo.detalle}</p>}
      {fallo.sugerencia && <p className="text-ink-soft">{fallo.sugerencia}</p>}
      {conCausas && fallo.causas && fallo.causas.length > 0 && (
        <div>
          <p className="font-medium text-ink">Posibles causas</p>
          <ul className="mt-0.5 list-disc space-y-0.5 pl-4 text-ink-soft">
            {fallo.causas.map((causa) => <li key={causa}>{causa}</li>)}
          </ul>
        </div>
      )}
      {fallo.mensajeOriginal && fallo.mensajeOriginal !== fallo.detalle && (
        <details>
          <summary className="w-fit cursor-pointer rounded font-medium text-ink-soft hover:text-ink">Mensaje técnico</summary>
          <p className="mt-1 whitespace-pre-wrap break-words font-mono text-ink-soft">{fallo.mensajeOriginal}</p>
        </details>
      )}
    </div>
  )
}

function RegistroActividad({ eventos }: { eventos: EventoActividad[] }) {
  if (eventos.length === 0) {
    return <p className="px-4 py-3 text-sm text-ink-soft">Aún no hay actividad registrada.</p>
  }

  return (
    <ol aria-label="Eventos de ejecución" className="px-4 py-1">
      {eventos.map((e) => {
        const estado = e.estado && ESTADOS[e.estado]
        const advertencia = !estado && e.nivel === 'WARNING'
        const Icono = estado ? estado.icono : advertencia ? TriangleAlert : Dot
        const tono = e.nivel === 'WARNING' ? 'bg-warning-soft text-warning' : estado ? estado.tono : 'bg-panel-muted text-ink-faint'
        const fecha = e.fecha ? Date.parse(e.fecha) : NaN
        return (
          <li key={e.clave} className="relative flex items-start gap-3 py-2.5 before:absolute before:top-6 before:-bottom-4 before:left-3 before:w-px before:bg-line last:before:hidden">
            <span className={`relative z-10 flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${tono}`}>
              <Icono className={`h-4 w-4 ${e.estado === 'EN_CURSO' ? 'motion-safe:animate-spin' : ''}`} aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex min-h-6 flex-wrap items-center gap-x-2 gap-y-0.5">
                <p className="break-words text-sm font-medium text-ink">{e.accion}</p>
                {Number.isFinite(fecha) && (
                  <time dateTime={e.fecha} className="whitespace-nowrap text-xs tabular-nums text-ink-soft sm:ml-auto">
                    {FECHA.format(fecha)}
                  </time>
                )}
              </div>
              {e.detalle && <p className="mt-0.5 whitespace-pre-wrap break-words text-xs text-ink-soft">{e.detalle}</p>}
              {e.diagnostico && <div className="mt-2"><DiagnosticoActividad fallo={e.diagnostico} /></div>}
            </div>
          </li>
        )
      })}
    </ol>
  )
}

interface ActividadTareaProps {
  idEjecucion: number
  tarea: TareaMopRespuesta
  nombreSalida: string
  /** Cada cuántos milisegundos se consulta de nuevo mientras la tarea se ejecuta */
  refrescoMs?: number
}

/**
 * Detalle de una ejecución del historial (DEC-59): lo que ocurrió en el servidor antes de la tarea (inicio y
 * comprobación de acceso) y sus pasos, con la hora exacta de cada uno, y la salida del script
 */
export function ActividadTarea({ idEjecucion, tarea, nombreSalida, refrescoMs }: ActividadTareaProps) {
  const actividad = useConsulta(() => operacionApi.actividadEjecucion(idEjecucion), [idEjecucion, tarea.estado])
  useRefrescoConsulta(actividad, refrescoMs != null, refrescoMs)
  // El fallo de esta tarea se diagnostica con su salida: así se reconoce un error de sintaxis del script
  const eventos = deRespuestas(actividad.datos).map((e) =>
    e.idTarea === idEjecucion && e.error && e.nivel === 'ERROR' ? { ...e, diagnostico: diagnosticarFallo(e.error, tarea.salida) } : e,
  )

  return (
    <section aria-label="Actividad de la tarea" className="flex flex-col gap-3">
      <div className="overflow-hidden rounded-lg border border-line">
        <h4 className="border-b border-line bg-panel-muted px-4 py-2.5 text-sm font-semibold text-ink">Actividad de la tarea</h4>
        {actividad.error ? (
          <div className="p-3"><MensajeError error={actividad.error} onReintentar={actividad.recargar} /></div>
        ) : actividad.cargando && !actividad.datos ? (
          <div className="p-3"><Cargando /></div>
        ) : (
          <RegistroActividad eventos={eventos} />
        )}
      </div>
      {(tarea.salida || tarea.error) && (
        <details className="group rounded-lg border border-line">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-2 rounded-lg px-4 py-2.5 text-sm font-medium text-ink hover:bg-panel-muted">
            Salida del script
            <ChevronDown className="h-4 w-4 shrink-0 text-ink-soft transition-transform group-open:rotate-180" aria-hidden="true" />
          </summary>
          <div className="px-3 pb-3">
            <SalidaTerminal salida={tarea.salida} error={tarea.error} nombre={nombreSalida} altoMaximo="24rem" />
          </div>
        </details>
      )}
    </section>
  )
}
