import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight, Clock, TriangleAlert } from 'lucide-react'
import type { PlanMop, TareaMopRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { AFECTACION, ESTADO_TAREA_MOP, formatearSegundos, TONO_ESTADO_DETALLE } from '@/utils/automatizacion'
import { ETIQUETA_ESTADO_DETALLE } from '@/utils/etiquetas'
import { formatearDuracion, formatearFechaHora } from '@/utils/formato'
import { SalidaTerminal } from '../SalidaTerminal'

interface PlanesMopProps {
  mops: PlanMop[]
}

function FilaTarea({ tarea: t }: { tarea: TareaMopRespuesta }) {
  const [abierta, setAbierta] = useState(false)
  const conSalida = !!(t.salida || t.error)
  const estado = t.estado ? ESTADO_TAREA_MOP[t.estado] : undefined

  return (
    <li className="border-b border-line last:border-b-0">
      <button
        type="button"
        disabled={!conSalida}
        aria-expanded={conSalida ? abierta : undefined}
        onClick={() => setAbierta(!abierta)}
        className="grid w-full grid-cols-[1.5rem_minmax(0,1fr)_auto] items-start gap-3 px-4 py-3 text-left text-sm enabled:hover:bg-panel-muted sm:grid-cols-[1.5rem_minmax(0,1fr)_10rem_8rem_auto]"
      >
        <span className="font-mono text-xs leading-5 text-ink-faint">{t.orden}</span>
        <span className="min-w-0">
          <span className="block font-medium text-ink">{t.tarea}</span>
          <span className="block text-xs text-ink-soft">
            {t.idVersion ? (
              <>
                {t.script} <span className="font-mono">v{t.numeroVersion}</span>
              </>
            ) : (
              <span className="text-danger">Sin implementación compatible</span>
            )}
            {t.cuenta && <> · cuenta {t.cuenta}</>} · prioridad {t.prioridad}
          </span>
          {t.error && !abierta && <span className="mt-1 line-clamp-1 block text-xs text-danger">{t.error}</span>}
        </span>
        <span className="hidden text-xs text-ink-soft sm:block">
          {t.afectacion && <StatusPill tone={AFECTACION[t.afectacion].tono} label={AFECTACION[t.afectacion].etiqueta} />}
        </span>
        <span className="hidden text-xs tabular-nums text-ink-soft sm:block">
          <Clock className="mr-1 inline h-3.5 w-3.5" aria-hidden="true" />
          {t.duracionSegundos !== undefined && t.duracionSegundos !== null
            ? `${formatearSegundos(t.duracionSegundos)} / ${formatearDuracion(t.duracionMinutos)}`
            : formatearDuracion(t.duracionMinutos)}
        </span>
        <span className="flex items-center gap-2">
          {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
          {conSalida && (
            <ChevronRight
              className={`h-4 w-4 text-ink-faint transition-transform ${abierta ? 'rotate-90' : ''}`}
              aria-hidden="true"
            />
          )}
        </span>
      </button>
      {abierta && (
        <div className="flex flex-col gap-2 px-4 pb-4 pl-12">
          <p className="text-xs text-ink-soft">
            {formatearFechaHora(t.fechaInicio)} – {formatearFechaHora(t.fechaFin)}
            {t.codigoRetorno !== undefined && t.codigoRetorno !== null && <> · código de salida {t.codigoRetorno}</>}
          </p>
          <SalidaTerminal salida={t.salida} error={t.error} nombre={`tarea_${t.id}_${t.tarea}`} />
        </div>
      )}
    </li>
  )
}

/** MOP de cada servidor (RF41, RF42): tareas en orden de ejecución, versión fijada, cuenta y resultado */
export function PlanesMop({ mops }: PlanesMopProps) {
  if (mops.length === 0) {
    return (
      <p className="text-sm text-ink-soft">
        La MOP se genera en la evaluación previa, con las tareas que activen los umbrales de cada servidor.
      </p>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      {mops.map((m) => (
        <section key={m.id} className="overflow-hidden rounded-lg border border-line">
          <header className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-line bg-panel-muted px-4 py-3">
            <Link to={`/servidores/${m.idServidor}`} className="font-mono text-sm font-medium text-ink hover:text-accent hover:underline">
              {m.servidor}
            </Link>
            {m.piloto && <span className="rounded bg-accent-soft px-1.5 py-0.5 text-xs text-ink">Piloto</span>}
            {m.estadoServidor && (
              <StatusPill tone={TONO_ESTADO_DETALLE[m.estadoServidor]} label={ETIQUETA_ESTADO_DETALLE[m.estadoServidor]} />
            )}
            {m.afectacion && (
              <span className="text-xs text-ink-soft">Afectación {AFECTACION[m.afectacion].etiqueta.toLowerCase()}</span>
            )}
            <span className={`ml-auto text-xs ${m.superaDuracionMaxima ? 'font-medium text-warning' : 'text-ink-soft'}`}>
              {m.superaDuracionMaxima && <TriangleAlert className="mr-1 inline h-3.5 w-3.5" aria-hidden="true" />}
              {formatearDuracion(m.duracionMinutos)} estimados de {formatearDuracion(m.maximoMinutos)} permitidos
            </span>
          </header>
          {(m.tareas ?? []).length === 0 ? (
            <p className="px-4 py-4 text-sm text-ink-soft">
              Ningún umbral activó tareas en este servidor: no requiere intervención.
            </p>
          ) : (
            <ol>
              {m.tareas?.map((t) => (
                <FilaTarea key={t.id} tarea={t} />
              ))}
            </ol>
          )}
          {(m.inicioReal || m.finReal) && (
            <p className="border-t border-line px-4 py-2 text-xs text-ink-soft">
              Ejecutada {formatearFechaHora(m.inicioReal)} – {formatearFechaHora(m.finReal)}
            </p>
          )}
        </section>
      ))}
    </div>
  )
}
