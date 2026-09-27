import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, CalendarClock, CalendarX } from 'lucide-react'
import type { EstadoOrden } from '@/api/dominio'
import { planificacionApi } from '@/api/planificacion'
import type { OrdenDetalleRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { CancelarModal } from '@/components/planificacion/CancelarModal'
import { ProgramarModal } from '@/components/planificacion/ProgramarModal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'
import { ESTADO_DETALLE, ESTADO_ORDEN, ETIQUETA_ETAPA, ETIQUETA_MODO, ETIQUETA_ORIGEN } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'

/** Estados desde los que la tabla 6 permite reprogramar y cancelar. */
const REPROGRAMABLES: EstadoOrden[] = ['PROGRAMADA', 'EN_COLA']
const CANCELABLES: EstadoOrden[] = ['PROGRAMADA', 'AUTORIZADA', 'EN_COLA']

/** Detalle de una orden (RF33, HU20 CA4, HU23 CA3). */
export function OrdenDetallePage() {
  const tonoCriticidad = useTonoCriticidad()
  const { id } = useParams()
  const idOrden = Number(id)
  const navigate = useNavigate()
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const { avisar } = useAvisos()
  const [dialogo, setDialogo] = useState<'reprogramar' | 'cancelar' | null>(null)
  const orden = useConsulta(() => planificacionApi.orden(idOrden), [idOrden])
  const o = orden.datos
  const r = o?.resumen

  function actualizar(nueva: OrdenDetalleRespuesta, mensaje: string) {
    orden.reemplazar(nueva)
    setDialogo(null)
    avisar(mensaje)
  }

  if (orden.cargando && !o) return <Cargando texto="Cargando orden…" />
  if (orden.error && !o) return <MensajeError error={orden.error} onReintentar={orden.recargar} />
  if (!o || !r) return null

  const estado = r.estado ? ESTADO_ORDEN[r.estado] : undefined
  const grupal = r.tipoObjetivo === 'GRUPAL'
  const enlaceObjetivo = grupal ? `/grupos/${r.objetivo?.id}` : `/servidores/${r.objetivo?.id}`

  return (
    <div className="flex flex-col gap-6">
      <button type="button" onClick={() => navigate(-1)} className="inline-flex items-center gap-1 self-start text-sm text-ink-soft hover:text-ink">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver
      </button>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="font-mono text-2xl font-semibold text-ink">{r.codigo}</h2>
          <p className="mt-1 text-sm text-ink-soft">
            {grupal ? 'Mantenimiento grupal de ' : 'Mantenimiento de '}
            <Link to={enlaceObjetivo} className="text-accent hover:underline">
              {r.objetivo?.nombre}
            </Link>
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
            <StatusPill tone={tonoCriticidad(r.criticidad?.prioridad)} label={`Criticidad ${r.criticidad?.nombre ?? ''}`} />
            {r.etapa && <StatusPill tone="neutral" label={`Etapa: ${ETIQUETA_ETAPA[r.etapa]}`} />}
            {r.prioridad === 'ALTA' && <StatusPill tone="danger" label="Prioridad alta" />}
          </div>
        </div>
        {esAdmin && (
          <div className="flex gap-2">
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
          </div>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Tarjeta titulo="Información general">
          <dl className="grid gap-4 sm:grid-cols-2">
            <Dato etiqueta="Origen" valor={r.origen ? ETIQUETA_ORIGEN[r.origen] : undefined} />
            <Dato etiqueta="Solicitada por" valor={o.solicitante?.nombre ?? 'Sistema'} />
            <Dato etiqueta="Criticidad aplicada" valor={r.criticidad?.nombre} />
            <Dato etiqueta="Prioridad" valor={r.prioridad === 'ALTA' ? 'Alta' : 'Normal'} />
            {grupal && <Dato etiqueta="Modo de ejecución" valor={o.modoEjecucionAplicado ? ETIQUETA_MODO[o.modoEjecucionAplicado] : undefined} />}
            <Dato etiqueta="Cuenta de servicio" valor={o.usaCuentaPredeterminada ? 'Predeterminada del sistema' : 'Propia de la configuración'} />
            <Dato etiqueta="Creada" valor={formatearFechaHora(r.fechaCreacion)} />
          </dl>
        </Tarjeta>
        <Tarjeta titulo="Programación vigente">
          <dl className="grid gap-4 sm:grid-cols-2">
            <Dato etiqueta="Fecha objetivo" valor={formatearFechaHora(r.fechaObjetivo)} />
            <Dato etiqueta="Inicio programado" valor={formatearFechaHora(r.inicioProgramado)} />
            <Dato etiqueta="Fin previsto" valor={formatearFechaHora(r.finProgramado)} />
            <Dato etiqueta="Evaluación previa" valor={formatearFechaHora(r.fechaEvaluacion)} />
            <Dato
              etiqueta="Ventana permisiva aplicada"
              valor={`${formatearFechaHora(o.inicioVentanaAplicada)} – ${formatearFechaHora(o.finVentanaAplicada)}`}
            />
          </dl>
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
              {o.detalles?.map((d) => (
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
                  <td className="py-2 pr-4">
                    {d.estado && <StatusPill tone={ESTADO_DETALLE[d.estado].tono} label={ESTADO_DETALLE[d.estado].etiqueta} />}
                  </td>
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
      </Tarjeta>

      <div className="grid gap-6 lg:grid-cols-2">
        <Tarjeta titulo="Historial de programación">
          <ol className="flex flex-col gap-3">
            {o.programaciones?.map((p) => (
              <li key={p.numeroVersion} className="rounded-lg bg-panel-muted px-3 py-2 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium text-ink">Versión {p.numeroVersion}</span>
                  <span className="text-xs text-ink-faint">
                    {formatearFechaHora(p.fechaRegistro)} · {p.registradoPor?.nombre ?? 'Sistema'}
                  </span>
                </div>
                <p className="mt-1 text-ink-soft">
                  {formatearFechaHora(p.inicio)} – {formatearFechaHora(p.fin)}
                </p>
                {p.motivo && <p className="mt-1 text-xs text-ink-soft">Motivo: {p.motivo}</p>}
              </li>
            ))}
          </ol>
        </Tarjeta>
        <Tarjeta titulo="Historial de estados">
          <ol className="flex flex-col gap-3">
            {o.historial?.map((h, i) => (
              <li key={i} className="flex flex-col gap-0.5 border-l-2 border-line pl-3 text-sm">
                <span className="text-ink">
                  {h.estadoAnterior ? `${ESTADO_ORDEN[h.estadoAnterior].etiqueta} → ` : ''}
                  <span className="font-medium">{h.estadoNuevo ? ESTADO_ORDEN[h.estadoNuevo].etiqueta : ''}</span>
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
