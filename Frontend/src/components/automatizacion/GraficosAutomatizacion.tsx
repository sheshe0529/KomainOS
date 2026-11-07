import type { ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { TooltipContentProps } from 'recharts'
import type { EstadoOrden, OrigenIncidencia } from '@/api/dominio'
import type { Conteo, ResumenGraficos } from '@/api/types'
import type { Consulta } from '@/hooks/useConsulta'
import { ChartCard } from '@/components/dashboard/ChartCard'
import { ChartLegend } from '@/components/dashboard/ChartLegend'
import { ChartTooltip } from '@/components/dashboard/ChartTooltip'
import { EvolucionDashboard } from '@/components/dashboard/EvolucionDashboard'
import { MensajeError } from '@/components/ui/MensajeError'
import { ORIGEN_INCIDENCIA } from '@/utils/automatizacion'
import { ETIQUETA_ESTADO_ORDEN } from '@/utils/etiquetas'

const COLOR = {
  exito: 'var(--color-grafico-exito)',
  informativo: 'var(--color-grafico-informativo)',
  advertencia: 'var(--color-grafico-advertencia)',
  error: 'var(--color-grafico-error)',
  neutro: 'var(--color-grafico-neutro)',
}

/** Ejes y cuadrícula recesivos: líneas finas y sólidas, un tono sobre la superficie */
const EJE = { tickLine: false, axisLine: false, tick: { fill: 'var(--color-ink-faint)', fontSize: 10 } }
const ETIQUETA_EJE = { fill: 'var(--color-ink-soft)', fontSize: 11 }
/** La separación de 2 px entre segmentos y barras contiguas se dibuja con el color de la superficie */
const SEPARACION = { stroke: 'var(--color-panel)', strokeWidth: 2 }
const BARRA = 24

const MES = new Intl.DateTimeFormat('es-PE', { month: 'short' })
const MES_ANIO = new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' })

function mes(clave?: string, largo = false): string {
  const [anio, numero] = (clave ?? '').split('-').map(Number)
  if (!anio || !numero) return clave ?? ''
  const fecha = new Date(anio, numero - 1, 1)
  return largo ? MES_ANIO.format(fecha) : MES.format(fecha).replace('.', '')
}

function SinDatos({ children }: { children: ReactNode }) {
  return (
    <p className="absolute inset-x-8 top-1/3 rounded-xl bg-panel/95 p-4 text-center text-xs leading-relaxed text-ink-soft">
      {children}
    </p>
  )
}

const DIA = new Intl.DateTimeFormat('es-PE', { weekday: 'long', day: 'numeric', month: 'short', timeZone: 'UTC' })

/** Familias de estados: pocas porciones para que la dona se lea; el detalle de cada estado va en la lista */
const FAMILIAS: { etiqueta: string; color: string; estados: EstadoOrden[] }[] = [
  {
    etiqueta: 'En curso',
    color: COLOR.informativo,
    estados: ['PROGRAMADA', 'REPROGRAMADA', 'EN_EVALUACION', 'AUTORIZADA', 'EN_COLA', 'EN_EJECUCION'],
  },
  { etiqueta: 'Esperan una decisión', color: COLOR.advertencia, estados: ['PENDIENTE_AUTORIZACION', 'PENDIENTE_VALIDACION', 'SIN_IMPLEMENTACION'] },
  {
    etiqueta: 'Con incidencia o sin realizar',
    color: COLOR.error,
    estados: ['INCIDENCIA', 'RECHAZADA', 'AUTORIZACION_VENCIDA', 'VALIDACION_VENCIDA', 'CANCELADA'],
  },
  { etiqueta: 'Cerradas', color: COLOR.neutro, estados: ['CERRADA'] },
]

function TooltipFamilia({ active, payload }: TooltipContentProps) {
  if (!active || !payload?.length) return null
  const familia = payload[0].payload as { etiqueta: string; cantidad: number; color: string; total: number }
  return (
    <div className="rounded-lg border border-line bg-panel px-3 py-2 text-xs shadow-lg">
      <p className="flex items-center gap-1.5 font-medium text-ink">
        <span className="h-2 w-2 rounded-full" style={{ background: familia.color }} />
        {familia.etiqueta}
      </p>
      <p className="mt-1 text-ink-soft">
        <span className="font-mono font-medium text-ink">{familia.cantidad}</span> órdenes ·{' '}
        {Math.round((familia.cantidad / familia.total) * 100)} %
      </p>
    </div>
  )
}

/** Parte de un todo: una dona por familia de estados y, al lado, la cantidad de cada estado */
function OrdenesPorEstado({ conteos }: { conteos: Conteo[] }) {
  const cantidad = (estado: EstadoOrden) => conteos.find((c) => c.clave === estado)?.cantidad ?? 0
  const total = conteos.reduce((suma, c) => suma + (c.cantidad ?? 0), 0)
  const familias = FAMILIAS.map((f) => ({
    ...f,
    cantidad: f.estados.reduce((suma, e) => suma + cantidad(e), 0),
    total,
  })).filter((f) => f.cantidad > 0)

  if (total === 0) {
    return (
      <div className="relative h-full">
        <SinDatos>No se crearon órdenes en el periodo.</SinDatos>
      </div>
    )
  }
  return (
    <div className="grid h-full items-center gap-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <div className="relative h-48 sm:h-full" aria-label="Dona de órdenes por familia de estados">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={familias}
              dataKey="cantidad"
              nameKey="etiqueta"
              innerRadius="62%"
              outerRadius="92%"
              paddingAngle={0}
              stroke="var(--color-panel)"
              strokeWidth={2}
              isAnimationActive={false}
            >
              {familias.map((f) => (
                <Cell key={f.etiqueta} fill={f.color} />
              ))}
            </Pie>
            <Tooltip content={TooltipFamilia} />
          </PieChart>
        </ResponsiveContainer>
        <p className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
          <span className="font-mono text-2xl font-semibold tabular-nums text-ink">{total}</span>
          <span className="text-xs text-ink-soft">órdenes</span>
        </p>
      </div>
      <ul className="flex max-h-full flex-col gap-3 overflow-auto text-sm">
        {FAMILIAS.map((f) => {
          const estados = f.estados.filter((e) => cantidad(e) > 0)
          if (estados.length === 0) return null
          return (
            <li key={f.etiqueta}>
              <p className="flex items-center gap-2 font-medium text-ink">
                <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: f.color }} aria-hidden="true" />
                {f.etiqueta}
              </p>
              <ul className="mt-1 flex flex-col gap-0.5 pl-[18px] text-xs text-ink-soft">
                {estados.map((e) => (
                  <li key={e} className="flex items-baseline justify-between gap-3">
                    {ETIQUETA_ESTADO_ORDEN[e]}
                    <span className="font-mono tabular-nums text-ink">{cantidad(e)}</span>
                  </li>
                ))}
              </ul>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/** Gráficos de mantenimientos, órdenes, incidencias y ejecuciones del dashboard (RF67). */
export function GraficosAutomatizacion({ informe }: { informe: Consulta<ResumenGraficos> }) {
  const datos = informe.datos
  const meses = (datos?.mantenimientosPorMes ?? []).map((m) => ({ ...m, etiqueta: mes(m.mes), titulo: mes(m.mes, true) }))
  const hayMantenimientos = meses.some((m) => (m.exitosos ?? 0) + (m.sinIntervencion ?? 0) + (m.conIncidencia ?? 0) + (m.noRealizados ?? 0) > 0)
  const incidencias = (datos?.incidenciasPorOrigen ?? []).map((i) => ({
    etiqueta: ORIGEN_INCIDENCIA[i.origen as OrigenIncidencia] ?? i.origen,
    abiertas: i.abiertas,
    cerradas: i.cerradas,
  }))

  return (
    <section className="mt-6" aria-label="Gráficos del mantenimiento">
      <MensajeError error={informe.error} onReintentar={informe.recargar} />
      <EvolucionDashboard servidores={datos?.servidoresPorMes} mantenimientos={datos?.mantenimientosEjecutadosPorMes} cargando={informe.cargando} />
      <div className="grid gap-5 xl:grid-cols-2">
        <ChartCard
          title="Mantenimientos por resultado"
          description="Últimos 6 meses · cada servidor de una orden cerrada cuenta una vez, en el mes del cierre"
          legend={
            <ChartLegend
              items={[
                { label: 'Exitoso', colorVar: COLOR.exito },
                { label: 'Sin intervención', colorVar: COLOR.informativo },
                { label: 'Con incidencia', colorVar: COLOR.advertencia },
                { label: 'No realizado', colorVar: COLOR.neutro },
              ]}
            />
          }
        >
          <div className="relative h-full min-w-0" aria-label="Columnas apiladas de mantenimientos por mes">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={meses} margin={{ top: 8, right: 10, bottom: 0, left: 0 }} barCategoryGap="30%">
                <CartesianGrid stroke="var(--color-line)" vertical={false} />
                <XAxis {...EJE} dataKey="etiqueta" />
                <YAxis {...EJE} width={32} allowDecimals={false} />
                <Tooltip
                  content={ChartTooltip}
                  cursor={{ fill: 'var(--color-panel-muted)' }}
                  labelFormatter={(_, payload) => String(payload?.[0]?.payload?.titulo ?? '')}
                />
                <Bar stackId="r" dataKey="exitosos" name="Exitoso" fill={COLOR.exito} maxBarSize={BARRA} {...SEPARACION} isAnimationActive={false} />
                <Bar stackId="r" dataKey="sinIntervencion" name="Sin intervención" fill={COLOR.informativo} maxBarSize={BARRA} {...SEPARACION} isAnimationActive={false} />
                <Bar stackId="r" dataKey="conIncidencia" name="Con incidencia" fill={COLOR.advertencia} maxBarSize={BARRA} {...SEPARACION} isAnimationActive={false} />
                <Bar stackId="r" dataKey="noRealizados" name="No realizado" fill={COLOR.neutro} maxBarSize={BARRA} radius={[4, 4, 0, 0]} {...SEPARACION} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
            {!informe.cargando && !hayMantenimientos && <SinDatos>No se cerraron mantenimientos en los últimos 6 meses.</SinDatos>}
          </div>
        </ChartCard>

        <ChartCard title="Órdenes por estado" description="Creadas en el periodo, según su estado actual">
          <OrdenesPorEstado conteos={datos?.ordenesPorEstado ?? []} />
        </ChartCard>

        <ChartCard
          title="Incidencias por origen"
          description="Abiertas en el periodo"
          legend={
            <ChartLegend
              items={[
                { label: 'Sin cerrar', colorVar: COLOR.error },
                { label: 'Cerradas', colorVar: COLOR.neutro },
              ]}
            />
          }
        >
          <div className="relative h-full min-w-0" aria-label="Barras horizontales apiladas de incidencias por origen">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={incidencias} layout="vertical" margin={{ top: 0, right: 16, bottom: 0, left: 0 }} barCategoryGap="25%">
                <CartesianGrid stroke="var(--color-line)" horizontal={false} />
                <XAxis {...EJE} type="number" allowDecimals={false} />
                <YAxis {...EJE} type="category" dataKey="etiqueta" width={170} tick={ETIQUETA_EJE} />
                <Tooltip content={ChartTooltip} cursor={{ fill: 'var(--color-panel-muted)' }} />
                <Bar stackId="i" dataKey="abiertas" name="Sin cerrar" fill={COLOR.error} maxBarSize={BARRA} {...SEPARACION} isAnimationActive={false} />
                <Bar stackId="i" dataKey="cerradas" name="Cerradas" fill={COLOR.neutro} maxBarSize={BARRA} radius={[0, 4, 4, 0]} {...SEPARACION} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
            {!informe.cargando && incidencias.length === 0 && <SinDatos>Sin incidencias en el periodo.</SinDatos>}
          </div>
        </ChartCard>

        <ChartCard
          title="Ejecuciones de scripts"
          description="Últimos 7 días, por resultado · UTC"
          legend={
            <ChartLegend
              items={[
                { label: 'Éxito', colorVar: COLOR.exito },
                { label: 'Advertencia', colorVar: COLOR.advertencia },
                { label: 'Error', colorVar: COLOR.error },
              ]}
            />
          }
        >
          <div className="h-full min-w-0" aria-label="Columnas apiladas de ejecuciones por día">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={datos?.ejecuciones ?? []} margin={{ top: 8, right: 10, bottom: 0, left: 0 }} barCategoryGap="30%">
                <CartesianGrid stroke="var(--color-line)" vertical={false} />
                <XAxis
                  {...EJE}
                  dataKey="fecha"
                  tickFormatter={(v) =>
                    new Date(`${v}T00:00:00Z`).toLocaleDateString('es-PE', { weekday: 'short', timeZone: 'UTC' })
                  }
                />
                <YAxis {...EJE} width={32} allowDecimals={false} />
                <Tooltip
                  content={ChartTooltip}
                  cursor={{ fill: 'var(--color-panel-muted)' }}
                  labelFormatter={(v) => DIA.format(new Date(`${v}T00:00:00Z`))}
                />
                <Bar stackId="resultado" dataKey="exito" name="Éxito" fill={COLOR.exito} maxBarSize={BARRA} {...SEPARACION} isAnimationActive={false} />
                <Bar stackId="resultado" dataKey="advertencia" name="Advertencia" fill={COLOR.advertencia} maxBarSize={BARRA} {...SEPARACION} isAnimationActive={false} />
                <Bar stackId="resultado" dataKey="error" name="Error" fill={COLOR.error} maxBarSize={BARRA} radius={[4, 4, 0, 0]} {...SEPARACION} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </ChartCard>

      </div>
    </section>
  )
}
