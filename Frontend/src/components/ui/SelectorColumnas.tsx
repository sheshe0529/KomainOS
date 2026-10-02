import { useEffect, useId, useRef } from 'react'
import { Columns3, RotateCcw } from 'lucide-react'
import type { ColumnasVisibles } from '@/hooks/useColumnasVisibles'

/** La lista es un popover nativo: no la recorta el contenedor de la tabla (DEC-32) */
export function SelectorColumnas({ columnas, ve, alternar, restablecer, cantidadVisibles }: ColumnasVisibles) {
  const idPanel = useId()
  const boton = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = panel.current
    if (!el) return
    // Se alinea al borde derecho del botón antes de mostrarse
    const ubicar = (e: Event) => {
      if ((e as Event & { newState?: string }).newState !== 'open') return
      const r = boton.current?.getBoundingClientRect()
      if (!r) return
      Object.assign(el.style, {
        top: `${r.bottom + 4}px`,
        right: `${Math.max(8, window.innerWidth - r.right)}px`,
        left: 'auto',
        bottom: 'auto',
        maxHeight: `${Math.max(160, window.innerHeight - r.bottom - 16)}px`,
      })
    }
    el.addEventListener('beforetoggle', ubicar)
    return () => el.removeEventListener('beforetoggle', ubicar)
  }, [])

  const opcionales = columnas.filter((c) => !c.fija)

  return (
    <>
      <button
        ref={boton}
        type="button"
        popoverTarget={idPanel}
        aria-haspopup="true"
        className="inline-flex items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink transition hover:bg-panel-muted"
      >
        <Columns3 className="h-4 w-4 text-ink-soft" aria-hidden="true" />
        Columnas
        <span className="rounded-full bg-panel-muted px-1.5 font-mono text-xs tabular-nums text-ink-soft">
          {cantidadVisibles}/{columnas.length}
        </span>
      </button>
      <div
        ref={panel}
        id={idPanel}
        popover="auto"
        className="m-0 w-60 overflow-y-auto rounded-lg border border-line bg-panel p-2 text-sm text-ink shadow-lg"
      >
        <p className="px-2 pb-1.5 pt-1 text-xs font-semibold uppercase tracking-wide text-ink-faint">Mostrar columnas</p>
        <ul className="flex flex-col">
          {opcionales.map((c) => (
            <li key={c.id}>
              <label className="flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 hover:bg-panel-muted">
                <input type="checkbox" checked={ve(c.id)} onChange={() => alternar(c.id)} className="accent-[var(--color-accent)]" />
                {c.etiqueta}
              </label>
            </li>
          ))}
        </ul>
        <div className="mt-1 border-t border-line pt-1">
          <button
            type="button"
            onClick={restablecer}
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs text-ink-soft hover:bg-panel-muted hover:text-ink"
          >
            <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
            Restablecer columnas
          </button>
        </div>
      </div>
    </>
  )
}
