import type { FichaServidorRespuesta } from '@/api/types'
import { Tarjeta } from '@/components/common/Tarjeta'
import { formatearFechaHora } from '@/utils/formato'

/** Bajas y reactivaciones en una sola línea de tiempo, de la más reciente a la más antigua (RF72, RF73) */
export function HistorialBajasServidor({ servidor: s }: { servidor: FichaServidorRespuesta }) {
  const eventos = [
    ...(s.historialBajas ?? []).map((b) => ({ tipo: 'baja' as const, fecha: b.fechaSolicitud ?? '', baja: b })),
    ...(s.reactivaciones ?? []).map((r) => ({ tipo: 'reactivacion' as const, fecha: r.fecha ?? '', usuario: r.usuario?.nombre })),
  ].sort((a, b) => b.fecha.localeCompare(a.fecha))

  if (eventos.length === 0) return null

  return (
    <Tarjeta titulo="Historial de bajas y reactivaciones">
      <ol className="flex flex-col gap-3">
        {eventos.map((e, i) =>
          e.tipo === 'baja' ? (
            <li key={i} className="flex flex-col gap-0.5 border-l-2 border-danger/60 pl-3 text-sm">
              <span className="font-medium text-ink">
                Baja {e.baja.estado === 'APLICADA' ? 'aplicada' : 'pendiente'}
                {e.baja.fechaAplicacion && ` el ${formatearFechaHora(e.baja.fechaAplicacion)}`}
              </span>
              <span className="text-xs text-ink-faint">
                Solicitada el {formatearFechaHora(e.baja.fechaSolicitud)} · {e.baja.solicitante?.nombre ?? 'Sistema'}
              </span>
              <span className="text-ink-soft">Motivo: {e.baja.motivo}</span>
            </li>
          ) : (
            <li key={i} className="flex flex-col gap-0.5 border-l-2 border-success/60 pl-3 text-sm">
              <span className="font-medium text-ink">Reactivado</span>
              <span className="text-xs text-ink-faint">
                {formatearFechaHora(e.fecha)} · {e.usuario ?? 'Sistema'}
              </span>
            </li>
          ),
        )}
      </ol>
    </Tarjeta>
  )
}
