import { Link } from 'react-router-dom'
import type { ResultadoImportacionRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { RESULTADO_IMPORTACION } from '@/utils/etiquetas'
import { Contador } from './Contador'

/** HU08 CA3: cada fila con lo que se hizo, y el motivo si se rechazó u omitió */
export function ResultadoImportacionVista({ resultado }: { resultado: ResultadoImportacionRespuesta }) {
  return (
    <>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Contador etiqueta="Creados" valor={resultado.creados ?? 0} tono="success" />
        <Contador etiqueta="Sobrescritos" valor={resultado.actualizados ?? 0} tono="success" />
        <Contador etiqueta="Omitidos" valor={resultado.omitidos ?? 0} />
        <Contador etiqueta="Rechazados" valor={resultado.rechazados ?? 0} tono="danger" />
      </div>
      <div className="max-h-[45dvh] overflow-auto rounded-lg border border-line">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead className="sticky top-0 bg-panel">
            <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
              <th className="px-3 py-2 font-medium">Fila</th>
              <th className="px-3 py-2 font-medium">Servidor</th>
              <th className="px-3 py-2 font-medium">Resultado</th>
              <th className="px-3 py-2 font-medium">Detalle</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {resultado.filas?.map((f) => {
              const r = f.resultado ? RESULTADO_IMPORTACION[f.resultado] : undefined
              const aplicado = f.resultado === 'CREADO' || f.resultado === 'ACTUALIZADO'
              return (
                <tr key={f.fila}>
                  <td className="px-3 py-2 font-mono tabular-nums text-ink-soft">{f.fila}</td>
                  <td className="px-3 py-2 font-mono">
                    {aplicado && f.idServidor ? (
                      <Link to={`/servidores/${f.idServidor}`} className="text-ink hover:text-accent">
                        {f.hostname}
                      </Link>
                    ) : (
                      <span className="text-ink">{f.hostname ?? '—'}</span>
                    )}
                  </td>
                  <td className="px-3 py-2">{r && <StatusPill tone={r.tono} label={r.etiqueta} />}</td>
                  <td className="px-3 py-2 text-xs text-ink-soft">{f.detalle}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </>
  )
}
