import { Link } from 'react-router-dom'
import type { OrdenResumenRespuesta } from '@/api/types'
import { ENLACE_FILA } from '@/components/ui/estilos'
import { FilaNavegable } from '@/components/ui/FilaNavegable'
import { ETIQUETA_ESTADO_ORDEN, ETIQUETA_ORIGEN } from '@/utils/etiquetas'
import { formatearFechaHora, formatearHora } from '@/utils/formato'

interface OrdenesTablaProps {
  ordenes: OrdenResumenRespuesta[]
  /** Oculta la columna del objetivo (por ejemplo, en la ficha de un servidor) */
  sinObjetivo?: boolean
  vacio?: string
}

/** Misma presentación que la tabla de la sala de operaciones: la fila completa abre la orden y el estado es texto */
export function OrdenesTabla({ ordenes, sinObjetivo, vacio = 'No hay órdenes para mostrar.' }: OrdenesTablaProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
            <th className="px-5 py-3 font-medium">Orden</th>
            {!sinObjetivo && <th className="px-3 py-3 font-medium">Servidor / grupo</th>}
            <th className="px-3 py-3 font-medium">Estado</th>
            <th className="px-3 py-3 font-medium">Origen</th>
            <th className="px-3 py-3 font-medium">Programada</th>
            <th className="px-3 py-3 font-medium">Criticidad</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {ordenes.map((o) => (
            <FilaNavegable key={o.id} a={`/ordenes/${o.id}`}>
              <td className="px-5 py-3">
                <Link to={`/ordenes/${o.id}`} className={`font-mono ${ENLACE_FILA}`}>
                  {o.codigo}
                </Link>
                {o.prioridad === 'ALTA' && (
                  <span className="ml-2 rounded bg-danger-soft px-1.5 py-0.5 text-xs font-medium text-danger">Alta</span>
                )}
              </td>
              {!sinObjetivo && (
                <td className="px-3 py-3">
                  <span className={o.tipoObjetivo === 'GRUPAL' ? 'text-ink' : 'font-mono text-ink'}>{o.objetivo?.nombre}</span>
                  {o.tipoObjetivo === 'GRUPAL' && (
                    <span className="block text-xs text-ink-faint">Grupo · {o.cantidadServidores} servidores</span>
                  )}
                </td>
              )}
              <td className="px-3 py-3 text-ink-soft">{o.estado ? ETIQUETA_ESTADO_ORDEN[o.estado] : '—'}</td>
              <td className="px-3 py-3 text-ink-soft">{o.origen ? ETIQUETA_ORIGEN[o.origen] : '—'}</td>
              <td className="px-3 py-3 whitespace-nowrap text-ink-soft">
                {formatearFechaHora(o.inicioProgramado)}
                {o.finProgramado && <span className="text-ink-faint"> – {formatearHora(o.finProgramado)}</span>}
              </td>
              <td className="px-3 py-3 text-ink-soft">{o.criticidad?.nombre}</td>
            </FilaNavegable>
          ))}
          {ordenes.length === 0 && (
            <tr>
              <td colSpan={sinObjetivo ? 5 : 6} className="px-4 py-8 text-center text-sm text-ink-soft">
                {vacio}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
