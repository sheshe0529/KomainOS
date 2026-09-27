import type { TooltipContentProps } from 'recharts'

/**
 * Tooltip personalizado para los gráficos del dashboard: al pasar el
 * cursor sobre un punto o una barra, muestra el detalle de cada serie
 * (color, nombre y valor exacto) en vez del tooltip por defecto de
 * Recharts, y respeta los tokens de tema para verse bien en modo oscuro.
 */
export function ChartTooltip({ active, payload, label }: TooltipContentProps) {
  if (!active || !payload || payload.length === 0) return null

  return (
    <div className="min-w-[160px] rounded-lg border border-line bg-panel px-3 py-2 text-xs shadow-lg shadow-black/10">
      {label !== undefined && <p className="mb-1.5 font-medium text-ink">{label}</p>}
      <div className="flex flex-col gap-1">
        {payload.map((entry, index) => (
          <div key={`${entry.name ?? 'serie'}-${index}`} className="flex items-center justify-between gap-4">
            <span className="flex items-center gap-1.5 text-ink-soft">
              <span className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: entry.color }} />
              {entry.name}
            </span>
            <span className="font-mono font-medium text-ink">{entry.value}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
