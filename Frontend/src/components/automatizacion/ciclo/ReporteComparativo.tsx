import { ArrowRight } from 'lucide-react'
import type { NivelAlerta } from '@/api/dominio'
import type { AnalisisRespuesta, ReporteServidor } from '@/api/types'
import { FilaAnalisis } from '../FilaAnalisis'

interface ReporteComparativoProps {
  reporte: ReporteServidor[]
  evaluaciones: AnalisisRespuesta[]
}

const COLOR_NIVEL: Record<NivelAlerta, string> = {
  NORMAL: 'text-ink',
  ADVERTENCIA: 'text-warning',
  CRITICO: 'text-danger',
}

function Valor({ texto, nivel }: { texto?: string; nivel?: NivelAlerta }) {
  if (!texto) return <span className="text-ink-faint">—</span>
  return <span className={`font-mono ${nivel ? COLOR_NIVEL[nivel] : 'text-ink'}`}>{texto}</span>
}

/** Reporte comparativo (RF54): métricas antes y después de intervenir, tareas y errores por servidor */
export function ReporteComparativo({ reporte, evaluaciones }: ReporteComparativoProps) {
  return (
    <div className="flex flex-col gap-6">
      {reporte.length === 0 ? (
        <p className="text-sm text-ink-soft">El reporte aparece cuando la evaluación previa registra métricas.</p>
      ) : (
        reporte.map((r) => (
          <section key={r.idServidor} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
              <h4 className="font-mono text-sm font-semibold text-ink">{r.servidor}</h4>
              <span className="text-xs text-ink-soft">
                <span className="text-success">{r.tareasFinalizadas} finalizadas</span> ·{' '}
                <span className={r.tareasFallidas ? 'text-danger' : ''}>{r.tareasFallidas} fallidas</span> ·{' '}
                {r.tareasNoIniciadas} no iniciadas
              </span>
            </div>
            {(r.metricas ?? []).length > 0 && (
              <div className="overflow-x-auto rounded-lg border border-line">
                <table className="w-full min-w-[560px] text-left text-sm">
                  <thead>
                    <tr className="border-b border-line bg-panel-muted text-xs uppercase tracking-wide text-ink-faint">
                      <th className="px-3 py-2 font-medium">Métrica</th>
                      <th className="px-3 py-2 font-medium">Antes</th>
                      <th className="w-6 px-1 py-2">
                        <span className="sr-only">a</span>
                      </th>
                      <th className="px-3 py-2 font-medium">Después</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {r.metricas?.map((m, i) => (
                      <tr key={`${m.codigo}-${m.instancia ?? ''}-${i}`}>
                        <td className="px-3 py-2">
                          {m.nombre ?? m.codigo}
                          {m.instancia && <span className="ml-1 font-mono text-xs text-ink-soft">{m.instancia}</span>}
                          {m.unidad && <span className="ml-1 text-xs text-ink-faint">({m.unidad})</span>}
                        </td>
                        <td className="max-w-[14rem] px-3 py-2">
                          <Valor texto={m.antes} nivel={m.nivelAntes} />
                        </td>
                        <td className="px-1 py-2 text-ink-faint">
                          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                        </td>
                        <td className="max-w-[14rem] px-3 py-2">
                          <Valor texto={m.despues} nivel={m.nivelDespues} />
                          {!m.despues && <span className="ml-1 text-xs text-ink-faint">sin validación técnica aún</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {(r.errores ?? []).length > 0 && (
              <ul className="flex flex-col gap-1 rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">
                {r.errores?.map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
              </ul>
            )}
            {(r.advertencias ?? []).length > 0 && (
              <ul className="flex flex-col gap-1 rounded-lg bg-warning-soft px-3 py-2 text-sm text-warning">
                {r.advertencias?.map((a, i) => (
                  <li key={i}>{a}</li>
                ))}
              </ul>
            )}
          </section>
        ))
      )}

      {evaluaciones.length > 0 && (
        <section>
          <h4 className="mb-2 text-sm font-semibold text-ink">Análisis ejecutados ({evaluaciones.length})</h4>
          <ol className="overflow-hidden rounded-lg border border-line">
            {evaluaciones.map((a) => (
              <FilaAnalisis key={a.id} analisis={a} />
            ))}
          </ol>
        </section>
      )}
    </div>
  )
}
