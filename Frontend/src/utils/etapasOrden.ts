import type { EstadoOrden, EtapaOrden } from '@/api/dominio'

/** Etapas del ciclo de una orden en el orden en que se recorren (R2.1) */
export const PASOS: EtapaOrden[] = ['PLANIFICACION', 'EVALUACION', 'AUTORIZACION', 'EJECUCION', 'VALIDACION', 'CIERRE']

/** Misma correspondencia que EstadoOrden.etapa() del backend */
export const ETAPA_DE: Record<EstadoOrden, EtapaOrden> = {
  PROGRAMADA: 'PLANIFICACION',
  EN_EVALUACION: 'EVALUACION',
  PENDIENTE_AUTORIZACION: 'AUTORIZACION',
  AUTORIZADA: 'AUTORIZACION',
  EN_COLA: 'EJECUCION',
  EN_EJECUCION: 'EJECUCION',
  EN_VALIDACION_TECNICA: 'VALIDACION',
  PENDIENTE_VALIDACION_FUNCIONAL: 'VALIDACION',
  INCIDENCIA: 'INCIDENCIA',
  CERRADA: 'CIERRE',
}

/** Estados que detienen el ciclo en su etapa sin completarla; los demás finales son motivos de cierre (DEC-61) */
export const DETENIDOS: EstadoOrden[] = ['INCIDENCIA']

/** La incidencia ocurre al ejecutar o al validar: en el recorrido ocupa el lugar de la validación */
export function pasoDe(etapa: EtapaOrden): EtapaOrden {
  return etapa === 'INCIDENCIA' ? 'VALIDACION' : etapa
}

/** Etapas por las que pasó la orden: las demás no tienen nada que mostrar */
export function etapasAlcanzadas(etapa: EtapaOrden, recorrido: EstadoOrden[]): Set<EtapaOrden> {
  return new Set<EtapaOrden>(['PLANIFICACION', pasoDe(etapa), ...recorrido.map((e) => pasoDe(ETAPA_DE[e]))])
}
