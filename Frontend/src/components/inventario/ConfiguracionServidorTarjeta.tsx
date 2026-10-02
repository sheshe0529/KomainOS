import type { ConfiguracionRespuesta } from '@/api/types'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { ETIQUETA_MODALIDAD } from '@/utils/etiquetas'
import { DatoCuentaServicio } from './DatoCuentaServicio'

export function ConfiguracionServidorTarjeta({ configuracion }: { configuracion?: ConfiguracionRespuesta }) {
  return (
    <Tarjeta titulo="Configuración de mantenimiento">
      {configuracion ? (
        <dl className="grid gap-4">
          <Dato etiqueta="Frecuencia de revisión" valor={`Cada ${configuracion.frecuenciaRevisionDias} días`} />
          <Dato etiqueta="Frecuencia de mantenimiento" valor={`Cada ${configuracion.frecuenciaMantenimientoDias} días`} />
          <Dato
            etiqueta="Modalidad de planificación"
            valor={configuracion.modalidadPlanificacion ? ETIQUETA_MODALIDAD[configuracion.modalidadPlanificacion] : undefined}
          />
          <DatoCuentaServicio configuracion={configuracion} />
        </dl>
      ) : (
        <p className="text-sm text-ink-soft">Sin configuración de mantenimiento.</p>
      )}
    </Tarjeta>
  )
}
