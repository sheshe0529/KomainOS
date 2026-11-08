import type { InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react'
import { Info } from './Info'

const CONTROL =
  'w-full rounded-lg border border-line bg-panel px-3 py-2 text-sm text-ink placeholder:text-ink-faint focus:border-accent focus:outline-none disabled:bg-panel-muted disabled:text-ink-soft'

interface CampoProps {
  etiqueta: string
  error?: string
  ayuda?: string
  /** Explicación detrás de un ⓘ junto a la etiqueta: lo que no hace falta leer para completar el campo */
  info?: ReactNode
  obligatorio?: boolean
  children: ReactNode
  className?: string
}

export function Campo({ etiqueta, error, ayuda, info, obligatorio, children, className = '' }: CampoProps) {
  return (
    <label className={`flex flex-col gap-1 text-sm ${className}`}>
      <span className="flex items-center gap-1 font-medium text-ink">
        <span>
          {etiqueta}
          {obligatorio && <span className="ml-0.5 text-danger">*</span>}
        </span>
        {info && (
          // Dentro de la etiqueta, un clic en la explicación no debe enfocar el control
          <span className="inline-flex" onClick={(e) => e.preventDefault()}>
            <Info etiqueta={`Sobre: ${etiqueta}`}>{info}</Info>
          </span>
        )}
      </span>
      {children}
      {error ? (
        <span className="text-xs text-danger">{error}</span>
      ) : (
        ayuda && <span className="text-xs text-ink-faint">{ayuda}</span>
      )}
    </label>
  )
}

export function Entrada(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${CONTROL} ${props.className ?? ''}`} />
}

export { Selector } from './Selector'

export function AreaTexto(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea rows={3} {...props} className={`${CONTROL} ${props.className ?? ''}`} />
}
