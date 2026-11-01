import type { MouseEvent, ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'

interface FilaNavegableProps {
  a: string
  children: ReactNode
  className?: string
}

/**
 * Fila que abre su detalle al hacer clic en cualquier parte, como en la sala de operaciones. Los enlaces y botones
 * de la fila conservan su propia acción
 */
export function FilaNavegable({ a, children, className = '' }: FilaNavegableProps) {
  const navigate = useNavigate()

  function abrir(evento: MouseEvent<HTMLTableRowElement>) {
    if ((evento.target as HTMLElement).closest('a, button, input, select, textarea, label')) return
    // Quien selecciona texto para copiarlo no quiere navegar
    if (window.getSelection()?.toString()) return
    navigate(a)
  }

  return (
    <tr onClick={abrir} className={`cursor-pointer transition-colors hover:bg-panel-muted ${className}`}>
      {children}
    </tr>
  )
}
