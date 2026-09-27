interface LegendEntry {
  label: string
  colorVar: string
}

export function ChartLegend({ items }: { items: LegendEntry[] }) {
  return (
    <ul className="flex flex-wrap items-center gap-x-3 gap-y-1">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-1.5 text-xs text-ink-soft">
          <span className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: item.colorVar }} />
          {item.label}
        </li>
      ))}
    </ul>
  )
}
