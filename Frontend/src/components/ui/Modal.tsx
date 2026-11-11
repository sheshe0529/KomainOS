import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

interface ModalProps {
  abierto: boolean
  titulo: string
  descripcion?: ReactNode
  onCerrar: () => void
  children: ReactNode
  pie?: ReactNode
  ancho?: 'md' | 'lg' | 'xl' | '2xl'
  posicion?: 'centro' | 'derecha'
}

const ANCHOS = { md: 'max-w-lg', lg: 'max-w-2xl', xl: 'max-w-4xl', '2xl': 'max-w-6xl' }

/** Sobre <dialog> nativo: el navegador resuelve el foco, Escape y la capa de fondo */
export function Modal({ abierto, titulo, descripcion, onCerrar, children, pie, ancho = 'md', posicion = 'centro' }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const tituloId = useId()
  const descripcionId = useId()
  const lateral = posicion === 'derecha'

  useEffect(() => {
    const dialogo = ref.current
    if (!dialogo || !abierto) return
    const disparador = document.activeElement
    dialogo.showModal()
    return () => {
      if (dialogo.open) dialogo.close()
      if (lateral && disparador instanceof HTMLElement && disparador.isConnected) disparador.focus()
    }
  }, [abierto, lateral])

  return (
    <dialog
      ref={ref}
      aria-labelledby={tituloId}
      aria-describedby={descripcion ? descripcionId : undefined}
      onCancel={(evento) => {
        evento.preventDefault()
        onCerrar()
      }}
      className={`border-line bg-panel p-0 text-ink shadow-xl backdrop:bg-black/50 ${
        lateral
          ? 'fixed inset-y-0 right-0 left-auto m-0 h-dvh max-h-none w-full max-w-none border-l md:w-1/2 lg:w-2/5 motion-safe:open:animate-[entrada-lateral_180ms_ease-out]'
          : `m-auto w-[calc(100%-2rem)] ${ANCHOS[ancho]} rounded-xl border`
      }`}
    >
      {abierto && (
        <div className={`flex flex-col ${lateral ? 'h-full' : 'max-h-[85dvh]'}`}>
          <div className="flex shrink-0 items-start justify-between gap-4 border-b border-line px-5 py-4">
            <div className="min-w-0">
              <h3 id={tituloId} className="text-base font-semibold text-ink">{titulo}</h3>
              {descripcion && (typeof descripcion === 'string'
                ? <p id={descripcionId} className="mt-0.5 text-sm text-ink-soft">{descripcion}</p>
                : <div id={descripcionId} className="mt-2">{descripcion}</div>)}
            </div>
            <button
              type="button"
              onClick={onCerrar}
              aria-label="Cerrar"
              className="shrink-0 rounded-lg p-1 text-ink-soft hover:bg-panel-muted hover:text-ink"
            >
              <X className="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
          <div className={`min-h-0 overflow-y-auto py-4 ${lateral ? 'flex-1 overscroll-contain px-3 sm:px-4' : 'px-5'}`}>{children}</div>
          {pie && <div className="flex shrink-0 flex-wrap justify-end gap-2 border-t border-line px-5 py-3">{pie}</div>}
        </div>
      )}
    </dialog>
  )
}
