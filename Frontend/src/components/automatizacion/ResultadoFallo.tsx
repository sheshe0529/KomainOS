import { CircleCheck, CircleX } from 'lucide-react'
import type { TipoFallo } from '@/api/dominio'
import { detalleFallo, FALLO } from '@/utils/automatizacion'

interface ResultadoFalloProps {
  correcto: boolean
  fallo?: TipoFallo
  mensaje?: string
  textoCorrecto: string
}

/** RF71: el resultado distingue comunicación, autenticación y permisos, y sugiere qué revisar */
export function ResultadoFallo({ correcto, fallo, mensaje, textoCorrecto }: ResultadoFalloProps) {
  return (
    <div
      role="status"
      className={`flex items-start gap-3 rounded-lg px-4 py-3 text-sm ${
        correcto ? 'bg-success-soft text-success' : 'bg-danger-soft text-danger'
      }`}
    >
      {correcto ? (
        <CircleCheck className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
      ) : (
        <CircleX className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
      )}
      <div className="flex min-w-0 flex-col gap-1">
        <p className="font-medium">{correcto ? textoCorrecto : fallo ? FALLO[fallo].etiqueta : 'No se completó'}</p>
        {mensaje && <p className="break-words text-ink">{detalleFallo(fallo, mensaje)}</p>}
        {!correcto && fallo && <p className="text-ink-soft">{FALLO[fallo].sugerencia}</p>}
      </div>
    </div>
  )
}
