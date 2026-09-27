import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { TooltipContentProps } from 'recharts'

export interface PuntoHora {
  hora: string
  ordenes: number
}

/** Tooltip de una barra: hora y cantidad de órdenes que inician en ella. */
function TooltipHora({ active, payload, label }: TooltipContentProps) {
  if (!active || !payload || payload.length === 0) return null
  const valor = Number(payload[0].value ?? 0)
  return (
    <div className="rounded-lg border border-line bg-panel px-3 py-2 text-xs shadow-lg shadow-black/10">
      <p className="font-medium text-ink">
        {label}:00 – {label}:59
      </p>
      <p className="mt-0.5 text-ink-soft">
        <span className="font-mono font-medium text-ink">{valor}</span> {valor === 1 ? 'orden inicia' : 'órdenes inician'}
      </p>
    </div>
  )
}

/**
 * Distribución horaria del día (RF32, HU20 CA3): una sola serie, sin leyenda
 * (el título la nombra), barras finas con el extremo redondeado sobre la base
 * y tooltip por barra. La lista de órdenes debajo es su vista de tabla.
 */
export function DistribucionHorariaChart({ datos }: { datos: PuntoHora[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={datos} margin={{ top: 8, right: 8, bottom: 0, left: -24 }} barCategoryGap={2}>
        <CartesianGrid vertical={false} stroke="var(--color-line)" strokeDasharray="0" />
        {/* Las 24 horas siempre rotuladas, también las que no tienen órdenes. */}
        <XAxis
          dataKey="hora"
          tickLine={false}
          axisLine={{ stroke: 'var(--color-line)' }}
          tick={{ fill: 'var(--color-ink-faint)', fontSize: 10, fontFamily: 'var(--font-mono)' }}
          interval={0}
          minTickGap={0}
        />
        <YAxis
          allowDecimals={false}
          tickLine={false}
          axisLine={false}
          tick={{ fill: 'var(--color-ink-faint)', fontSize: 11, fontFamily: 'var(--font-mono)' }}
        />
        <Tooltip content={TooltipHora} cursor={{ fill: 'var(--color-panel-muted)' }} />
        <Bar dataKey="ordenes" name="Órdenes" fill="var(--color-serie-1)" radius={[4, 4, 0, 0]} maxBarSize={28} />
      </BarChart>
    </ResponsiveContainer>
  )
}
