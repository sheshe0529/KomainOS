import type { EstadoOrden, EtapaOrden } from '@/api/dominio'

/** Etapas del ciclo de una orden en el orden en que se recorren (R2.1) */
export const PASOS: EtapaOrden[] = ['PLANIFICACION', 'EVALUACION', 'AUTORIZACION', 'EJECUCION', 'VALIDACION', 'CIERRE']

/** Misma correspondencia que EstadoOrden.etapa() del backend */
export const ETAPA_DE: Record<EstadoOrden, EtapaOrden> = {
  PROGRAMADA: 'PLANIFICACION',
  REPROGRAMADA: 'PLANIFICACION',
  EN_EVALUACION: 'EVALUACION',
  SIN_IMPLEMENTACION: 'EVALUACION',
  PENDIENTE_AUTORIZACION: 'AUTORIZACION',
  AUTORIZADA: 'AUTORIZACION',
  RECHAZADA: 'AUTORIZACION',
  AUTORIZACION_VENCIDA: 'AUTORIZACION',
  EN_COLA: 'EJECUCION',
  EN_EJECUCION: 'EJECUCION',
  PENDIENTE_VALIDACION: 'VALIDACION',
  VALIDACION_VENCIDA: 'VALIDACION',
  INCIDENCIA: 'INCIDENCIA',
  CANCELADA: 'CIERRE',
  CERRADA: 'CIERRE',
}

/** Estados que detienen el ciclo en su etapa sin completarla */
export const DETENIDOS: EstadoOrden[] = ['SIN_IMPLEMENTACION', 'RECHAZADA', 'AUTORIZACION_VENCIDA', 'VALIDACION_VENCIDA', 'INCIDENCIA']

/** La incidencia ocurre al ejecutar o al validar: en el recorrido ocupa el lugar de la validación */
export function pasoDe(etapa: EtapaOrden): EtapaOrden {
  return etapa === 'INCIDENCIA' ? 'VALIDACION' : etapa
}

/** Etapas por las que pasó la orden: las demás no tienen nada que mostrar */
export function etapasAlcanzadas(etapa: EtapaOrden, recorrido: EstadoOrden[]): Set<EtapaOrden> {
  return new Set<EtapaOrden>(['PLANIFICACION', pasoDe(etapa), ...recorrido.map((e) => pasoDe(ETAPA_DE[e]))])
}
