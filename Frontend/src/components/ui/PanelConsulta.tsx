import { useLayoutEffect, useRef, useState } from 'react'
import type { ReactNode, RefObject } from 'react'
import { ArrowLeft } from 'lucide-react'

/**
 * Navegación de un panel lateral de consulta, como el de la actividad de una orden: una lista y, al pulsar una fila, su
 * detalle en el mismo panel con un botón para volver. Al abrir el detalle el foco pasa a «Volver»; al volver, regresa a
 * la fila de la que se partió
 */
export function useDetallePanel<T>() {
  const [seleccion, setSeleccion] = useState<T>()
  const origen = useRef<HTMLElement | null>(null)
  const volver = useRef<HTMLButtonElement>(null)
  const abierto = seleccion !== undefined

  useLayoutEffect(() => {
    if (abierto) {
      volver.current?.focus()
    } else {
      origen.current?.focus()
      origen.current = null
    }
  }, [abierto])

  return {
    seleccion,
    abrir(valor: T) {
      origen.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
      setSeleccion(valor)
    },
    cerrar: () => setSeleccion(undefined),
    refVolver: volver,
  }
}

/** El detalle de una fila: «Volver» arriba y debajo el contenido */
export function VistaDetalle({ onVolver, refVolver, children }: {
  onVolver: () => void
  refVolver: RefObject<HTMLButtonElement | null>
  children: ReactNode
}) {
  return (
    <div className="flex flex-col gap-4">
      <button
        ref={refVolver}
        type="button"
        onClick={onVolver}
        className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-lg px-2 text-sm font-medium text-ink-soft hover:bg-panel-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-ink"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Volver
      </button>
      {children}
    </div>
  )
}
