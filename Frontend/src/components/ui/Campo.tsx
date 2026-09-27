import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'

const CONTROL =
  'w-full rounded-lg border border-line bg-panel px-3 py-2 text-sm text-ink placeholder:text-ink-faint focus:border-accent focus:outline-none disabled:bg-panel-muted disabled:text-ink-soft'

interface CampoProps {
  etiqueta: string
  error?: string
  ayuda?: string
  obligatorio?: boolean
  children: ReactNode
  className?: string
}

/** Etiqueta + control + mensaje de validación del backend junto al campo (RNF05). */
export function Campo({ etiqueta, error, ayuda, obligatorio, children, className = '' }: CampoProps) {
  return (
    <label className={`flex flex-col gap-1 text-sm ${className}`}>
      <span className="font-medium text-ink">
        {etiqueta}
        {obligatorio && <span className="ml-0.5 text-danger">*</span>}
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

export function Selector(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`${CONTROL} ${props.className ?? ''}`} />
}

export function AreaTexto(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea rows={3} {...props} className={`${CONTROL} ${props.className ?? ''}`} />
}
