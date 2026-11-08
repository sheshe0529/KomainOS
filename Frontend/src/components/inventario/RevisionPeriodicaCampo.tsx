import { useId } from 'react'
import type { ReactNode } from 'react'
import { Info } from '@/components/ui/Info'

interface RevisionPeriodicaCampoProps {
  habilitada: boolean
  onCambiar: (habilitada: boolean) => void
  /** La frecuencia de revisión: solo se muestra con la revisión activa, así no se puede cambiar estando inactiva */
  children: ReactNode
  grupo?: boolean
  error?: string
}

/** Si el servidor o el grupo se revisa solo (RF37) y, en ese caso, cada cuántos días */
export function RevisionPeriodicaCampo({ habilitada, onCambiar, children, grupo, error }: RevisionPeriodicaCampoProps) {
  const idError = useId()
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 flex items-center gap-1.5 text-sm font-medium text-ink">
        Revisión periódica
        <Info etiqueta="Qué es la revisión periódica">
          {grupo
            ? 'El sistema analiza a la vez a todos los integrantes del grupo; si encuentra una condición crítica, genera una orden grupal. Las revisiones individuales se configuran en la ficha de cada servidor. Inactiva, el grupo no se revisa automáticamente.'
            : 'El sistema analiza el servidor cada cierta cantidad de días; si encuentra una condición crítica, genera una orden de mantenimiento. Inactiva, no se programan revisiones automáticas, pero «Revisar ahora» sigue disponible.'}{' '}
          La frecuencia se conserva para cuando vuelva a activarla.
        </Info>
      </legend>
      <button
        type="button"
        role="switch"
        aria-label="Revisión periódica"
        aria-checked={habilitada}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? idError : undefined}
        onClick={() => onCambiar(!habilitada)}
        className="group inline-flex min-h-11 w-fit cursor-pointer items-center gap-2 rounded-lg text-sm font-medium text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
      >
        <span
          aria-hidden="true"
          className="inline-flex h-6 w-11 shrink-0 items-center rounded-full bg-ink-soft p-0.5 group-aria-checked:bg-accent motion-safe:transition-colors"
        >
          <span className="size-5 rounded-full bg-panel group-aria-checked:translate-x-5 group-aria-checked:bg-accent-ink motion-safe:transition-transform" />
        </span>
        <span aria-hidden="true">{habilitada ? 'Activa' : 'Inactiva'}</span>
      </button>
      {error && <p id={idError} className="text-xs text-danger">{error}</p>}
      {habilitada && children}
    </fieldset>
  )
}
