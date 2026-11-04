import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowRight, CalendarClock, CalendarX, ChevronRight, Clock } from 'lucide-react'
import type { EstadoOrden } from '@/api/dominio'
import { planificacionApi } from '@/api/planificacion'
import type { OrdenDetalleRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { SeguimientoOrden } from '@/components/automatizacion/SeguimientoOrden'
import { PageHeader } from '@/components/common/PageHeader'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { CancelarModal } from '@/components/planificacion/CancelarModal'
import { ProgramarModal } from '@/components/planificacion/ProgramarModal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacionLocal } from '@/hooks/usePaginacion'
import { ETIQUETA_ESTADO_DETALLE, ETIQUETA_ESTADO_ORDEN, ETIQUETA_MODO, ETIQUETA_ORIGEN } from '@/utils/etiquetas'
import { formatearDuracion, formatearFechaHora, formatearFechaLarga, formatearHora } from '@/utils/formato'

/** Estados desde los que la tabla 6 permite reprogramar y cancelar */
const REPROGRAMABLES: EstadoOrden[] = ['PROGRAMADA', 'EN_COLA']
const CANCELABLES: EstadoOrden[] = ['PROGRAMADA', 'AUTORIZADA', 'EN_COLA']

const MES = new Intl.DateTimeFormat('es-PE', { month: 'short' })
const DIA_SEMANA = new Intl.DateTimeFormat('es-PE', { weekday: 'short' })

/** Hoja de calendario: el mes, el día y el día de la semana se leen de un vistazo */
function HojaCalendario({ fecha }: { fecha: Date }) {
  return (
    <div className="flex w-14 shrink-0 flex-col overflow-hidden rounded-lg border border-line text-center" aria-hidden="true">
      <span className="bg-accent py-0.5 text-[10px] font-semibold uppercase tracking-wide text-accent-ink">
        {MES.format(fecha).replace('.', '')}
      </span>
      <span className="pt-1 font-mono text-xl font-semibold leading-none text-ink">{fecha.getDate()}</span>
      <span className="pb-1 text-[10px] text-ink-faint">{DIA_SEMANA.format(fecha).replace('.', '')}</span>
    </div>
  )
}

function Momento({ etiqueta, iso }: { etiqueta: string; iso?: string }) {
  const fecha = iso ? new Date(iso) : undefined
  return (
    <div className="flex flex-col gap-2 rounded-lg bg-panel-muted px-4 py-3">
      <span className="text-xs font-medium uppercase tracking-wide text-ink-faint">{etiqueta}</span>
      {fecha ? (
        <div className="flex items-center gap-3">
          <HojaCalendario fecha={fecha} />
          <div className="min-w-0">
            <p className="font-mono text-2xl font-semibold tabular-nums text-ink">{formatearHora(fecha)}</p>
            <p className="text-sm text-ink-soft">{formatearFechaLarga(fecha)}</p>
          </div>
        </div>
      ) : (
        <p className="text-sm text-ink-soft">Sin definir</p>
      )}
    </div>
  )
}

/** La programación vigente en dos momentos, inicio y fin, y al final lo que dura */
function Programacion({ inicio, fin }: { inicio?: string; fin?: string }) {
  const minutos =
    inicio && fin ? Math.round((new Date(fin).getTime() - new Date(inicio).getTime()) / 60000) : undefined
  return (
    // Consulta de contenedor: la tarjeta ocupa media página en pantallas anchas
    <div className="@container flex flex-col gap-3">
      <div className="grid items-center gap-3 @lg:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]">
        <Momento etiqueta="Inicio programado" iso={inicio} />
        <ArrowRight className="mx-auto hidden h-5 w-5 text-ink-faint @lg:block" aria-hidden="true" />
        <Momento etiqueta="Fin previsto" iso={fin} />
      </div>
      <p className="flex items-center gap-2 text-sm text-ink">
        <Clock className="h-4 w-4 text-ink-faint" aria-hidden="true" />
        Duración prevista: <span className="font-semibold">{formatearDuracion(minutos)}</span>
      </p>
    </div>
  )
}

export function OrdenDetallePage() {
  const { id } = useParams()
  const idOrden = Number(id)
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const { avisar } = useAvisos()
  const [dialogo, setDialogo] = useState<'reprogramar' | 'cancelar' | null>(null)
  const orden = useConsulta(() => planificacionApi.orden(idOrden), [idOrden])
  const o = orden.datos
  const r = o?.resumen
  const detalles = usePaginacionLocal(o?.detalles ?? [], 'orden-detalles')

  function actualizar(nueva: OrdenDetalleRespuesta, mensaje: string) {
    orden.reemplazar(nueva)
    setDialogo(null)
    avisar(mensaje)
  }

  if (orden.cargando && !o) return <Cargando texto="Cargando orden…" />
  if (orden.error && !o) return <MensajeError error={orden.error} onReintentar={orden.recargar} />
  if (!o || !r) return null

  const grupal = r.tipoObjetivo === 'GRUPAL'
  const enlaceObjetivo = grupal ? `/grupos/${r.objetivo?.id}` : `/servidores/${r.objetivo?.id}`
  const reprogramaciones = (o.historial ?? []).filter((h) => h.estadoNuevo === 'REPROGRAMADA').reverse()

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        className=""
        volver={{ a: '/ordenes', texto: 'Volver', atras: true }}
        mono
        title={r.codigo}
        description={
          <>
            {grupal ? 'Mantenimiento grupal de ' : 'Mantenimiento de '}
            <Link to={enlaceObjetivo} className="text-accent hover:underline">
              {r.objetivo?.nombre}
            </Link>
          </>
        }
        // El estado y la etapa se leen en el ciclo de mantenimiento, justo debajo
        meta={r.prioridad === 'ALTA' && <StatusPill tone="danger" label="Prioridad alta" />}
        actions={
          esAdmin && (
            <>
              {r.estado && REPROGRAMABLES.includes(r.estado) && (
                <Boton icono={CalendarClock} onClick={() => setDialogo('reprogramar')}>
                  Reprogramar
                </Boton>
              )}
              {r.estado && CANCELABLES.includes(r.estado) && (
                <Boton icono={CalendarX} variante="fantasma" onClick={() => setDialogo('cancelar')}>
                  Cancelar orden
                </Boton>
              )}
            </>
          )
        }
      />

      <SeguimientoOrden idOrden={idOrden} onCambioEstado={orden.recargar} />

      <div className="grid gap-6 lg:grid-cols-2">
        <Tarjeta titulo="Información general">
          <dl className="grid gap-4 sm:grid-cols-2">
            <Dato etiqueta="Origen" valor={r.origen ? ETIQUETA_ORIGEN[r.origen] : undefined} />
            <Dato etiqueta="Criticidad" valor={r.criticidad?.nombre} />
            <Dato etiqueta="Solicitada por" valor={o.solicitante?.nombre ?? 'Sistema'} />
            {grupal && <Dato etiqueta="Modo de ejecución" valor={o.modoEjecucionAplicado ? ETIQUETA_MODO[o.modoEjecucionAplicado] : undefined} />}
            <Dato etiqueta="Cuenta de servicio" valor={o.cuentaServicio?.nombre ?? 'Sin cuenta asignada al generarse'} />
            <Dato etiqueta="Creada" valor={formatearFechaHora(r.fechaCreacion)} />
          </dl>
        </Tarjeta>
        <Tarjeta titulo="Programación">
          <Programacion inicio={r.inicioProgramado} fin={r.finProgramado} />
        </Tarjeta>
      </div>

      {/* En una orden individual el servidor y sus fechas ya están arriba: el detalle sirve para los grupos */}
      {grupal && (
        <Tarjeta titulo={`Detalle por servidor (${o.detalles?.length ?? 0})`}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="py-2 pr-4 font-medium">Posición</th>
                  <th className="py-2 pr-4 font-medium">Servidor</th>
                  <th className="py-2 pr-4 font-medium">Estado</th>
                  <th className="py-2 pr-4 font-medium">Previsto</th>
                  <th className="py-2 pr-4 font-medium">Real</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {detalles.filas.map((d) => (
                  <tr key={d.id}>
                    <td className="py-2 pr-4 font-mono tabular-nums text-ink-soft">
                      {d.posicionEjecucion}
                      {d.esServidorPiloto && <span className="ml-2 text-xs text-accent">piloto</span>}
                    </td>
                    <td className="py-2 pr-4">
                      <Link to={`/servidores/${d.servidor?.id}`} className="font-mono text-ink hover:text-accent">
                        {d.servidor?.nombre}
                      </Link>
                      <span className="ml-2 font-mono text-xs text-ink-faint">{d.direccionIp}</span>
                    </td>
                    <td className="py-2 pr-4 text-ink-soft">{d.estado ? ETIQUETA_ESTADO_DETALLE[d.estado] : ''}</td>
                    <td className="py-2 pr-4 text-ink-soft">
                      {formatearFechaHora(d.fechaPrevistaInicio)} – {formatearFechaHora(d.fechaPrevistaFin)}
                    </td>
                    <td className="py-2 pr-4 text-ink-soft">
                      {d.fechaRealInicio ? `${formatearFechaHora(d.fechaRealInicio)} – ${formatearFechaHora(d.fechaRealFin)}` : 'Sin iniciar'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="-mx-5 -mb-5 mt-2">
            <Paginacion {...detalles.controles} />
          </div>
        </Tarjeta>
      )}

      {/* Consulta ocasional: los cambios de estado y de fecha quedan plegados para no cargar la página */}
      <details className="group rounded-xl border border-line bg-panel">
        <summary className="flex cursor-pointer list-none flex-wrap items-center gap-x-3 gap-y-1 px-5 py-4 hover:bg-panel-muted">
          <ChevronRight className="h-4 w-4 text-ink-faint transition-transform group-open:rotate-90" aria-hidden="true" />
          <span className="text-base font-semibold text-ink">Historial de la orden</span>
          <span className="text-sm text-ink-soft">
            {o.historial?.length ?? 0} cambios de estado
            {reprogramaciones.length > 0 &&
              ` · ${reprogramaciones.length} ${reprogramaciones.length === 1 ? 'cambio de fecha' : 'cambios de fecha'}`}
          </span>
        </summary>
        <div className="grid gap-6 border-t border-line px-5 py-4 lg:grid-cols-2">
          {/* La orden guarda solo su programación vigente: cada reprogramación queda en el historial (R2.4) */}
          <section className="flex flex-col gap-3">
            <h4 className="text-sm font-semibold text-ink">Cambios de fecha</h4>
            {reprogramaciones.length === 0 ? (
              <p className="text-sm text-ink-soft">Conserva la programación con la que se generó.</p>
            ) : (
              <ol className="flex flex-col gap-3">
                {reprogramaciones.map((h, i) => (
                  <li key={i} className="rounded-lg bg-panel-muted px-3 py-2 text-sm">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-medium text-ink">{formatearFechaHora(h.fechaHora)}</span>
                      <span className="text-xs text-ink-faint">{h.usuario?.nombre ?? 'Sistema'}</span>
                    </div>
                    {h.motivo && <p className="mt-1 text-xs text-ink-soft">Motivo: {h.motivo}</p>}
                  </li>
                ))}
              </ol>
            )}
          </section>
          <section className="flex flex-col gap-3">
            <h4 className="text-sm font-semibold text-ink">Cambios de estado</h4>
            <ol className="flex flex-col gap-3">
              {o.historial?.map((h, i) => (
                <li key={i} className="flex flex-col gap-0.5 border-l-2 border-line pl-3 text-sm">
                  <span className="text-ink">
                    {h.estadoAnterior ? `${ETIQUETA_ESTADO_ORDEN[h.estadoAnterior]} → ` : ''}
                    <span className="font-medium">{h.estadoNuevo ? ETIQUETA_ESTADO_ORDEN[h.estadoNuevo] : ''}</span>
                  </span>
                  <span className="text-xs text-ink-faint">
                    {formatearFechaHora(h.fechaHora)} · {h.usuario?.nombre ?? 'Sistema'}
                  </span>
                  {h.motivo && <span className="text-xs text-ink-soft">{h.motivo}</span>}
                </li>
              ))}
            </ol>
          </section>
        </div>
      </details>

      {dialogo === 'reprogramar' && (
        <ProgramarModal
          abierto
          idOrden={idOrden}
          objetivo={{ nombre: r.objetivo?.nombre ?? '' }}
          onCerrar={() => setDialogo(null)}
          onGuardado={(nueva) => actualizar(nueva, 'Orden reprogramada.')}
        />
      )}
      {dialogo === 'cancelar' && (
        <CancelarModal
          abierto
          codigo={r.codigo ?? ''}
          onCerrar={() => setDialogo(null)}
          onConfirmar={async (motivo) => actualizar(await planificacionApi.cancelar(idOrden, { motivo }), 'Orden cancelada.')}
        />
      )}
    </div>
  )
}
