import { Link } from 'react-router-dom'
import type { OrdenResumenRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { ESTADO_ORDEN, ETIQUETA_ORIGEN } from '@/utils/etiquetas'
import { formatearFechaHora, formatearHora } from '@/utils/formato'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'

interface OrdenesTablaProps {
  ordenes: OrdenResumenRespuesta[]
  /** Oculta la columna del objetivo (por ejemplo, en la ficha de un servidor). */
  sinObjetivo?: boolean
  vacio?: string
}

/** Tabla de órdenes reutilizada en la consulta (RF36) y en las fichas (HU10 CA4). */
export function OrdenesTabla({ ordenes, sinObjetivo, vacio = 'No hay órdenes para mostrar.' }: OrdenesTablaProps) {
  const tonoCriticidad = useTonoCriticidad()
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
            <th className="px-4 py-3 font-medium">Orden</th>
            {!sinObjetivo && <th className="px-4 py-3 font-medium">Servidor / grupo</th>}
            <th className="px-4 py-3 font-medium">Programada</th>
            <th className="px-4 py-3 font-medium">Criticidad</th>
            <th className="px-4 py-3 font-medium">Origen</th>
            <th className="px-4 py-3 font-medium">Estado</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {ordenes.map((o) => {
            const estado = o.estado ? ESTADO_ORDEN[o.estado] : undefined
            return (
              <tr key={o.id} className="transition-colors hover:bg-panel-muted">
                <td className="px-4 py-3">
                  <Link to={`/ordenes/${o.id}`} className="font-mono font-medium text-ink hover:text-accent">
                    {o.codigo}
                  </Link>
                </td>
                {!sinObjetivo && (
                  <td className="px-4 py-3">
                    <span className={o.tipoObjetivo === 'GRUPAL' ? 'text-ink' : 'font-mono text-ink'}>{o.objetivo?.nombre}</span>
                    {o.tipoObjetivo === 'GRUPAL' && (
                      <span className="block text-xs text-ink-faint">Grupo · {o.cantidadServidores} servidores</span>
                    )}
                  </td>
                )}
                <td className="px-4 py-3 text-ink-soft">
                  {formatearFechaHora(o.inicioProgramado)}
                  {o.finProgramado && <span className="text-ink-faint"> – {formatearHora(o.finProgramado)}</span>}
                </td>
                <td className="px-4 py-3">
                  <StatusPill tone={tonoCriticidad(o.criticidad?.prioridad)} label={o.criticidad?.nombre ?? ''} />
                </td>
                <td className="px-4 py-3 text-ink-soft">{o.origen ? ETIQUETA_ORIGEN[o.origen] : ''}</td>
                <td className="px-4 py-3">{estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}</td>
              </tr>
            )
          })}
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
