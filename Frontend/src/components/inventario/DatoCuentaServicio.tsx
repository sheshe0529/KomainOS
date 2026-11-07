import type { ConfiguracionRespuesta } from '@/api/types'
import { Dato } from '@/components/common/Tarjeta'
import { FINALIDADES_CUENTA } from '@/utils/cuentasServicio'

/** Cada finalidad muestra su cuenta efectiva y si proviene de la predeterminada del sistema (RF05, RF06). */
export function DatoCuentaServicio({ configuracion }: { configuracion: ConfiguracionRespuesta }) {
  return (
    <>
      {FINALIDADES_CUENTA.map(({ finalidad, etiqueta, campoCuenta, campoUsaPredeterminada }) => (
        <Dato
          key={finalidad}
          etiqueta={`Cuenta de ${etiqueta.toLowerCase()}`}
          valor={configuracion[campoCuenta] ? (
            <span>
              {configuracion[campoCuenta]?.nombre}
              {configuracion[campoUsaPredeterminada] && (
                <>
                  {' '}
                  <em className="text-ink-faint">predeterminada del sistema</em>
                </>
              )}
            </span>
          ) : <span className="text-warning">Sin cuenta: no hay una predeterminada definida</span>}
        />
      ))}
    </>
  )
}
