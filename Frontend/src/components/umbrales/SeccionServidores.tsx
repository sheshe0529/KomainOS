import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { ExcepcionUmbralRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { BotonesExcepcion } from './acciones'
import type { AccionesExcepcion } from './acciones'
import { BuscadorServidor } from './BuscadorServidor'
import { SeccionAlcance } from './SeccionAlcance'

interface SeccionServidoresProps {
  prioridad: number
  /** Las excepciones del umbral: aquí se toman las de servidor */
  excepciones: ExcepcionUmbralRespuesta[]
  /** Encabezados de las columnas de valores y las celdas de cada fila; los pone quien conoce la modalidad del umbral */
  columnas: string[]
  celdas: (excepcion: ExcepcionUmbralRespuesta) => ReactNode
  esAdmin: boolean
  acciones: AccionesExcepcion
}

/**
 * Los servidores con valores propios. Plegada de entrada: el inventario puede tener cientos de servidores y aquí solo
 * interesan los que tienen los suyos; para sumar otro se busca por nombre en vez de recorrer una lista
 */
export function SeccionServidores({ prioridad, excepciones, columnas, celdas, esAdmin, acciones }: SeccionServidoresProps) {
  const [abierta, setAbierta] = useState(false)
  const propias = excepciones.filter((e) => e.alcance === 'SERVIDOR')
  const yaDefinidos = new Set(propias.map((e) => e.objetivo?.id).filter((id): id is number => id !== undefined))

  return (
    <SeccionAlcance
      prioridad={prioridad}
      titulo="Por servidor"
      resumen={String(propias.length)}
      resumenTitulo={`${propias.length} ${propias.length === 1 ? 'servidor' : 'servidores'} con valores propios`}
      abierta={abierta}
      onAlternar={() => setAbierta((a) => !a)}
    >
      {esAdmin && (
        <div className="border-b border-line px-5 py-4">
          <BuscadorServidor
            yaDefinidos={yaDefinidos}
            onElegir={(servidor) => acciones.definir({ alcance: 'SERVIDOR', id: servidor.id, nombre: servidor.nombre })}
          />
        </div>
      )}
      {propias.length === 0 ? (
        <p className="px-5 py-4 text-sm text-ink-faint">Sin servidores</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-5 py-2.5 font-medium">Servidor</th>
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
              {propias.map((e) => (
                <tr key={e.id} className="hover:bg-panel-muted">
                  <td className="px-5 py-2.5 font-medium">
                    <Link to={`/servidores/${e.objetivo?.id}`} className="font-mono text-accent hover:underline">
                      {e.objetivo?.nombre}
                    </Link>
                    {e.motivo && <div className="text-xs font-normal text-ink-faint">{e.motivo}</div>}
                  </td>
                  {celdas(e)}
                  <td className="px-3 py-2.5">
                    <StatusPill tone={e.activo ? 'success' : 'neutral'} label={e.activo ? 'Activa' : 'Inactiva'} />
                  </td>
                  {esAdmin && (
                    <td className="px-5 py-2.5">
                      <BotonesExcepcion excepcion={e} acciones={acciones} />
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </SeccionAlcance>
  )
}
