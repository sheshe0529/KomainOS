import { TriangleAlert } from 'lucide-react'
import { textoDeError } from '@/utils/errores'

interface MensajeErrorProps {
  error: unknown
  onReintentar?: () => void
}

export function MensajeError({ error, onReintentar }: MensajeErrorProps) {
  if (!error) return null
  return (
    <div role="alert" className="flex items-start gap-3 rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
      <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <p className="flex-1">{textoDeError(error)}</p>
      {onReintentar && (
        <button type="button" onClick={onReintentar} className="font-medium underline underline-offset-2">
          Reintentar
        </button>
      )}
    </div>
  )
}
