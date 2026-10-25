import { useId } from 'react'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { TooltipContentProps } from 'recharts'
import { automatizacionApi } from '@/api/automatizacion'
import { useConsulta } from '@/hooks/useConsulta'
import { ChartCard } from '@/components/dashboard/ChartCard'
import { ChartLegend } from '@/components/dashboard/ChartLegend'
import { ChartTooltip } from '@/components/dashboard/ChartTooltip'
import { MensajeError } from '@/components/ui/MensajeError'
import { formatearFechaHora } from '@/utils/formato'

function TooltipRecursos({ active, payload, label }: TooltipContentProps) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded-lg border border-line bg-panel p-3 text-xs shadow-lg">
      <p className="mb-2 font-medium">{formatearFechaHora(String(label))}</p>
      {payload.map((p) => (
        <p key={String(p.dataKey)} className="flex items-center justify-between gap-4 py-0.5">
          <span className="flex items-center gap-1.5 text-ink-soft">
            <span className="h-2 w-2 rounded-full" style={{ background: p.color }} />
            {p.name}
          </span>
          <strong>{Number(p.value).toLocaleString('es-PE', { maximumFractionDigits: 1 })} %</strong>
        </p>
      ))}
    </div>
  )
}
export function GraficosAutomatizacion() {
  const informe = useConsulta(automatizacionApi.graficos, [])
  const id = useId().replaceAll(':', '')
  const recursos = informe.datos?.recursos ?? []
  const hayMuestras = recursos.some((r) => r.cpu != null || r.memoria != null)
  const eje = { tickLine: false, axisLine: false, tick: { fill: 'var(--color-ink-faint)', fontSize: 10 } }
  return (
    <section className="mt-6" aria-label="Gráficos de recursos y ejecuciones">
      <MensajeError error={informe.error} onReintentar={informe.recargar} />
      <div className="grid gap-5 xl:grid-cols-2">
        <ChartCard
          title="Uso de recursos (24 h)"
          description="Promedio de CPU y RAM entre los servidores activos · UTC"
          legend={
            <ChartLegend
              items={[
                { label: 'CPU', colorVar: 'var(--color-serie-1)' },
                { label: 'RAM', colorVar: 'var(--color-serie-2)' },
              ]}
            />
          }
        >
          <div className="relative h-full min-w-0" aria-label="Gráfico de áreas de CPU y RAM">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={recursos} margin={{ top: 8, right: 10, bottom: 0, left: 0 }}>
                <defs>
                  <linearGradient id={`${id}-cpu`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-serie-1)" stopOpacity={0.22} />
                    <stop offset="100%" stopColor="var(--color-serie-1)" stopOpacity={0.01} />
                  </linearGradient>
                  <linearGradient id={`${id}-ram`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-serie-2)" stopOpacity={0.22} />
                    <stop offset="100%" stopColor="var(--color-serie-2)" stopOpacity={0.01} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="var(--color-line)" strokeDasharray="3 3" vertical={false} />
                <XAxis {...eje} dataKey="hora" tickFormatter={(v) => String(v).slice(11, 16)} interval={2} />
                <YAxis {...eje} width={42} domain={[0, 100]} tickFormatter={(v) => `${v}%`} />
                <Tooltip content={TooltipRecursos} cursor={{ stroke: 'var(--color-line)' }} />
                <Area
                  type="monotone"
                  dataKey="cpu"
                  name="CPU"
                  stroke="var(--color-serie-1)"
                  fill={`url(#${id}-cpu)`}
                  strokeWidth={1.8}
                  isAnimationActive={false}
                  connectNulls={false}
                />
                <Area
                  type="monotone"
                  dataKey="memoria"
                  name="RAM"
                  stroke="var(--color-serie-2)"
                  fill={`url(#${id}-ram)`}
                  strokeWidth={1.8}
                  isAnimationActive={false}
                  connectNulls={false}
                />
              </AreaChart>
            </ResponsiveContainer>
            {!informe.cargando && !hayMuestras && (
              <p className="absolute inset-x-12 top-1/3 rounded-xl bg-panel/95 p-4 text-center text-xs leading-relaxed text-ink-soft">
                No hay mediciones de CPU o RAM en las últimas 24 horas. Se mostrarán al realizar una revisión del
                servidor.
              </p>
            )}
          </div>
        </ChartCard>
        <ChartCard
          title="Ejecuciones de scripts"
          description="Últimos 7 días, por resultado · UTC"
          legend={
            <ChartLegend
              items={[
                { label: 'Éxito', colorVar: 'var(--color-success)' },
                { label: 'Advertencia', colorVar: 'var(--color-warning)' },
                { label: 'Error', colorVar: 'var(--color-danger)' },
              ]}
            />
          }
        >
          <div className="h-full min-w-0" aria-label="Gráfico de barras apiladas de ejecuciones">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={informe.datos?.ejecuciones ?? []}
                margin={{ top: 8, right: 10, bottom: 0, left: 0 }}
                barCategoryGap="20%"
              >
                <CartesianGrid stroke="var(--color-line)" strokeDasharray="3 3" vertical={false} />
                <XAxis
                  {...eje}
                  dataKey="fecha"
                  tickFormatter={(v) =>
                    new Date(`${v}T00:00:00Z`).toLocaleDateString('es-PE', { weekday: 'short', timeZone: 'UTC' })
                  }
                />
                <YAxis {...eje} width={35} allowDecimals={false} />
                <Tooltip content={ChartTooltip} cursor={{ fill: 'var(--color-panel-muted)' }} />
                <Bar
                  stackId="resultado"
                  dataKey="exito"
                  name="Éxito"
                  fill="var(--color-success)"
                  isAnimationActive={false}
                />
                <Bar
                  stackId="resultado"
                  dataKey="advertencia"
                  name="Advertencia"
                  fill="var(--color-warning)"
                  isAnimationActive={false}
                />
                <Bar
                  stackId="resultado"
                  dataKey="error"
                  name="Error"
                  fill="var(--color-danger)"
                  isAnimationActive={false}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </ChartCard>
      </div>
      <p className="mt-3 text-xs leading-relaxed text-ink-faint">
        La advertencia identifica tareas terminadas cuya validación técnica posterior resultó no conforme. Las tareas no
        iniciadas se excluyen. Las horas sin mediciones quedan sin datos.
        {informe.datos?.fechaActualizacion && ` Actualizado: ${formatearFechaHora(informe.datos.fechaActualizacion)}.`}
      </p>
    </section>
  )
}
