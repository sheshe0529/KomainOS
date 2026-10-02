import type { FichaServidorRespuesta } from '@/api/types'
import { formatearFechaHora } from '@/utils/formato'

/** Baja pendiente, baja aplicada con su motivo, o configuración pendiente */
export function AvisosServidor({ servidor: s }: { servidor: FichaServidorRespuesta }) {
  const ultimaBaja = s.historialBajas?.find((b) => b.estado === 'APLICADA')

  return (
    <>
      {s.bajaPendiente && (
        <div className="rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm">
          <p className="font-medium text-warning">
            Baja solicitada el {formatearFechaHora(s.bajaPendiente.fechaSolicitud)}
            {s.bajaPendiente.solicitante?.nombre && ` por ${s.bajaPendiente.solicitante.nombre}`}: se aplicará al finalizar el
            mantenimiento en curso.
          </p>
          <p className="mt-1 text-ink">Motivo: {s.bajaPendiente.motivo}</p>
        </div>
      )}
      {s.estado === 'DADO_DE_BAJA' && ultimaBaja && (
        <div className="rounded-lg border border-line bg-panel-muted px-4 py-3 text-sm">
          <p className="font-medium text-ink">
            Dado de baja el {formatearFechaHora(ultimaBaja.fechaAplicacion ?? ultimaBaja.fechaSolicitud)}
            {ultimaBaja.solicitante?.nombre && ` por ${ultimaBaja.solicitante.nombre}`}. No admite mantenimientos nuevos y conserva
            su historial.
          </p>
          <p className="mt-1 text-ink-soft">
            <span className="font-medium text-ink">Motivo:</span> {ultimaBaja.motivo}
          </p>
        </div>
      )}
      {s.estado === 'PENDIENTE_DE_CONFIGURACION' && (
        <p className="rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-warning">
          El servidor está pendiente de configuración y no genera mantenimientos hasta definir su configuración de mantenimiento.
        </p>
      )}
    </>
  )
}
