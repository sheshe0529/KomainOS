import { useState } from 'react'
import type { ReactNode } from 'react'
import { Plus } from 'lucide-react'
import type { ExcepcionUmbralRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { StatusPill } from '@/components/ui/StatusPill'
import { BotonesExcepcion } from './acciones'
import type { AccionesExcepcion } from './acciones'
import { SeccionAlcance } from './SeccionAlcance'

export interface ElementoCatalogo {
  id?: number
  nombre?: string
  /** Un elemento inactivo solo aparece si todavía tiene valores propios */
  activo: boolean
}

interface TablaPorCatalogoProps {
  prioridad: number
  titulo: string
  alcance: 'GRUPO' | 'ENTORNO' | 'CRITICIDAD'
  encabezado: string
  /** Las excepciones del umbral: aquí se toman las del alcance indicado */
  excepciones: ExcepcionUmbralRespuesta[]
  /** Encabezados de las columnas de valores y las celdas de cada fila (sin excepción, los valores globales atenuados) */
  columnas: string[]
  celdas: (propia: ExcepcionUmbralRespuesta | undefined) => ReactNode
  catalogo: ElementoCatalogo[]
  esAdmin: boolean
  acciones: AccionesExcepcion
  /** El catálogo todavía no llegó: no se puede decir qué falta definir */
  cargando?: boolean
  nota?: string
}

/**
 * Una fila por cada grupo, entorno o criticidad. Los que no tienen valores propios muestran, atenuados, los globales
 * que usan: así se ve qué rige sin necesidad de explicarlo con texto
 */
export function TablaPorCatalogo({
  prioridad,
  titulo,
  alcance,
  encabezado,
  excepciones,
  columnas,
  celdas,
  catalogo,
  esAdmin,
  acciones,
  cargando,
  nota,
}: TablaPorCatalogoProps) {
  const [abierta, setAbierta] = useState(false)
  const propias = new Map(excepciones.filter((e) => e.alcance === alcance).map((e) => [e.objetivo?.id, e]))
  const filas = catalogo.filter((c) => c.activo || propias.has(c.id))
  const definidas = filas.filter((c) => propias.has(c.id)).length

  return (
    <SeccionAlcance
      prioridad={prioridad}
      titulo={titulo}
      resumen={cargando ? undefined : `${definidas}/${filas.length}`}
      resumenTitulo={`${definidas} de ${filas.length} con valores propios`}
      abierta={abierta}
      onAlternar={() => setAbierta((a) => !a)}
    >
      {nota && <p className="border-b border-line px-5 py-2.5 text-xs text-ink-faint">{nota}</p>}
      {cargando ? (
        <Cargando />
      ) : filas.length === 0 ? (
        <p className="px-5 py-4 text-sm text-ink-faint">Sin elementos</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-5 py-2.5 font-medium">{encabezado}</th>
                {columnas.map((c) => (
                  <th key={c} className="px-3 py-2.5 font-medium">
                    {c}
                  </th>
                ))}
                <th className="px-3 py-2.5 font-medium">Estado</th>
                {esAdmin && (
                  <th className="px-5 py-2.5">
                    <span className="sr-only">Acciones</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {filas.map((c) => {
                const propia = propias.get(c.id)
                return (
                  <tr key={c.id} className="hover:bg-panel-muted">
                    <td className="px-5 py-2.5 font-medium text-ink">
                      {c.nombre}
                      {!c.activo && <span className="ml-2 text-xs font-normal text-ink-faint">(inactivo)</span>}
                      {propia?.motivo && <div className="text-xs font-normal text-ink-faint">{propia.motivo}</div>}
                    </td>
                    {celdas(propia)}
                    <td className="px-3 py-2.5">
                      {propia ? (
                        <StatusPill tone={propia.activo ? 'success' : 'neutral'} label={propia.activo ? 'Activa' : 'Inactiva'} />
                      ) : (
                        <span className="text-ink-faint">—</span>
                      )}
                    </td>
                    {esAdmin && (
                      <td className="px-5 py-2.5">
                        {propia ? (
                          <BotonesExcepcion excepcion={propia} acciones={acciones} />
                        ) : (
                          <div className="flex justify-end">
                            <Boton icono={Plus} onClick={() => acciones.definir({ alcance, id: c.id!, nombre: c.nombre ?? '' })} disabled={!c.activo}>
                              Definir
                            </Boton>
                          </div>
                        )}
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </SeccionAlcance>
  )
}
