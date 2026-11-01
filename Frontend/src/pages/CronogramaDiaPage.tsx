import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { planificacionApi } from '@/api/planificacion'
import { EnlaceVolver } from '@/components/common/PageHeader'
import { ChartCard } from '@/components/dashboard/ChartCard'
import { DistribucionHorariaChart, type PuntoHora } from '@/components/planificacion/DistribucionHorariaChart'
import { Cargando } from '@/components/ui/Cargando'
import { ENLACE_FILA } from '@/components/ui/estilos'
import { FilaNavegable } from '@/components/ui/FilaNavegable'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacionLocal } from '@/hooks/usePaginacion'
import { TONO_ESTADO_ORDEN } from '@/utils/automatizacion'
import { ETIQUETA_ESTADO_ORDEN, ETIQUETA_ORIGEN } from '@/utils/etiquetas'
import { claveDia, formatearFechaHora, formatearFechaLarga, formatearHora } from '@/utils/formato'

function fechaDesdeClave(clave?: string): Date {
  if (clave && /^\d{4}-\d{2}-\d{2}$/.test(clave)) {
    const [a, m, d] = clave.split('-').map(Number)
    return new Date(a, m - 1, d)
  }
  const hoy = new Date()
  return new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate())
}

export function CronogramaDiaPage() {
  const { fecha } = useParams()
  const dia = fechaDesdeClave(fecha)
  const siguiente = new Date(dia)
  siguiente.setDate(siguiente.getDate() + 1)
  const clave = claveDia(dia)

  const ordenes = useConsulta(() => planificacionApi.cronograma(dia, siguiente), [clave])

  const delDia = ordenes.datos ?? []
  const tabla = usePaginacionLocal(delDia, 'cronograma-dia')

  // Las que vienen cruzando desde el día anterior se listan pero no suman a la distribución por hora de inicio
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
      <EnlaceVolver a={`/cronograma?mes=${clave.slice(0, 7)}`} texto="Volver al cronograma" />
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
                  {tabla.filas.map((o) => {
                    const empiezaHoy = o.inicioProgramado && claveDia(new Date(o.inicioProgramado)) === clave
                    return (
                      <FilaNavegable key={o.id} a={`/ordenes/${o.id}`}>
                        <td className="px-4 py-3 font-mono text-ink-soft">
                          {empiezaHoy ? formatearHora(o.inicioProgramado) : formatearFechaHora(o.inicioProgramado)}
                          <span className="text-ink-faint"> – {formatearHora(o.finProgramado)}</span>
                        </td>
                        <td className="px-4 py-3">
                          <Link to={`/ordenes/${o.id}`} className={`font-mono ${ENLACE_FILA}`}>
                            {o.codigo}
                          </Link>
                        </td>
                        <td className="px-4 py-3">
                          <span className={o.tipoObjetivo === 'GRUPAL' ? 'text-ink' : 'font-mono text-ink-soft'}>{o.objetivo?.nombre}</span>
                          {o.tipoObjetivo === 'GRUPAL' && <span className="block text-xs text-ink-faint">{o.cantidadServidores} servidores</span>}
                        </td>
                        <td className="px-4 py-3 text-ink-soft">{o.origen ? ETIQUETA_ORIGEN[o.origen] : ''}</td>
                        <td className="px-4 py-3 text-ink-soft">{o.criticidad?.nombre}</td>
                        <td className="px-4 py-3">
                          {o.estado && <StatusPill tone={TONO_ESTADO_ORDEN[o.estado]} label={ETIQUETA_ESTADO_ORDEN[o.estado]} />}
                        </td>
                      </FilaNavegable>
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
            <Paginacion {...tabla.controles} />
          </div>
        </>
      )}
    </div>
  )
}
