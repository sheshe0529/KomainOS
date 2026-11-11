import { CircleCheck, CircleX, TriangleAlert } from 'lucide-react'
import type { TipoFallo } from '@/api/dominio'
import { detalleFallo, FALLO } from '@/utils/automatizacion'

interface ResultadoFalloProps {
  correcto: boolean
  fallo?: TipoFallo
  mensaje?: string
  textoCorrecto: string
  /** Qué se probó, junto al título del fallo (por ejemplo, la cuenta) */
  textoFallido?: string
  /** Para qué se usa lo probado (por ejemplo, «Evaluación y mantenimiento») */
  etiqueta?: string
  /** Correcto, pero con algo que conviene revisar: se muestra en ámbar en lugar de verde */
  advertencia?: string
}

/** RF71: el resultado distingue comunicación, autenticación y permisos, y sugiere qué revisar */
export function ResultadoFallo({ correcto, fallo, mensaje, textoCorrecto, textoFallido, etiqueta, advertencia }: ResultadoFalloProps) {
  const tono = !correcto ? 'bg-danger-soft text-danger' : advertencia ? 'bg-warning-soft text-warning' : 'bg-success-soft text-success'
  const Icono = !correcto ? CircleX : advertencia ? TriangleAlert : CircleCheck
  const tituloFallo = fallo ? FALLO[fallo].etiqueta : 'No se completó'
  return (
    <div role="status" className={`flex items-start gap-3 rounded-lg px-4 py-3 text-sm ${tono}`}>
      <Icono className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
          <p className="font-medium [overflow-wrap:anywhere]">
            {correcto ? textoCorrecto : textoFallido ? `${tituloFallo} con ${textoFallido}` : tituloFallo}
          </p>
          {etiqueta && <span className="text-xs font-medium text-ink-soft">{etiqueta}</span>}
        </div>
        {mensaje && <p className="whitespace-pre-wrap break-words text-ink">{detalleFallo(fallo, mensaje)}</p>}
        {correcto && advertencia && <p className="text-ink-soft">{advertencia}</p>}
        {!correcto && fallo && <p className="text-ink-soft">{FALLO[fallo].sugerencia}</p>}
      </div>
    </div>
  )
}
