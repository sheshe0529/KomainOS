import type { MedicionRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { NIVEL, valorMedicion } from '@/utils/automatizacion'

interface MedicionesAnalisisProps {
  mediciones: MedicionRespuesta[]
}

export function MedicionesAnalisis({ mediciones }: MedicionesAnalisisProps) {
  if (mediciones.length === 0) return <p className="text-sm text-ink-soft">Sin mediciones registradas.</p>

  // Las críticas primero: son las que activan tareas o una orden por condición crítica
  const orden = { CRITICO: 0, ADVERTENCIA: 1, NORMAL: 2 }
  const filas = [...mediciones].sort((a, b) => orden[a.nivel ?? 'NORMAL'] - orden[b.nivel ?? 'NORMAL'])

  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <table className="w-full min-w-[420px] text-left text-sm">
        <thead>
          <tr className="border-b border-line bg-panel-muted text-xs uppercase tracking-wide text-ink-faint">
            <th className="px-3 py-2 font-medium">Métrica</th>
            <th className="px-3 py-2 font-medium">Valor</th>
            <th className="px-3 py-2 font-medium">Nivel</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {filas.map((m, i) => (
            <tr key={`${m.codigo}-${m.instancia ?? ''}-${i}`} className="align-middle">
              <td className="px-3 py-2">
                <span className="text-ink">{m.nombre ?? m.codigo}</span>
                {m.instancia && <span className="ml-1 font-mono text-xs text-ink-soft">{m.instancia}</span>}
              </td>
              <td className="max-w-xs px-3 py-2 font-mono tabular-nums">{valorMedicion(m)}</td>
              <td className="px-3 py-2">
                {m.nivel && <StatusPill tone={NIVEL[m.nivel].tono} label={NIVEL[m.nivel].etiqueta} />}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
