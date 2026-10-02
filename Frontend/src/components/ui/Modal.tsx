import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

interface ModalProps {
  abierto: boolean
  titulo: string
  descripcion?: string
  onCerrar: () => void
  children: ReactNode
  pie?: ReactNode
  ancho?: 'md' | 'lg' | 'xl'
}

const ANCHOS = { md: 'max-w-lg', lg: 'max-w-2xl', xl: 'max-w-4xl' }

/** Sobre <dialog> nativo: el navegador resuelve el foco, Escape y la capa de fondo */
export function Modal({ abierto, titulo, descripcion, onCerrar, children, pie, ancho = 'md' }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialogo = ref.current
    if (!dialogo) return
    if (abierto && !dialogo.open) dialogo.showModal()
    if (!abierto && dialogo.open) dialogo.close()
  }, [abierto])

  return (
    <dialog
      ref={ref}
      onCancel={(evento) => {
        evento.preventDefault()
        onCerrar()
      }}
      className={`m-auto w-[calc(100%-2rem)] ${ANCHOS[ancho]} rounded-xl border border-line bg-panel p-0 text-ink shadow-xl backdrop:bg-black/50`}
    >
      {abierto && (
        <div className="flex max-h-[85dvh] flex-col">
          <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
            <div>
              <h3 className="text-base font-semibold text-ink">{titulo}</h3>
              {descripcion && <p className="mt-0.5 text-sm text-ink-soft">{descripcion}</p>}
            </div>
            <button
              type="button"
              onClick={onCerrar}
              aria-label="Cerrar"
              className="rounded-lg p-1 text-ink-soft hover:bg-panel-muted hover:text-ink"
            >
              <X className="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
          <div className="overflow-y-auto px-5 py-4">{children}</div>
          {pie && <div className="flex justify-end gap-2 border-t border-line px-5 py-3">{pie}</div>}
        </div>
      )}
    </dialog>
  )
}
