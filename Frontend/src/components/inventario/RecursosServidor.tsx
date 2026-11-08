import { Cpu, HardDrive, MemoryStick } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface RecursosServidorProps {
  cantidadCpu?: number
  ramGb?: number
  hdVirtualGb?: number
}

interface Recurso {
  icono: LucideIcon
  etiqueta: string
  valor?: number
  unidad: string
}

function formatear(valor: number): string {
  return valor.toLocaleString('es-PE', { maximumFractionDigits: 2 })
}

export function RecursosServidor({ cantidadCpu, ramGb, hdVirtualGb }: RecursosServidorProps) {
  const recursos: Recurso[] = [
    { icono: Cpu, etiqueta: 'CPU', valor: cantidadCpu, unidad: 'vCPU' },
    { icono: MemoryStick, etiqueta: 'Memoria RAM', valor: ramGb, unidad: 'GB' },
    { icono: HardDrive, etiqueta: 'Disco virtual', valor: hdVirtualGb, unidad: 'GB' },
  ]

  return (
    <dl className="grid grid-cols-3 gap-2">
      {recursos.map(({ icono: Icono, etiqueta, valor, unidad }) => {
        const registrado = valor !== undefined && valor !== null
        return (
          <div key={etiqueta} className="flex min-w-0 flex-col items-center gap-1.5 rounded-lg border border-line bg-panel-muted px-1 py-2.5 text-center">
            <span className="flex h-8 w-8 items-center justify-center rounded-md bg-accent-soft text-accent">
              <Icono className="h-4 w-4" aria-hidden="true" />
            </span>
            <dt className="flex flex-1 items-center justify-center text-[11px] leading-tight text-ink-soft">{etiqueta}</dt>
            <dd className="flex min-w-0 max-w-full flex-wrap items-baseline justify-center gap-x-0.5 leading-tight">
              <span className={`min-w-0 break-words font-mono text-xl leading-tight font-semibold tabular-nums ${registrado ? 'text-ink' : 'text-ink-faint'}`}>
                {registrado ? formatear(valor) : '—'}
              </span>
              {registrado && <span className="text-[10px] text-ink-soft">{unidad}</span>}
            </dd>
          </div>
        )
      })}
    </dl>
  )
}
