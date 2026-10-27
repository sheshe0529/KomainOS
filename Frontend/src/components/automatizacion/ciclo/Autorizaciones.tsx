import { Bot } from 'lucide-react'
import type { RondaAutorizacion } from '@/api/types'
import { StatusPill } from '@/components/ui/StatusPill'
import { AFECTACION, ESTADO_AUTORIZACION } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'

interface AutorizacionesProps {
  rondas: RondaAutorizacion[]
}

const APROBADOR = { RESPONSABLE: 'Responsable del servidor', ADMINISTRADOR: 'Administrador' }

/** Rondas de autorización (RF43-RF48, RF75): secuencia de aprobadores, plazo compartido y motivo de cada decisión */
export function Autorizaciones({ rondas }: AutorizacionesProps) {
  if (rondas.length === 0) {
    return <p className="text-sm text-ink-soft">La autorización se solicita al terminar la evaluación previa.</p>
  }

  return (
    <ol className="flex flex-col gap-4">
      {[...rondas].reverse().map((r) => (
        <li key={r.id} className="rounded-lg border border-line">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-line bg-panel-muted px-4 py-2.5 text-sm">
            <span className="font-medium text-ink">Ronda {r.ronda}</span>
            <span className="text-ink-soft">
              {r.entorno} · afectación {r.afectacion ? AFECTACION[r.afectacion].etiqueta.toLowerCase() : '—'}
            </span>
            {!r.automatica && <span className="ml-auto text-xs text-ink-soft">Plazo: {formatearFechaHora(r.fechaLimite)}</span>}
          </div>
          {r.automatica ? (
            <p className="flex items-center gap-2 px-4 py-3 text-sm text-ink-soft">
              <Bot className="h-4 w-4" aria-hidden="true" />
              Autorizada automáticamente: la política del entorno no exige aprobaciones para esta afectación.
            </p>
          ) : (
            <ol className="divide-y divide-line">
              {r.decisiones?.map((d) => {
                const estado = d.estado ? ESTADO_AUTORIZACION[d.estado] : undefined
                return (
                  <li key={d.id} className="flex flex-col gap-1 px-4 py-3 text-sm">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                      <span className="font-mono text-xs text-ink-faint">{d.secuencia}.</span>
                      <span className="font-medium text-ink">{d.tipo ? APROBADOR[d.tipo] : ''}</span>
                      {d.usuario && <span className="text-ink-soft">{d.usuario}</span>}
                      {estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}
                      <span className="ml-auto text-xs text-ink-faint">
                        {d.fechaDecision
                          ? `Decidió ${formatearFechaHora(d.fechaDecision)}`
                          : d.fechaSolicitud
                            ? `Solicitada ${formatearFechaHora(d.fechaSolicitud)}`
                            : 'Espera su turno'}
                      </span>
                    </div>
                    {d.fechaPropuesta && (
                      <p className="pl-6 text-ink">Propone iniciar el {formatearFechaHora(d.fechaPropuesta)}</p>
                    )}
                    {d.motivo && <p className="pl-6 text-ink-soft">«{d.motivo}»</p>}
                  </li>
                )
              })}
            </ol>
          )}
        </li>
      ))}
    </ol>
  )
}
