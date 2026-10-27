import type { MedicionRespuesta, UmbralEvaluadoRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { NIVEL, valorMedicion } from '@/utils/automatizacion'
import { formatearLimite } from '@/utils/umbrales'

interface MedicionesAnalisisProps {
  mediciones: MedicionRespuesta[]
}

/** Límites con los que se evaluó: los vigentes en ese momento, aunque el umbral cambie después (DEC-41) */
function Limites({ umbral, unidad }: { umbral: UmbralEvaluadoRespuesta; unidad?: string }) {
  if (umbral.idLista) {
    return <span>Comparada con una lista de referencia</span>
  }
  // El backend envía el símbolo con el que se evaluó (≥, <, ≠…), no el nombre del operador
  const operador = umbral.operador ?? ''
  return (
    <span className="font-mono">
      {umbral.advertencia !== undefined && umbral.advertencia !== null && (
        <span className="text-warning">
          {operador} {formatearLimite(umbral.advertencia, unidad)}
        </span>
      )}
      {umbral.advertencia != null && umbral.critico != null && ' · '}
      {umbral.critico !== undefined && umbral.critico !== null && (
        <span className="text-danger">
          {operador} {formatearLimite(umbral.critico, unidad)}
        </span>
      )}
      {umbral.idExcepcion && <span className="ml-1 font-sans text-xs text-ink-faint">(excepción)</span>}
    </span>
  )
}

export function MedicionesAnalisis({ mediciones }: MedicionesAnalisisProps) {
  if (mediciones.length === 0) return <p className="text-sm text-ink-soft">Sin mediciones registradas.</p>

  // Las críticas primero: son las que activan tareas o una orden por condición crítica
  const orden = { CRITICO: 0, ADVERTENCIA: 1, NORMAL: 2 }
  const filas = [...mediciones].sort((a, b) => orden[a.nivel ?? 'NORMAL'] - orden[b.nivel ?? 'NORMAL'])

  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <table className="w-full min-w-[620px] text-left text-sm">
        <thead>
          <tr className="border-b border-line bg-panel-muted text-xs uppercase tracking-wide text-ink-faint">
            <th className="px-3 py-2 font-medium">Métrica</th>
            <th className="px-3 py-2 font-medium">Valor</th>
            <th className="px-3 py-2 font-medium">Nivel</th>
            <th className="px-3 py-2 font-medium">Límites aplicados</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {filas.map((m, i) => (
            <tr key={`${m.codigo}-${m.instancia ?? ''}-${i}`} className="align-top">
              <td className="px-3 py-2">
                <span className="text-ink">{m.nombre ?? m.codigo}</span>
                {m.instancia && <span className="ml-1 font-mono text-xs text-ink-soft">{m.instancia}</span>}
              </td>
              <td className="max-w-xs px-3 py-2 font-mono tabular-nums">{valorMedicion(m)}</td>
              <td className="px-3 py-2">
                {m.nivel && <StatusPill tone={NIVEL[m.nivel].tono} label={NIVEL[m.nivel].etiqueta} />}
              </td>
              <td className="px-3 py-2 text-xs">
                {(m.umbrales ?? []).length === 0 ? (
                  <span className="text-ink-faint">Sin umbral activo</span>
                ) : (
                  <span className="flex flex-col gap-0.5">
                    {m.umbrales?.map((u, j) => (
                      <Limites key={`${u.idUmbral}-${j}`} umbral={u} unidad={m.unidad} />
                    ))}
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
