import { CircleAlert, CircleCheck, CircleX, Info } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { EstadoComprobacion } from '@/api/dominio'
import type { Preparacion } from '@/api/types'

interface PreparacionServidorProps {
  preparacion: Preparacion
}

const ICONO: Record<EstadoComprobacion, { icono: LucideIcon; color: string }> = {
  OK: { icono: CircleCheck, color: 'text-success' },
  INFO: { icono: Info, color: 'text-ink-faint' },
  ADVERTENCIA: { icono: CircleAlert, color: 'text-warning' },
  ERROR: { icono: CircleX, color: 'text-danger' },
}

/** Lista de comprobación: estado, cuenta, análisis compatible, política, ventana, umbrales, tareas y transporte */
export function PreparacionServidor({ preparacion: p }: PreparacionServidorProps) {
  const comprobaciones = p.comprobaciones ?? []
  const errores = comprobaciones.filter((c) => c.estado === 'ERROR').length

  return (
    <div className="flex flex-col gap-3">
      <p
        className={`rounded-lg px-3 py-2 text-sm font-medium ${
          p.listo ? 'bg-success-soft text-success' : 'bg-danger-soft text-danger'
        }`}
      >
        {p.listo
          ? 'Listo: el ciclo automático puede analizarlo y ejecutar sus tareas.'
          : `${errores} ${errores === 1 ? 'requisito impide' : 'requisitos impiden'} el ciclo automático en este servidor.`}
      </p>
      <ul className="flex flex-col gap-2.5">
        {comprobaciones.map((c) => {
          const { icono: Icono, color } = ICONO[c.estado ?? 'INFO']
          return (
            <li key={c.codigo} className="flex items-start gap-2.5 text-sm">
              <Icono className={`mt-0.5 h-4 w-4 shrink-0 ${color}`} aria-label={c.estado} />
              <span className="min-w-0">
                <span className="block text-ink">{c.titulo}</span>
                {c.detalle && <span className="block text-xs text-ink-soft">{c.detalle}</span>}
              </span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
