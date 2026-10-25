import type { ReactNode } from 'react'
import { Paginacion } from '@/components/ui/Paginacion'
import type { Pagina } from '@/api/dominio'
export function TablaAutomatizacion<T>({
  cabeceras,
  pagina,
  onPagina,
  fila,
}: {
  cabeceras: string[]
  pagina?: Pagina<T>
  onPagina: (p: number) => void
  fila: (item: T, i: number) => ReactNode
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-panel">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[680px] text-left text-sm">
          <thead className="bg-panel-muted text-xs text-ink-soft">
            <tr>
              {cabeceras.map((t) => (
                <th key={t} className="px-4 py-3 font-medium">
                  {t}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {pagina?.contenido?.map(fila)}
            {pagina?.contenido?.length === 0 && (
              <tr>
                <td className="p-8 text-center text-ink-soft" colSpan={cabeceras.length}>
                  Sin registros.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Paginacion
        pagina={pagina?.pagina ?? 0}
        totalPaginas={pagina?.totalPaginas ?? 0}
        totalElementos={pagina?.totalElementos ?? 0}
        onCambiar={onPagina}
      />
    </div>
  )
}
export const celda = 'px-4 py-3 align-top'
