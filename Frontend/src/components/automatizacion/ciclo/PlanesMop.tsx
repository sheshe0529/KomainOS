import { useState } from 'react'
import { ChevronRight, TriangleAlert } from 'lucide-react'
import type { PlanMop, ResumenPlan, TareaMopRespuesta } from '@/api/types'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { AFECTACION, ESTADO_TAREA_MOP, formatearSegundos, TONO_ESTADO_DETALLE } from '@/utils/automatizacion'
import { ETIQUETA_ESTADO_DETALLE } from '@/utils/etiquetas'
import { formatearDuracion, formatearFechaHora, formatearHora } from '@/utils/formato'
import { SalidaTerminal } from '../SalidaTerminal'
import { CELDA, ENCABEZADO } from './estilosTabla'

const SIN_PLAN = 'El plan se arma en la evaluación previa con las tareas que activen los umbrales.'

/** Cómo se suma la duración total: una MOP tras otra o, después del piloto, varias a la vez */
const MODO = { SECUENCIAL: 'un servidor tras otro', PARALELO: 'en paralelo, después del piloto' }

function plural(n: number, singular: string, varios: string) {
  return `${n} ${n === 1 ? singular : varios}`
}

function Script({ t }: { t: TareaMopRespuesta }) {
  if (!t.idVersion) return <span className="text-danger">Sin implementación compatible</span>
  return (
    <>
      {t.script} <span className="font-mono text-ink-faint">v{t.numeroVersion}</span>
    </>
  )
}

function Piloto({ m }: { m: PlanMop }) {
  return m.piloto ? <span className="rounded bg-accent-soft px-1.5 py-0.5 font-sans text-xs text-ink">Piloto</span> : null
}

/** Las tareas de un servidor en el orden en que se ejecutan, con su script y lo que duran */
function TareasPlan({ m }: { m: PlanMop }) {
  return (
    <ol className="flex flex-col divide-y divide-line">
      {m.tareas?.map((t) => (
        <li key={t.id} className="flex items-start gap-3 py-2.5">
          <span className="w-5 shrink-0 pt-0.5 text-right font-mono text-xs tabular-nums text-ink-faint">{t.orden}</span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-ink">{t.tarea}</p>
            <p className="mt-0.5 text-xs text-ink-soft">
              <Script t={t} />
              {t.afectacion && (
                <> · {t.afectacion === 'SIN_AFECTACION' ? 'Sin afectación' : `Afectación ${AFECTACION[t.afectacion].etiqueta.toLowerCase()}`}</>
              )}
            </p>
          </div>
          <span className="shrink-0 whitespace-nowrap text-sm tabular-nums text-ink-soft">{formatearDuracion(t.duracionMinutos)}</span>
        </li>
      ))}
    </ol>
  )
}

/** Un servidor del plan: al desplegarlo, sus tareas */
function ServidorPlan({ m, abierto }: { m: PlanMop; abierto: boolean }) {
  const tareas = m.tareas ?? []
  return (
    <details open={abierto} className="group rounded-lg border border-line">
      <summary className="flex cursor-pointer list-none flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 hover:bg-panel-muted">
        <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint transition-transform group-open:rotate-90" aria-hidden="true" />
        <span className="font-mono text-sm font-medium text-ink">{m.servidor}</span>
        <Piloto m={m} />
        <span className={`ml-auto text-sm tabular-nums ${m.superaDuracionMaxima ? 'font-medium text-warning' : 'text-ink-soft'}`}>
          {m.superaDuracionMaxima && <TriangleAlert className="mr-1 inline h-3.5 w-3.5" aria-hidden="true" />}
          {plural(tareas.length, 'tarea', 'tareas')} · {formatearDuracion(m.duracionMinutos)}
        </span>
        {m.superaDuracionMaxima && (
          <span className="basis-full pl-7 text-xs text-warning">Supera los {formatearDuracion(m.maximoMinutos)} permitidos por MOP</span>
        )}
      </summary>
      <div className="border-t border-line px-4">
        <TareasPlan m={m} />
      </div>
    </details>
  )
}

/** Lo que dura el plan completo, y cómo se suma si hay varios servidores */
function DuracionTotal({ resumen }: { resumen: ResumenPlan }) {
  return (
    <>
      <span className="font-medium text-ink">{formatearDuracion(resumen.duracionTotalMinutos)}</span>
      {resumen.modoEjecucion && (resumen.servidoresConTareas ?? 0) > 1 && (
        <span className="ml-1 text-xs text-ink-soft">({MODO[resumen.modoEjecucion]})</span>
      )}
    </>
  )
}

/**
 * Plan de trabajo (RF41, RF42, RF58) en una fila: cuántos servidores, cuántos tienen tareas, cuántas tareas y lo que dura
 * todo según el modo de ejecución. Al pulsarla se abre la MOP de cada servidor y, al final, el resumen
 */
export function PlanTrabajo({ mops, resumen }: { mops: PlanMop[]; resumen?: ResumenPlan }) {
  const [abierto, setAbierto] = useState(false)
  if (mops.length === 0 || !resumen) return <p className="text-sm text-ink-soft">{SIN_PLAN}</p>

  const conTareas = resumen.servidoresConTareas ?? 0
  const excedidas = mops.filter((m) => m.superaDuracionMaxima).length

  return (
    <>
      <div className="overflow-x-auto rounded-lg border border-line">
        <table className="w-full min-w-[560px] text-sm">
          <thead>
            <tr className={ENCABEZADO}>
              <th className="px-3 py-2 font-medium">Servidores</th>
              <th className="px-3 py-2 font-medium">Servidores con tareas</th>
              <th className="px-3 py-2 font-medium">Tareas</th>
              <th className="px-3 py-2 font-medium">Duración total estimada</th>
            </tr>
          </thead>
          <tbody>
            <FilaDetalle onAbrir={() => setAbierto(true)} className="align-middle">
              <td className={CELDA}>
                <BotonDetalle onAbrir={() => setAbierto(true)} etiqueta="Ver el plan de trabajo de cada servidor">
                  <span className="tabular-nums">{resumen.servidores}</span>
                </BotonDetalle>
              </td>
              <td className={`${CELDA} tabular-nums text-ink`}>{conTareas}</td>
              <td className={`${CELDA} tabular-nums text-ink`}>{resumen.tareas}</td>
              <td className={`${CELDA} whitespace-nowrap tabular-nums`}>
                <DuracionTotal resumen={resumen} />
                {excedidas > 0 && (
                  <span className="mt-0.5 flex items-center gap-1 text-xs text-warning">
                    <TriangleAlert className="h-3.5 w-3.5" aria-hidden="true" />
                    {excedidas === 1 ? 'Una MOP supera' : `${excedidas} MOP superan`} la duración permitida
                  </span>
                )}
              </td>
            </FilaDetalle>
          </tbody>
        </table>
      </div>

      {abierto && (
        <Modal
          abierto
          titulo="Plan de trabajo (MOP)"
          descripcion="Las tareas de cada servidor, en el orden en que se ejecutarán."
          onCerrar={() => setAbierto(false)}
          ancho="lg"
        >
          <div className="flex flex-col gap-4">
            <ul className="flex flex-col gap-2">
              {mops.map((m) => (
                <li key={m.id}>
                  <ServidorPlan m={m} abierto={mops.length === 1} />
                </li>
              ))}
            </ul>
            <p className="rounded-lg bg-panel-muted px-4 py-3 text-sm text-ink-soft">
              <span className="font-medium text-ink">{plural(resumen.tareas ?? 0, 'tarea', 'tareas')}</span>
              {(resumen.servidores ?? 0) > 1 && ` en ${conTareas} de ${resumen.servidores} servidores`}
              {' · duración total estimada '}
              <DuracionTotal resumen={resumen} />
            </p>
          </div>
        </Modal>
      )}
    </>
  )
}

/** Duración real del servidor si ya se ejecutó; si no, la estimada */
function duracionServidor(m: PlanMop): string {
  if (m.inicioReal && m.finReal) {
    return formatearSegundos(Math.round((new Date(m.finReal).getTime() - new Date(m.inicioReal).getTime()) / 1000))
  }
  return formatearDuracion(m.duracionMinutos)
}

/** Una tarea ejecutada: su resultado y, a pedido, la salida del script */
function TareaEjecutada({ t }: { t: TareaMopRespuesta }) {
  const [abierta, setAbierta] = useState(false)
  const conSalida = !!(t.salida || t.error)
  const estado = t.estado ? ESTADO_TAREA_MOP[t.estado] : undefined

  return (
    <li className="flex flex-col gap-2 py-2.5">
      <div className="flex flex-wrap items-start gap-x-3 gap-y-1">
        <span className="w-5 shrink-0 pt-0.5 text-right font-mono text-xs tabular-nums text-ink-faint">{t.orden}</span>
        <div className="min-w-28 flex-1">
          <p className="text-sm font-medium text-ink">{t.tarea}</p>
          <p className="mt-0.5 text-xs text-ink-soft">
            <Script t={t} />
          </p>
          {t.fechaInicio && t.cuenta && (
            <p className="mt-0.5 text-xs text-ink-soft">
              Cuenta de servicio <span className="break-words text-ink">{t.cuenta}</span>
            </p>
          )}
          {t.error && !abierta && <p className="mt-0.5 line-clamp-1 text-xs text-danger">{t.error}</p>}
        </div>
        <span className="shrink-0 whitespace-nowrap text-sm tabular-nums text-ink-soft">
          {t.duracionSegundos !== undefined && t.duracionSegundos !== null
            ? formatearSegundos(t.duracionSegundos)
            : formatearDuracion(t.duracionMinutos)}
        </span>
        {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
      </div>
      {conSalida && (
        <div className="sm:pl-8">
          <button
            type="button"
            aria-expanded={abierta}
            onClick={() => setAbierta(!abierta)}
            className="inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline"
          >
            <ChevronRight className={`h-3.5 w-3.5 transition-transform ${abierta ? 'rotate-90' : ''}`} aria-hidden="true" />
            {abierta ? 'Ocultar la salida' : 'Ver la salida'}
          </button>
          {abierta && (
            <div className="mt-2">
              <p className="mb-2 text-xs text-ink-soft">
                {formatearFechaHora(t.fechaInicio)} – {formatearHora(t.fechaFin)}
                {t.codigoRetorno != null && <> · código de salida {t.codigoRetorno}</>}
              </p>
              <SalidaTerminal salida={t.salida} error={t.error} nombre={`tarea_${t.id}_${t.tarea}`} />
            </div>
          )}
        </div>
      )}
    </li>
  )
}

/**
 * Ejecución de las MOP (RF51-RF53): una fila por servidor con su avance y su estado; al pulsarla se abren sus tareas con
 * el resultado y la salida de cada una
 */
export function EjecucionMop({ mops }: { mops: PlanMop[] }) {
  // Se guarda el servidor y no la fila: mientras la orden avanza, la ficha se refresca y el detalle abierto también
  const [idAbierto, setIdAbierto] = useState<number>()
  const abierto = idAbierto == null ? undefined : mops.find((m) => m.id === idAbierto)
  if (mops.length === 0) return <p className="text-sm text-ink-soft">{SIN_PLAN}</p>

  return (
    <>
      <div className="overflow-x-auto rounded-lg border border-line">
        <table className="w-full min-w-[680px] text-sm">
          <thead>
            <tr className={ENCABEZADO}>
              <th className="px-3 py-2 font-medium">Servidor</th>
              <th className="px-3 py-2 font-medium">Tareas</th>
              <th className="px-3 py-2 font-medium">Estado</th>
              <th className="px-3 py-2 font-medium">Cuenta de servicio</th>
              <th className="px-3 py-2 font-medium">Duración</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {mops.map((m) => {
              const tareas = m.tareas ?? []
              const finalizadas = tareas.filter((t) => t.estado === 'FINALIZADA').length
              const cuentas = [...new Set(tareas.flatMap((t) => t.fechaInicio && t.cuenta ? [t.cuenta] : []))]
              return (
                <FilaDetalle key={m.id} onAbrir={() => setIdAbierto(m.id)} className="align-middle">
                  <td className={CELDA}>
                    <span className="flex items-center gap-2">
                      <BotonDetalle onAbrir={() => setIdAbierto(m.id)} etiqueta={`Ver las tareas de ${m.servidor}`}>
                        <span className="font-mono">{m.servidor}</span>
                      </BotonDetalle>
                      <Piloto m={m} />
                    </span>
                  </td>
                  <td className={`${CELDA} text-ink-soft`}>
                    {finalizadas} de {plural(tareas.length, 'tarea finalizada', 'tareas finalizadas')}
                  </td>
                  <td className={CELDA}>
                    {m.estadoServidor && (
                      <StatusPill tone={TONO_ESTADO_DETALLE[m.estadoServidor]} label={ETIQUETA_ESTADO_DETALLE[m.estadoServidor]} />
                    )}
                  </td>
                  <td className={`${CELDA} break-words text-ink-soft`}>{cuentas.join(', ') || '—'}</td>
                  <td className={`${CELDA} whitespace-nowrap tabular-nums text-ink-soft`}>{duracionServidor(m)}</td>
                </FilaDetalle>
              )
            })}
          </tbody>
        </table>
      </div>

      {abierto && (
        <Modal
          abierto
          titulo={`Tareas en ${abierto.servidor}`}
          descripcion={
            !abierto.inicioReal ? 'Aún no se inicia la ejecución en este servidor.'
              : abierto.finReal ? `${formatearFechaHora(abierto.inicioReal)} – ${formatearHora(abierto.finReal)}`
                : `Inicio registrado el ${formatearFechaHora(abierto.inicioReal)}`
          }
          onCerrar={() => setIdAbierto(undefined)}
          ancho="lg"
        >
          <ol className="flex flex-col divide-y divide-line">
            {abierto.tareas?.map((t) => (
              <TareaEjecutada key={t.id} t={t} />
            ))}
          </ol>
        </Modal>
      )}
    </>
  )
}
