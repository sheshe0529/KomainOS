import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { Info as IconoInfo } from 'lucide-react'
import { usePopoverAnclado } from './popoverAnclado'

interface InfoProps {
  /** La explicación: se muestra al pasar el cursor o al presionar el ícono */
  children: ReactNode
  /** Nombre accesible del botón */
  etiqueta?: string
}

/**
 * Texto de ayuda detrás de un ⓘ: la pantalla queda limpia y la explicación sigue a un gesto. Se abre al pasar el
 * cursor y se fija al presionarlo; se cierra al salir (si no se fijó), al presionar fuera o con Escape
 */
export function Info({ children, etiqueta = 'Más información' }: InfoProps) {
  const id = useId()
  const disparador = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)
  const fijo = useRef(false)
  const abierto = usePopoverAnclado(disparador, panel, { altoMaximo: 360 })

  useEffect(() => {
    if (!abierto) fijo.current = false
  }, [abierto])

  function mostrar() {
    if (!panel.current?.matches(':popover-open')) panel.current?.showPopover()
  }

  function ocultar() {
    if (!fijo.current && panel.current?.matches(':popover-open')) panel.current.hidePopover()
  }

  function alternar() {
    if (fijo.current) {
      fijo.current = false
      panel.current?.hidePopover()
      return
    }
    fijo.current = true
    mostrar()
  }

  return (
    <>
      <button
        ref={disparador}
        type="button"
        aria-label={etiqueta}
        aria-expanded={abierto}
        aria-controls={id}
        onClick={alternar}
        onMouseEnter={mostrar}
        onMouseLeave={ocultar}
        className="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full align-middle text-ink-faint transition-colors hover:text-accent focus-visible:outline-2 focus-visible:outline-accent"
      >
        <IconoInfo className="h-4 w-4" aria-hidden="true" />
      </button>
      <div
        ref={panel}
        id={id}
        popover="auto"
        role="tooltip"
        className="m-0 w-80 max-w-[calc(100vw-16px)] overflow-auto rounded-lg border border-line bg-panel p-3 text-left text-sm font-normal leading-relaxed text-ink-soft shadow-lg"
      >
        {children}
      </div>
    </>
  )
}

/** Título de sección con su ⓘ */
export function TituloConInfo({ titulo, children, nivel = 'h5' }: { titulo: string; children: ReactNode; nivel?: 'h4' | 'h5' }) {
  const Etiqueta = nivel
  return (
    <div className="flex items-center gap-1.5">
      <Etiqueta className="text-sm font-semibold text-ink">{titulo}</Etiqueta>
      <Info etiqueta={`Qué es: ${titulo}`}>{children}</Info>
    </div>
  )
}
