import type { CuentaServicioRespuesta } from '@/api/types'
import { ETIQUETA_AUTENTICACION } from '@/utils/etiquetas'

/** Cada finalidad usa la cuenta asignada, sin cuenta predeterminada (DEC-60); ambas pueden apuntar a la misma credencial. */
export const FINALIDADES_CUENTA = [
  {
    finalidad: 'EVALUACION',
    etiqueta: 'Evaluación',
    campoId: 'idCuentaServicioEvaluacion',
    campoCuenta: 'cuentaServicioEvaluacion',
  },
  {
    finalidad: 'MANTENIMIENTO',
    etiqueta: 'Mantenimiento',
    campoId: 'idCuentaServicioMantenimiento',
    campoCuenta: 'cuentaServicioMantenimiento',
  },
] as const

export function rotuloCuentaServicio(cuenta: CuentaServicioRespuesta): string {
  const usuario = cuenta.usuarioAcceso?.trim()
  const nombre = cuenta.nombre?.trim() || usuario || 'Cuenta de servicio'
  const mecanismo = cuenta.tipoAutenticacion ? ETIQUETA_AUTENTICACION[cuenta.tipoAutenticacion] : undefined
  const detalle = [usuario === nombre ? undefined : usuario, mecanismo].filter(Boolean).join(', ')
  return detalle ? `${nombre} (${detalle})` : nombre
}
