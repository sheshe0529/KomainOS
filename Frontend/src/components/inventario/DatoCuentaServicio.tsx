import type { ConfiguracionRespuesta } from '@/api/types'
import { Dato } from '@/components/common/Tarjeta'
import { FINALIDADES_CUENTA } from '@/utils/cuentasServicio'

/** Cada finalidad muestra la cuenta asignada; no hay cuenta predeterminada (RF05, DEC-60). */
export function DatoCuentaServicio({ configuracion }: { configuracion: ConfiguracionRespuesta }) {
  return (
    <>
      {FINALIDADES_CUENTA.map(({ finalidad, etiqueta, campoCuenta }) => (
        <Dato
          key={finalidad}
          etiqueta={`Cuenta de ${etiqueta.toLowerCase()}`}
          valor={configuracion[campoCuenta]
            ? configuracion[campoCuenta]?.nombre
            : <span className="text-warning">Sin cuenta asignada</span>}
        />
      ))}
    </>
  )
}
