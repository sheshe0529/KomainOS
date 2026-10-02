import { useNavigate } from 'react-router-dom'
import { ArrowDown, ArrowUp, ArrowUpDown, Eye, Pencil } from 'lucide-react'
import type { Pagina } from '@/api/dominio'
import type { ServidorResumenRespuesta } from '@/api/types'
import { BotonIcono } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { Paginacion } from '@/components/ui/Paginacion'
import type { ColumnaServidor, ContextoColumna } from './columnasServidores'

export interface Orden {
  propiedad: string
  asc: boolean
}

interface TablaServidoresProps {
  columnas: ColumnaServidor[]
  contexto: ContextoColumna
  pagina?: Pagina<ServidorResumenRespuesta>
  cargando: boolean
  orden: Orden
  onOrdenar: (propiedad: string) => void
  onPagina: (pagina: number) => void
  esAdmin: boolean
  /** Cambia el mensaje de la tabla vacía */
  conFiltros: boolean
}

export function TablaServidores({ columnas, contexto, pagina, cargando, orden, onOrdenar, onPagina, esAdmin, conFiltros }: TablaServidoresProps) {
  const navigate = useNavigate()
  const totalColumnas = columnas.length + 1

  return (
    <div className="overflow-hidden rounded-xl border border-line bg-panel">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm" style={{ minWidth: `${Math.max(640, totalColumnas * 125)}px` }}>
          <thead>
            <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              {columnas.map((c) => (
                <Encabezado key={c.id} columna={c} orden={orden} onOrdenar={onOrdenar} />
              ))}
              <th className="px-4 py-3 text-right font-medium">Acciones</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {cargando && !pagina ? (
              <tr>
                <td colSpan={totalColumnas}>
                  <Cargando texto="Cargando inventario…" />
                </td>
              </tr>
            ) : (
              pagina?.contenido?.map((s) => (
                <tr key={s.id} className="transition-colors hover:bg-panel-muted">
                  {columnas.map((c) => (
                    <td key={c.id} className="px-4 py-3">
                      {c.celda(s, contexto)}
                    </td>
                  ))}
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <BotonIcono icono={Eye} etiqueta="Ver ficha" onClick={() => navigate(`/servidores/${s.id}`)} />
                      {esAdmin && s.estado !== 'DADO_DE_BAJA' && (
                        <BotonIcono icono={Pencil} etiqueta="Editar o configurar" onClick={() => navigate(`/servidores/${s.id}`)} />
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
            {!cargando && pagina?.contenido?.length === 0 && (
              <tr>
                <td colSpan={totalColumnas} className="px-4 py-10 text-center text-sm text-ink-soft">
                  {conFiltros ? 'No se encontraron servidores con los filtros aplicados.' : 'Aún no hay servidores registrados en el inventario.'}
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

function Encabezado({ columna, orden, onOrdenar }: { columna: ColumnaServidor; orden: Orden; onOrdenar: (propiedad: string) => void }) {
  if (!columna.orden) {
    return <th className="px-4 py-3 font-medium">{columna.etiqueta}</th>
  }
  const activa = orden.propiedad === columna.orden
  const Icono = !activa ? ArrowUpDown : orden.asc ? ArrowUp : ArrowDown
  return (
    <th className="px-4 py-3 font-medium" aria-sort={activa ? (orden.asc ? 'ascending' : 'descending') : undefined}>
      <button
        type="button"
        onClick={() => onOrdenar(columna.orden!)}
        className="inline-flex items-center gap-1 whitespace-nowrap uppercase hover:text-ink"
      >
        {columna.etiqueta}
        <Icono className={`h-3 w-3 ${activa ? 'text-accent' : ''}`} aria-hidden="true" />
      </button>
    </th>
  )
}
