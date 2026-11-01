import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowRight, CalendarClock, CalendarDays, CalendarX } from 'lucide-react'
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
import { TONO_ESTADO_ORDEN } from '@/utils/automatizacion'
import { ETIQUETA_ESTADO_DETALLE, ETIQUETA_ESTADO_ORDEN, ETIQUETA_ETAPA, ETIQUETA_MODO, ETIQUETA_ORIGEN } from '@/utils/etiquetas'
import { formatearDuracion, formatearFechaHora, formatearFechaLarga, formatearHora } from '@/utils/formato'

/** Estados desde los que la tabla 6 permite reprogramar y cancelar */
const REPROGRAMABLES: EstadoOrden[] = ['PROGRAMADA', 'EN_COLA']
const CANCELABLES: EstadoOrden[] = ['PROGRAMADA', 'AUTORIZADA', 'EN_COLA']

/** Inicio y fin en una línea, como se lee en una agenda; el fin lleva fecha solo si cae otro día */
function Programacion({ inicio, fin, evaluacion }: { inicio?: string; fin?: string; evaluacion?: string }) {
  if (!inicio) return <p className="text-sm text-ink-soft">Sin programación.</p>
  const desde = new Date(inicio)
  const hasta = fin ? new Date(fin) : undefined
  const mismoDia = hasta && desde.toDateString() === hasta.toDateString()
  const minutos = hasta ? Math.round((hasta.getTime() - desde.getTime()) / 60000) : undefined
  return (
    <div className="flex items-start gap-4">
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent">
        <CalendarDays className="h-5 w-5" aria-hidden="true" />
      </span>
      <div className="flex flex-col gap-1">
        <p className="text-base font-semibold text-ink">{formatearFechaLarga(desde)}</p>
        <p className="flex flex-wrap items-center gap-2 font-mono text-lg tabular-nums text-ink">
          {formatearHora(desde)}
          <ArrowRight className="h-4 w-4 text-ink-faint" aria-hidden="true" />
          {hasta ? (mismoDia ? formatearHora(hasta) : formatearFechaHora(fin)) : '—'}
          {minutos !== undefined && <span className="font-sans text-sm text-ink-soft">({formatearDuracion(minutos)})</span>}
        </p>
        <p className="text-xs text-ink-soft">
          Inicio programado y fin previsto{evaluacion && ` · el análisis previo corre el ${formatearFechaHora(evaluacion)}`}
        </p>
      </div>
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
        meta={
          <>
            {r.estado && <StatusPill tone={TONO_ESTADO_ORDEN[r.estado]} label={ETIQUETA_ESTADO_ORDEN[r.estado]} />}
            <StatusPill tone="neutral" label={`Criticidad ${r.criticidad?.nombre ?? ''}`} />
            {r.etapa && <StatusPill tone="neutral" label={`Etapa: ${ETIQUETA_ETAPA[r.etapa]}`} />}
            {r.prioridad === 'ALTA' && <StatusPill tone="danger" label="Prioridad alta" />}
          </>
        }
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
            <Dato etiqueta="Solicitada por" valor={o.solicitante?.nombre ?? 'Sistema'} />
            <Dato etiqueta="Criticidad aplicada" valor={r.criticidad?.nombre} />
            <Dato etiqueta="Prioridad" valor={r.prioridad === 'ALTA' ? 'Alta' : 'Normal'} />
            {grupal && <Dato etiqueta="Modo de ejecución" valor={o.modoEjecucionAplicado ? ETIQUETA_MODO[o.modoEjecucionAplicado] : undefined} />}
            <Dato etiqueta="Cuenta de servicio" valor={o.cuentaServicio?.nombre ?? 'Sin cuenta asignada al generarse'} />
            <Dato etiqueta="Creada" valor={formatearFechaHora(r.fechaCreacion)} />
          </dl>
        </Tarjeta>
        <Tarjeta titulo="Programación">
          <Programacion inicio={r.inicioProgramado} fin={r.finProgramado} evaluacion={r.fechaEvaluacion} />
        </Tarjeta>
      </div>

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

      <div className="grid gap-6 lg:grid-cols-2">
        {/* La orden guarda solo su programación vigente: cada reprogramación queda en el historial (R2.4) */}
        <Tarjeta titulo={`Reprogramaciones (${reprogramaciones.length})`}>
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
        </Tarjeta>
        <Tarjeta titulo="Historial de estados">
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
        </Tarjeta>
      </div>

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
