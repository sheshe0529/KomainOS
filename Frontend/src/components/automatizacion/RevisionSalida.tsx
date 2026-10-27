import { CircleAlert, CircleCheck } from 'lucide-react'
import type { RevisionSalidaRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'

interface RevisionSalidaProps {
  revision: RevisionSalidaRespuesta
}

/**
 * Lo que el ciclo haría con una salida de análisis: qué métricas reconoce del catálogo, cuáles ignora por no existir
 * y cuáles faltan para evaluar los umbrales activos
 */
export function RevisionSalida({ revision }: RevisionSalidaProps) {
  const metricas = revision.metricas ?? []
  const errores = revision.errores ?? []
  const faltantes = revision.faltantes ?? []

  return (
    <div className="flex flex-col gap-3">
      <div
        className={`flex items-start gap-2 rounded-lg px-3 py-2 text-sm ${
          revision.valida ? 'bg-success-soft text-success' : 'bg-danger-soft text-danger'
        }`}
      >
        {revision.valida ? (
          <CircleCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        ) : (
          <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        )}
        <div>
          <p className="font-medium">
            {revision.valida
              ? `Salida válida: ${metricas.length} ${metricas.length === 1 ? 'métrica informada' : 'métricas informadas'}`
              : 'La salida no cumple el contrato'}
          </p>
          {errores.map((e) => (
            <p key={e}>{e}</p>
          ))}
        </div>
      </div>

      {faltantes.length > 0 && (
        <p className="rounded-lg bg-warning-soft px-3 py-2 text-sm text-warning">
          Faltan métricas que exigen los umbrales activos:{' '}
          <span className="font-mono">{faltantes.join(', ')}</span>. Sin ellas, la evaluación del servidor falla.
        </p>
      )}

      {metricas.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-line">
          <table className="w-full min-w-[520px] text-left text-sm">
            <thead>
              <tr className="border-b border-line bg-panel-muted text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-3 py-2 font-medium">Código</th>
                <th className="px-3 py-2 font-medium">Instancia</th>
                <th className="px-3 py-2 font-medium">Valor</th>
                <th className="px-3 py-2 font-medium">Catálogo</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {metricas.map((m, i) => (
                <tr key={`${m.codigo}-${m.instancia ?? ''}-${i}`}>
                  <td className="px-3 py-2">
                    <span className="font-mono">{m.codigo}</span>
                    {m.nombre && <span className="block text-xs text-ink-faint">{m.nombre}</span>}
                  </td>
                  <td className="px-3 py-2 font-mono text-ink-soft">{m.instancia || '—'}</td>
                  <td className="px-3 py-2 font-mono tabular-nums">
                    {m.valor !== undefined && m.valor !== null
                      ? `${m.valor}${m.unidad ? ` ${m.unidad}` : ''}`
                      : (m.elementos ?? []).join(', ') || 'Lista vacía'}
                  </td>
                  <td className="px-3 py-2">
                    {m.reconocida ? (
                      <StatusPill tone="success" label="Reconocida" />
                    ) : (
                      <StatusPill tone="warning" label="No existe: se ignora" />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
