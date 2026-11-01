import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react'
import { paginasVisibles, TAMANOS_PAGINA } from '@/hooks/usePaginacion'

interface PaginacionProps {
  pagina: number
  totalPaginas: number
  totalElementos: number
  onCambiar: (pagina: number) => void
  /** Filas por página; sin onTamano no se ofrece cambiarlo */
  tamano?: number
  onTamano?: (tamano: number) => void
}

const BOTON = 'rounded-lg p-1.5 hover:bg-panel-muted disabled:opacity-40 disabled:hover:bg-transparent'

export function Paginacion({ pagina, totalPaginas, totalElementos, onCambiar, tamano, onTamano }: PaginacionProps) {
  if (totalElementos === 0) return null
  const paginas = Math.max(totalPaginas, 1)
  const desde = tamano ? pagina * tamano + 1 : undefined
  const hasta = tamano ? Math.min((pagina + 1) * tamano, totalElementos) : undefined

  return (
    <nav
      aria-label="Paginación"
      className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-t border-line px-4 py-3 text-sm text-ink-soft"
    >
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <span className="tabular-nums">
          {desde !== undefined ? `${desde}–${hasta} de ${totalElementos}` : totalElementos}{' '}
          {totalElementos === 1 ? 'registro' : 'registros'}
        </span>
        {onTamano && tamano !== undefined && (
          <label className="flex items-center gap-2">
            Filas por página
            <select
              value={tamano}
              onChange={(e) => onTamano(Number(e.target.value))}
              className="rounded-lg border border-line bg-panel px-2 py-1 text-sm text-ink focus:border-accent focus:outline-none"
            >
              {TAMANOS_PAGINA.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {paginas > 1 && (
        <div className="flex items-center gap-1">
          <button type="button" onClick={() => onCambiar(0)} disabled={pagina <= 0} aria-label="Primera página" className={BOTON}>
            <ChevronsLeft className="h-4 w-4" aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => onCambiar(pagina - 1)}
            disabled={pagina <= 0}
            aria-label="Página anterior"
            className={BOTON}
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </button>
          {paginasVisibles(pagina, paginas).map((p, i) =>
            p === '…' ? (
              <span key={`salto-${i}`} className="px-1 text-ink-faint" aria-hidden="true">
                …
              </span>
            ) : (
              <button
                key={p}
                type="button"
                onClick={() => onCambiar(p)}
                aria-label={`Página ${p + 1}`}
                aria-current={p === pagina ? 'page' : undefined}
                className={`min-w-8 rounded-lg px-2 py-1 tabular-nums transition-colors ${
                  p === pagina ? 'bg-accent font-medium text-accent-ink' : 'text-ink hover:bg-panel-muted'
                }`}
              >
                {p + 1}
              </button>
            ),
          )}
          <button
            type="button"
            onClick={() => onCambiar(pagina + 1)}
            disabled={pagina + 1 >= paginas}
            aria-label="Página siguiente"
            className={BOTON}
          >
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => onCambiar(paginas - 1)}
            disabled={pagina + 1 >= paginas}
            aria-label="Última página"
            className={BOTON}
          >
            <ChevronsRight className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      )}
    </nav>
  )
}
