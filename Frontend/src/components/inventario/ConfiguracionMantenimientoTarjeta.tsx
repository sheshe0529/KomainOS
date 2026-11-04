import type { ConfiguracionRespuesta } from '@/api/types'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { ETIQUETA_MODALIDAD, ETIQUETA_MODO } from '@/utils/etiquetas'
import { DatoCuentaServicio } from './DatoCuentaServicio'

interface ConfiguracionMantenimientoTarjetaProps {
  configuracion?: ConfiguracionRespuesta
  /** Un grupo indica además si sus integrantes se mantienen uno tras otro o a la vez (RF58) */
  conModoEjecucion?: boolean
}

/** Cada cuánto se revisa y se mantiene, cómo se planifica y con qué cuenta (RF05, RF26); la usan servidores y grupos */
export function ConfiguracionMantenimientoTarjeta({ configuracion, conModoEjecucion }: ConfiguracionMantenimientoTarjetaProps) {
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
          {conModoEjecucion && (
            <Dato
              etiqueta="Modo de ejecución"
              valor={configuracion.modoEjecucion ? ETIQUETA_MODO[configuracion.modoEjecucion] : undefined}
            />
          )}
          <DatoCuentaServicio configuracion={configuracion} />
        </dl>
      ) : (
        <p className="text-sm text-ink-soft">Sin configuración de mantenimiento.</p>
      )}
    </Tarjeta>
  )
}
