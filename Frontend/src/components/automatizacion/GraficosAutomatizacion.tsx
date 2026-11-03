import { useId } from 'react'
import type { ReactNode } from 'react'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { TooltipContentProps } from 'recharts'
import { CircleAlert, CircleCheck, CircleDashed, CircleX } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { operacionApi } from '@/api/automatizacion'
import type { EstadoOrden, OrigenIncidencia } from '@/api/dominio'
import type { RecursoHora, SaludServidores } from '@/api/types'
import { useConsulta } from '@/hooks/useConsulta'
import { ChartCard } from '@/components/dashboard/ChartCard'
import { ChartLegend } from '@/components/dashboard/ChartLegend'
import { ChartTooltip } from '@/components/dashboard/ChartTooltip'
import { MensajeError } from '@/components/ui/MensajeError'
import { ORIGEN_INCIDENCIA } from '@/utils/automatizacion'
import { ETIQUETA_ESTADO_ORDEN } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'

interface GraficosAutomatizacionProps {
  /** Periodo de los indicadores (ISO); sin él, los últimos 30 días */
  desde?: string
  hasta?: string
  /** Cambiarlo vuelve a consultar */
  version?: number
}

const COLOR = {
  exito: 'var(--color-grafico-exito)',
  informativo: 'var(--color-grafico-informativo)',
  advertencia: 'var(--color-grafico-advertencia)',
  error: 'var(--color-grafico-error)',
  neutro: 'var(--color-grafico-neutro)',
  serie: 'var(--color-serie-1)',
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

/** Una hora con medición entre dos sin ella no forma área: se marca con un punto de 8 px */
function puntoAislado(datos: RecursoHora[], clave: 'cpu' | 'memoria', color: string) {
  const valor = (i: number) => datos[i]?.[clave]
  return function Punto({ cx, cy, index = 0 }: { cx?: number; cy?: number; index?: number }) {
    const aislado = valor(index) != null && valor(index - 1) == null && valor(index + 1) == null
    if (!aislado || cx === undefined || cy === undefined) return <g key={`${clave}-${index}`} />
    return <circle key={`${clave}-${index}`} cx={cx} cy={cy} r={4} fill={color} stroke="var(--color-panel)" strokeWidth={2} />
  }
}

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

const SALUD: { clave: keyof SaludServidores; etiqueta: string; color: string; icono: LucideIcon; detalle: string }[] = [
  { clave: 'normal', etiqueta: 'Normal', color: COLOR.exito, icono: CircleCheck, detalle: 'Todas sus métricas dentro de los umbrales' },
  { clave: 'advertencia', etiqueta: 'Advertencia', color: COLOR.advertencia, icono: CircleAlert, detalle: 'Alguna métrica en advertencia' },
  { clave: 'critico', etiqueta: 'Crítico', color: COLOR.error, icono: CircleX, detalle: 'Alguna métrica en nivel crítico' },
  { clave: 'sinAnalisis', etiqueta: 'Sin análisis', color: COLOR.neutro, icono: CircleDashed, detalle: 'Aún no tiene un análisis concluyente' },
]

/** Parte de un todo con cuatro estados: una barra apilada y, debajo, la cifra de cada uno con su ícono */
function SaludDeServidores({ salud }: { salud?: SaludServidores }) {
  const cantidad = (clave: keyof SaludServidores) => salud?.[clave] ?? 0
  const total = SALUD.reduce((suma, s) => suma + cantidad(s.clave), 0)
  if (total === 0) {
    return (
      <div className="relative h-full">
        <SinDatos>No hay servidores activos.</SinDatos>
      </div>
    )
  }
  return (
    <div className="flex h-full flex-col justify-center gap-5">
      <p className="text-sm text-ink-soft">
        <span className="font-mono text-3xl font-semibold tabular-nums text-ink">{total}</span> servidores activos
      </p>
      <div className="flex h-6 w-full gap-0.5" role="img" aria-label="Distribución de los servidores por nivel">
        {SALUD.filter((s) => cantidad(s.clave) > 0).map((s) => (
          <span
            key={s.clave}
            title={`${s.etiqueta}: ${cantidad(s.clave)} de ${total}`}
            className="h-full min-w-1.5 first:rounded-l last:rounded-r"
            style={{ flexGrow: cantidad(s.clave), background: s.color }}
          />
        ))}
      </div>
      <ul className="grid grid-cols-2 gap-x-6 gap-y-3">
        {SALUD.map(({ clave, etiqueta, color, icono: Icono, detalle }) => (
          <li key={clave} className="flex items-start gap-2 text-sm">
            <Icono className="mt-0.5 h-4 w-4 shrink-0" style={{ color }} aria-hidden="true" />
            <span className="min-w-0">
              <span className="flex items-baseline gap-2 text-ink">
                {etiqueta}
                <span className="font-mono font-semibold tabular-nums">{cantidad(clave)}</span>
                <span className="text-xs text-ink-faint">{Math.round((cantidad(clave) / total) * 100)} %</span>
              </span>
              <span className="block text-xs text-ink-soft">{detalle}</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * Gráficos del dashboard (RF67). Cada uno responde una pregunta: cómo terminan los mantenimientos, en qué estado están
 * las órdenes, qué tan sanos están los servidores, de dónde vienen las incidencias, cómo van las ejecuciones y cuánto
 * consumen los servidores
 */
export function GraficosAutomatizacion({ desde, hasta, version = 0 }: GraficosAutomatizacionProps) {
  const informe = useConsulta(() => operacionApi.graficos(desde, hasta), [desde, hasta, version])
  const id = useId().replaceAll(':', '')
  const datos = informe.datos
  const recursos = datos?.recursos ?? []
  const hayMuestras = recursos.some((r) => r.cpu != null || r.memoria != null)
  const meses = (datos?.mantenimientosPorMes ?? []).map((m) => ({ ...m, etiqueta: mes(m.mes), titulo: mes(m.mes, true) }))
  const hayMantenimientos = meses.some((m) => (m.exitosos ?? 0) + (m.sinIntervencion ?? 0) + (m.conIncidencia ?? 0) + (m.noRealizados ?? 0) > 0)
  const ordenes = (datos?.ordenesPorEstado ?? []).map((o) => ({
    etiqueta: ETIQUETA_ESTADO_ORDEN[o.clave as EstadoOrden] ?? o.clave,
    cantidad: o.cantidad,
  }))
  const incidencias = (datos?.incidenciasPorOrigen ?? []).map((i) => ({
    etiqueta: ORIGEN_INCIDENCIA[i.origen as OrigenIncidencia] ?? i.origen,
    abiertas: i.abiertas,
    cerradas: i.cerradas,
  }))

  return (
    <section className="mt-6" aria-label="Gráficos del mantenimiento">
      <MensajeError error={informe.error} onReintentar={informe.recargar} />
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
          <div className="relative h-full min-w-0" aria-label="Barras horizontales de órdenes por estado">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={ordenes} layout="vertical" margin={{ top: 0, right: 36, bottom: 0, left: 0 }} barCategoryGap="25%">
                <CartesianGrid stroke="var(--color-line)" horizontal={false} />
                <XAxis {...EJE} type="number" allowDecimals={false} />
                <YAxis {...EJE} type="category" dataKey="etiqueta" width={150} tick={ETIQUETA_EJE} />
                <Tooltip content={ChartTooltip} cursor={{ fill: 'var(--color-panel-muted)' }} />
                <Bar dataKey="cantidad" name="Órdenes" fill={COLOR.serie} maxBarSize={BARRA} radius={[0, 4, 4, 0]} isAnimationActive={false}>
                  <LabelList dataKey="cantidad" position="right" fill="var(--color-ink-soft)" fontSize={11} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
            {!informe.cargando && ordenes.length === 0 && <SinDatos>No se crearon órdenes en el periodo.</SinDatos>}
          </div>
        </ChartCard>

        <ChartCard title="Salud de los servidores" description="Nivel del último análisis concluyente de cada servidor activo">
          <SaludDeServidores salud={datos?.saludServidores} />
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
          <div className="relative h-full min-w-0" aria-label="Áreas de CPU y RAM por hora">
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
                <CartesianGrid stroke="var(--color-line)" vertical={false} />
                <XAxis {...EJE} dataKey="hora" tickFormatter={(v) => String(v).slice(11, 16)} interval={2} />
                <YAxis {...EJE} width={42} domain={[0, 100]} tickFormatter={(v) => `${v}%`} />
                <Tooltip content={TooltipRecursos} cursor={{ stroke: 'var(--color-line)' }} />
                <Area
                  type="monotone"
                  dataKey="cpu"
                  name="CPU"
                  stroke="var(--color-serie-1)"
                  fill={`url(#${id}-cpu)`}
                  strokeWidth={2}
                  dot={puntoAislado(recursos, 'cpu', 'var(--color-serie-1)')}
                  isAnimationActive={false}
                  connectNulls={false}
                />
                <Area
                  type="monotone"
                  dataKey="memoria"
                  name="RAM"
                  stroke="var(--color-serie-2)"
                  fill={`url(#${id}-ram)`}
                  strokeWidth={2}
                  dot={puntoAislado(recursos, 'memoria', 'var(--color-serie-2)')}
                  isAnimationActive={false}
                  connectNulls={false}
                />
              </AreaChart>
            </ResponsiveContainer>
            {!informe.cargando && !hayMuestras && (
              <SinDatos>
                No hay mediciones de CPU o RAM en las últimas 24 horas. Se mostrarán al realizar una revisión del servidor.
              </SinDatos>
            )}
          </div>
        </ChartCard>
      </div>
      <p className="mt-3 text-xs leading-relaxed text-ink-faint">
        «No realizado» agrupa los mantenimientos rechazados, con la autorización vencida, cancelados o cerrados sin una
        implementación compatible. En las ejecuciones, la advertencia identifica tareas terminadas cuya validación técnica
        resultó no conforme; las tareas no iniciadas se excluyen. Las horas sin mediciones quedan sin datos.
        {datos?.fechaActualizacion && ` Actualizado: ${formatearFechaHora(datos.fechaActualizacion)}.`}
      </p>
    </section>
  )
}
