import type { MouseEvent, ReactNode } from 'react'

interface FilaDetalleProps {
  onAbrir: () => void
  children: ReactNode
  className?: string
}

/**
 * Fila que abre su detalle en un recuadro al hacer clic en cualquier parte. Para el teclado, una de sus celdas lleva el
 * botón que hace lo mismo; los enlaces y botones de la fila conservan su propia acción
 */
export function FilaDetalle({ onAbrir, children, className = '' }: FilaDetalleProps) {
  function abrir(evento: MouseEvent<HTMLTableRowElement>) {
    if ((evento.target as HTMLElement).closest('a, button, input, select, textarea, label')) return
    // Quien selecciona texto para copiarlo no quiere abrir el detalle
    if (window.getSelection()?.toString()) return
    onAbrir()
  }

  return (
    <tr onClick={abrir} className={`cursor-pointer transition-colors hover:bg-panel-muted ${className}`}>
      {children}
    </tr>
  )
}

/** El botón de la fila que abre su detalle: se ve como texto de la celda */
export function BotonDetalle({ onAbrir, children, etiqueta }: { onAbrir: () => void; children: ReactNode; etiqueta?: string }) {
  return (
    <button
      type="button"
      onClick={onAbrir}
      aria-haspopup="dialog"
      aria-label={etiqueta}
      className="rounded text-left font-medium text-ink hover:text-accent focus-visible:outline-2 focus-visible:outline-accent"
    >
      {children}
    </button>
  )
}
