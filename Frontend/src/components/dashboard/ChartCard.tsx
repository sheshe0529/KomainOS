import type { ReactNode } from 'react'

interface ChartCardProps {
  title: string
  description?: string
  legend?: ReactNode
  children: ReactNode
}

export function ChartCard({ title, description, legend, children }: ChartCardProps) {
  return (
    <div className="rounded-xl border border-line bg-panel p-5">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
        <div>
          <h3 className="text-sm font-semibold text-ink">{title}</h3>
          {description && <p className="mt-0.5 text-xs text-ink-soft">{description}</p>}
        </div>
        {legend}
      </div>
      <div className="h-64 sm:h-72">{children}</div>
    </div>
  )
}
