import { ChevronLeft, ChevronRight } from 'lucide-react'

interface PaginacionProps {
  pagina: number
  totalPaginas: number
  totalElementos: number
  onCambiar: (pagina: number) => void
}

/** Paginación de listados; el backend pagina siempre (RF11). */
export function Paginacion({ pagina, totalPaginas, totalElementos, onCambiar }: PaginacionProps) {
  if (totalElementos === 0) return null
  return (
    <div className="flex items-center justify-between gap-3 border-t border-line px-4 py-3 text-sm text-ink-soft">
      <span>
        {totalElementos} {totalElementos === 1 ? 'registro' : 'registros'}
      </span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onCambiar(pagina - 1)}
          disabled={pagina <= 0}
          aria-label="Página anterior"
          className="rounded-lg p-1.5 hover:bg-panel-muted disabled:opacity-40"
        >
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
        </button>
        <span className="tabular-nums">
          Página {pagina + 1} de {Math.max(totalPaginas, 1)}
        </span>
        <button
          type="button"
          onClick={() => onCambiar(pagina + 1)}
          disabled={pagina + 1 >= totalPaginas}
          aria-label="Página siguiente"
          className="rounded-lg p-1.5 hover:bg-panel-muted disabled:opacity-40"
        >
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </div>
  )
}
