import { useState } from 'react'
import { ChevronRight } from 'lucide-react'
import type { AnalisisRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { ESTADO_EVALUACION, NIVEL, TIPO_EVALUACION } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'
import { MedicionesAnalisis } from './MedicionesAnalisis'

interface FilaAnalisisProps {
  analisis: AnalisisRespuesta
  /** En la ficha del servidor el nombre sobra */
  conServidor?: boolean
}

/** Un análisis (revisión periódica, evaluación previa o validación técnica) con sus mediciones al desplegarlo */
export function FilaAnalisis({ analisis: a, conServidor = true }: FilaAnalisisProps) {
  const [abierto, setAbierto] = useState(false)
  const estado = a.estado ? ESTADO_EVALUACION[a.estado] : undefined

  return (
    <li className="border-b border-line last:border-b-0">
      <button
        type="button"
        aria-expanded={abierto}
        onClick={() => setAbierto(!abierto)}
        className="flex w-full flex-wrap items-center gap-x-4 gap-y-1 px-4 py-3 text-left text-sm hover:bg-panel-muted"
      >
        <ChevronRight className={`h-4 w-4 text-ink-faint transition-transform ${abierto ? 'rotate-90' : ''}`} aria-hidden="true" />
        <span className="font-medium text-ink">{a.tipo ? TIPO_EVALUACION[a.tipo] : 'Análisis'}</span>
        {conServidor && <span className="font-mono text-ink-soft">{a.servidor}</span>}
        {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
        {a.estado === 'FINALIZADA' && a.nivel && <StatusPill tone={NIVEL[a.nivel].tono} label={NIVEL[a.nivel].etiqueta} />}
        <span className="ml-auto text-xs text-ink-faint">
          {a.script} v{a.numeroVersion} · {formatearFechaHora(a.fechaInicio)}
        </span>
      </button>
      {abierto && (
        <div className="flex flex-col gap-3 px-4 pb-4 pl-12">
          {a.error && <p className="rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">{a.error}</p>}
          {a.cuenta && <p className="text-xs text-ink-soft">Ejecutado con la cuenta {a.cuenta}</p>}
          <MedicionesAnalisis mediciones={a.mediciones ?? []} />
        </div>
      )}
    </li>
  )
}
