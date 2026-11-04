import type { MouseEvent, ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'

export interface Volver {
  /** Destino si no hay una pantalla anterior a la que regresar */
  a: string
  texto: string
  /** Regresa a la pantalla anterior (una orden se abre desde la sala, el cronograma o una ficha) */
  atras?: boolean
  /** Se ejecuta antes de salir; si llama a preventDefault, no se sale (por ejemplo, para confirmar cambios sin guardar) */
  alSalir?: (evento: MouseEvent<HTMLAnchorElement>) => void
}

/** La misma flecha sobre el título en todas las pantallas de detalle */
export function EnlaceVolver({ a, texto, atras, alSalir }: Volver) {
  const navigate = useNavigate()
  // BrowserRouter guarda la posición en el historial: idx 0 es la primera pantalla de la sesión
  const posicion = (window.history.state as { idx?: number } | null)?.idx ?? 0
  const regresa = !!atras && posicion > 0

  function alHacerClic(evento: MouseEvent<HTMLAnchorElement>) {
    alSalir?.(evento)
    if (evento.defaultPrevented) return
    if (regresa) {
      evento.preventDefault()
      navigate(-1)
    }
  }

  return (
    <Link to={a} onClick={alHacerClic} className="inline-flex items-center gap-1 self-start text-sm text-ink-soft hover:text-ink">
      {/* Regresa a donde se abrió (una orden, la bitácora, el inventario): el texto no promete un destino fijo */}
      <ArrowLeft className="h-4 w-4" aria-hidden="true" /> {regresa ? 'Volver' : texto}
    </Link>
  )
}

interface PageHeaderProps {
  title: ReactNode
  description?: ReactNode
  /** Etiquetas de estado bajo el título */
  meta?: ReactNode
  actions?: ReactNode
  volver?: Volver
  /** Hostnames y códigos van en monoespaciada */
  mono?: boolean
  className?: string
}

/** Las acciones quedan siempre a la derecha, también cuando no caben junto al título y bajan de línea */
export function PageHeader({ title, description, meta, actions, volver, mono, className = 'mb-6' }: PageHeaderProps) {
  return (
    <div className={`flex flex-col gap-4 ${className}`}>
      {volver && <EnlaceVolver {...volver} />}
      <div className="flex flex-wrap items-start gap-4">
        <div className="min-w-0 flex-1 basis-80">
          <h2 className={`text-xl font-semibold text-ink sm:text-2xl ${mono ? 'font-mono' : ''}`}>{title}</h2>
          {description && <div className="mt-1 text-sm text-ink-soft">{description}</div>}
          {meta && <div className="mt-3 flex flex-wrap gap-2">{meta}</div>}
        </div>
        {actions && <div className="ml-auto flex flex-wrap items-center justify-end gap-2">{actions}</div>}
      </div>
    </div>
  )
}
