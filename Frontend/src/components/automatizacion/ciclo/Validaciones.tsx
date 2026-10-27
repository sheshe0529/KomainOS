import type { ValidacionFuncionalRespuesta, ValidacionTecnicaRespuesta } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { ESTADO_VALIDACION } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'

interface ValidacionesProps {
  tecnicas: ValidacionTecnicaRespuesta[]
  funcional?: ValidacionFuncionalRespuesta
}

/** Validación técnica automática por servidor (RF55, RF56) y conformidad del responsable (RF57) */
export function Validaciones({ tecnicas, funcional }: ValidacionesProps) {
  const estadoFuncional = funcional?.estado ? ESTADO_VALIDACION[funcional.estado] : undefined

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section>
        <h4 className="mb-2 text-sm font-semibold text-ink">Validación técnica</h4>
        {tecnicas.length === 0 ? (
          <p className="text-sm text-ink-soft">
            Al terminar las tareas, KomainOS vuelve a analizar cada servidor y compara contra los umbrales.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {tecnicas.map((v) => (
              <li key={v.idDetalle} className="rounded-lg border border-line px-3 py-2 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-ink">{v.servidor}</span>
                  <StatusPill
                    tone={v.resultado === 'CONFORME' ? 'success' : 'danger'}
                    label={v.resultado === 'CONFORME' ? 'Conforme' : 'No conforme'}
                  />
                  <span className="ml-auto text-xs text-ink-faint">{formatearFechaHora(v.fecha)}</span>
                </div>
                {v.observaciones && <p className="mt-1 text-ink-soft">{v.observaciones}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h4 className="mb-2 text-sm font-semibold text-ink">Validación funcional del responsable</h4>
        {!funcional ? (
          <p className="text-sm text-ink-soft">
            Si la validación técnica es conforme, se pide al responsable que confirme que el servicio funciona.
          </p>
        ) : (
          <div className="rounded-lg border border-line px-3 py-2 text-sm">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-ink">{funcional.responsable}</span>
              {estadoFuncional && <StatusPill tone={estadoFuncional.tono} label={estadoFuncional.etiqueta} />}
            </div>
            <p className="mt-1 text-xs text-ink-faint">
              Solicitada {formatearFechaHora(funcional.fechaSolicitud)}
              {funcional.fechaRespuesta
                ? ` · respondió ${formatearFechaHora(funcional.fechaRespuesta)}`
                : ` · plazo ${formatearFechaHora(funcional.fechaLimite)}`}
            </p>
            {funcional.observaciones && <p className="mt-2 text-ink-soft">«{funcional.observaciones}»</p>}
          </div>
        )}
      </section>
    </div>
  )
}
