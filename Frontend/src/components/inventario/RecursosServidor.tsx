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
  const ninguno = recursos.every((r) => r.valor === undefined || r.valor === null)

  return (
    <div className="flex flex-col gap-3">
      <dl className="grid grid-cols-3 gap-3">
        {recursos.map(({ icono: Icono, etiqueta, valor, unidad }) => {
          const registrado = valor !== undefined && valor !== null
          return (
            <div key={etiqueta} className="flex flex-col items-center gap-2 rounded-lg border border-line px-2 py-3 text-center">
              <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent-soft text-accent">
                <Icono className="h-5 w-5" aria-hidden="true" />
              </span>
              <dt className="text-xs text-ink-soft">{etiqueta}</dt>
              <dd className="flex items-baseline gap-1">
                <span className={`font-mono text-xl font-semibold tabular-nums ${registrado ? 'text-ink' : 'text-ink-faint'}`}>
                  {registrado ? formatear(valor) : '—'}
                </span>
                {registrado && <span className="text-xs text-ink-soft">{unidad}</span>}
              </dd>
            </div>
          )
        })}
      </dl>
      {ninguno && <p className="text-xs text-ink-faint">Sin recursos registrados. Se completan al editar el servidor.</p>}
    </div>
  )
}
