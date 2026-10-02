import type { ConfiguracionRespuesta } from '@/api/types'
import { Dato } from '@/components/common/Tarjeta'

/** La cuenta con que se ejecutará el mantenimiento: la propia de la configuración o la predeterminada (RF05, RF06) */
export function DatoCuentaServicio({ configuracion }: { configuracion: ConfiguracionRespuesta }) {
  return (
    <Dato
      etiqueta="Cuenta de servicio"
      valor={
        configuracion.cuentaServicio ? (
          <span>
            {configuracion.cuentaServicio.nombre}
            {configuracion.usaCuentaPredeterminada && <span className="text-ink-faint"> · predeterminada del sistema</span>}
          </span>
        ) : (
          <span className="text-warning">Sin cuenta: no hay una predeterminada definida</span>
        )
      }
    />
  )
}
