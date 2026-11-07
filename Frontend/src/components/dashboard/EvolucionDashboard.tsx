import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { MantenimientosMes, ServidoresMes } from '@/api/types'
import { ChartCard } from './ChartCard'
import { ChartLegend } from './ChartLegend'
import { ChartTooltip } from './ChartTooltip'

const EJE = { tickLine: false, axisLine: false, tick: { fill: 'var(--color-ink-faint)', fontSize: 10 } }
const INFORMACION = 'var(--color-grafico-informativo)'
const EXITO = 'var(--color-grafico-exito)'
const ERROR = 'var(--color-grafico-error)'
const FORMATO_MES = new Intl.DateTimeFormat('es-PE', { month: 'short', year: '2-digit', timeZone: 'UTC' })
const FORMATO_TITULO = new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric', timeZone: 'UTC' })

function etiquetas(mes: string) {
  const fecha = new Date(`${mes}-01T00:00:00Z`)
  return { etiqueta: FORMATO_MES.format(fecha).replace('.', ''), titulo: FORMATO_TITULO.format(fecha) }
}

/** Series de doce meses fijos: los filtros de periodo de los KPI no cambian esta ventana de evolución. */
export function EvolucionDashboard({ servidores, mantenimientos, cargando }: {
  servidores?: ServidoresMes[]
  mantenimientos?: MantenimientosMes[]
  cargando: boolean
}) {
  const inventario = (servidores ?? []).flatMap(({ mes, registrados = 0, altas = 0 }) =>
    mes ? [{ mes, registrados, altas, ...etiquetas(mes) }] : [],
  )
  const ciclos = (mantenimientos ?? []).flatMap(({ mes, finalizados = 0, fallidos = 0 }) =>
    mes ? [{ mes, finalizados, fallidos, ...etiquetas(mes) }] : [],
  )
  const vacio = !cargando && !ciclos.some((m) => m.finalizados + m.fallidos > 0)

  return (
    <div className="mb-5 grid gap-5 xl:grid-cols-2">
      <ChartCard title="Servidores registrados en el tiempo"
        description="Acumulado al cierre de cada mes · últimos 12 meses · UTC"
        legend={<ChartLegend items={[{ label: 'Registrados', colorVar: INFORMACION }]} />}>
        <div className="relative h-full min-w-0" aria-label="Evolución mensual de servidores registrados, incluidos los retirados">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={inventario} margin={{ top: 10, right: 12, bottom: 5, left: 0 }}>
              <CartesianGrid stroke="var(--color-line)" vertical={false} />
              <XAxis {...EJE} dataKey="etiqueta" minTickGap={18} />
              <YAxis {...EJE} allowDecimals={false} width={38} domain={[0, 'auto']} />
              <Tooltip content={ChartTooltip} labelFormatter={(_, p) => String(p?.[0]?.payload?.titulo ?? '')} />
              <Line dataKey="registrados" name="Registrados (servidores)" stroke={INFORMACION} strokeWidth={2.5}
                dot={{ r: 3 }} activeDot={{ r: 5 }} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
          {!cargando && !inventario.some((m) => m.registrados > 0) && <p className="absolute inset-x-6 top-1/3 rounded-lg bg-panel/95 p-3 text-center text-xs text-ink-soft">No hay altas de servidores registradas.</p>}
        </div>
      </ChartCard>
      <ChartCard title="Mantenimientos ejecutados en el tiempo"
        description="Por mes de fin de ejecución · últimos 12 meses · UTC"
        legend={<ChartLegend items={[{ label: 'Finalizados', colorVar: EXITO }, { label: 'Fallidos', colorVar: ERROR }]} />}>
        <div className="relative h-full min-w-0" aria-label="Cantidad mensual de mantenimientos por servidor, finalizados y fallidos">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={ciclos} margin={{ top: 10, right: 12, bottom: 5, left: 0 }}>
              <CartesianGrid stroke="var(--color-line)" vertical={false} />
              <XAxis {...EJE} dataKey="etiqueta" minTickGap={18} />
              <YAxis {...EJE} allowDecimals={false} width={38} />
              <Tooltip content={ChartTooltip} labelFormatter={(_, p) => String(p?.[0]?.payload?.titulo ?? '')}
                cursor={{ fill: 'var(--color-panel-muted)' }} />
              <Bar dataKey="finalizados" name="Finalizados (servidores)" stackId="m" fill={EXITO} maxBarSize={24}
                stroke="var(--color-panel)" strokeWidth={2} isAnimationActive={false} />
              <Bar dataKey="fallidos" name="Fallidos (servidores)" stackId="m" fill={ERROR} maxBarSize={24}
                stroke="var(--color-panel)" strokeWidth={2} radius={[4, 4, 0, 0]} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
          {vacio && <p className="absolute inset-x-6 top-1/3 rounded-lg bg-panel/95 p-3 text-center text-xs text-ink-soft">No hay ejecuciones terminadas en estos doce meses.</p>}
        </div>
      </ChartCard>
      <table className="sr-only"><caption>Evolución mensual: servidores registrados y mantenimientos por servidor</caption>
        <thead><tr><th>Mes</th><th>Servidores registrados</th><th>Altas</th><th>Mantenimientos finalizados</th><th>Mantenimientos fallidos</th></tr></thead>
        <tbody>{inventario.map((m) => {
          const ciclo = ciclos.find((c) => c.mes === m.mes)
          return <tr key={m.mes}><td>{m.titulo}</td><td>{m.registrados}</td><td>{m.altas}</td><td>{ciclo?.finalizados ?? 0}</td><td>{ciclo?.fallidos ?? 0}</td></tr>
        })}</tbody>
      </table>
    </div>
  )
}
