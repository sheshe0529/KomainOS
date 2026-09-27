import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { planificacionApi } from '@/api/planificacion'
import { ChartCard } from '@/components/dashboard/ChartCard'
import { DistribucionHorariaChart, type PuntoHora } from '@/components/planificacion/DistribucionHorariaChart'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'
import { ESTADO_ORDEN, ETIQUETA_ORIGEN } from '@/utils/etiquetas'
import { claveDia, formatearFechaHora, formatearFechaLarga, formatearHora } from '@/utils/formato'

function fechaDesdeClave(clave?: string): Date {
  if (clave && /^\d{4}-\d{2}-\d{2}$/.test(clave)) {
    const [a, m, d] = clave.split('-').map(Number)
    return new Date(a, m - 1, d)
  }
  const hoy = new Date()
  return new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate())
}

/** Órdenes de un día y su distribución horaria (RF32, HU20 CA3-CA4). */
export function CronogramaDiaPage() {
  const tonoCriticidad = useTonoCriticidad()
  const { fecha } = useParams()
  const dia = fechaDesdeClave(fecha)
  const siguiente = new Date(dia)
  siguiente.setDate(siguiente.getDate() + 1)
  const clave = claveDia(dia)

  const ordenes = useConsulta(() => planificacionApi.cronograma(dia, siguiente), [clave])

  const delDia = ordenes.datos ?? []

  // Las que empiezan este día; las que vienen cruzando desde el anterior se
  // listan pero no suman a la distribución por hora de inicio.
  const { inicianHoy, distribucion } = useMemo(() => {
    const propias = (ordenes.datos ?? []).filter(
      (o) => o.inicioProgramado && claveDia(new Date(o.inicioProgramado)) === clave,
    )
    const conteo = Array.from({ length: 24 }, () => 0)
    for (const o of propias) conteo[new Date(o.inicioProgramado!).getHours()] += 1
    const puntos: PuntoHora[] = conteo.map((n, h) => ({ hora: String(h).padStart(2, '0'), ordenes: n }))
    return { inicianHoy: propias, distribucion: puntos }
  }, [ordenes.datos, clave])

  return (
    <div className="flex flex-col gap-6">
      <Link to={`/cronograma?mes=${clave.slice(0, 7)}`} className="inline-flex items-center gap-1 self-start text-sm text-ink-soft hover:text-ink">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver al cronograma
      </Link>
      <div>
        <h2 className="text-2xl font-semibold text-ink">{formatearFechaLarga(dia)}</h2>
        <p className="mt-1 text-sm text-ink-soft">
          {inicianHoy.length} {inicianHoy.length === 1 ? 'orden programada' : 'órdenes programadas'} este día
          {delDia.length > inicianHoy.length && ` · ${delDia.length - inicianHoy.length} en curso desde el día anterior`}
        </p>
      </div>

      <MensajeError error={ordenes.error} onReintentar={ordenes.recargar} />

      {ordenes.cargando && !ordenes.datos ? (
        <Cargando />
      ) : (
        <>
          <ChartCard title="Distribución horaria" description="Órdenes que inician en cada hora del día (00 a 23 h).">
            <DistribucionHorariaChart datos={distribucion} />
          </ChartCard>

          <div className="overflow-hidden rounded-xl border border-line bg-panel">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead>
                  <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                    <th className="px-4 py-3 font-medium">Hora</th>
                    <th className="px-4 py-3 font-medium">Orden</th>
                    <th className="px-4 py-3 font-medium">Servidor / grupo</th>
                    <th className="px-4 py-3 font-medium">Tipo</th>
                    <th className="px-4 py-3 font-medium">Criticidad</th>
                    <th className="px-4 py-3 font-medium">Estado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {delDia.map((o) => {
                    const estado = o.estado ? ESTADO_ORDEN[o.estado] : undefined
                    const empiezaHoy = o.inicioProgramado && claveDia(new Date(o.inicioProgramado)) === clave
                    return (
                      <tr key={o.id} className="hover:bg-panel-muted">
                        <td className="px-4 py-3 font-mono text-ink-soft">
                          {empiezaHoy ? formatearHora(o.inicioProgramado) : formatearFechaHora(o.inicioProgramado)}
                          <span className="text-ink-faint"> – {formatearHora(o.finProgramado)}</span>
                        </td>
                        <td className="px-4 py-3">
                          <Link to={`/ordenes/${o.id}`} className="font-mono font-medium text-ink hover:text-accent">
                            {o.codigo}
                          </Link>
                        </td>
                        <td className="px-4 py-3">
                          <span className={o.tipoObjetivo === 'GRUPAL' ? 'text-ink' : 'font-mono text-ink-soft'}>{o.objetivo?.nombre}</span>
                          {o.tipoObjetivo === 'GRUPAL' && <span className="block text-xs text-ink-faint">{o.cantidadServidores} servidores</span>}
                        </td>
                        <td className="px-4 py-3 text-ink-soft">{o.origen ? ETIQUETA_ORIGEN[o.origen] : ''}</td>
                        <td className="px-4 py-3">
                          <StatusPill tone={tonoCriticidad(o.criticidad?.prioridad)} label={o.criticidad?.nombre ?? ''} />
                        </td>
                        <td className="px-4 py-3">{estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}</td>
                      </tr>
                    )
                  })}
                  {delDia.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-4 py-10 text-center text-ink-soft">
                        No hay órdenes programadas este día.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
