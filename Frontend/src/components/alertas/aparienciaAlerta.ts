import type { AlertaRespuesta } from '@/api/types'

const NOMBRES: Record<NonNullable<AlertaRespuesta['tipo']>, string> = {
  ADVERTENCIA_UMBRAL: 'Advertencia de umbral',
  CONDICION_CRITICA: 'Condición crítica',
  SIN_IMPLEMENTACION: 'Sin implementación',
  SOLICITUD_AUTORIZACION: 'Solicitud de autorización',
  AUTORIZACION_VENCIDA: 'Autorización vencida',
  SOLICITUD_VALIDACION: 'Solicitud de validación',
  VALIDACION_VENCIDA: 'Validación vencida',
  INCIDENCIA_REGISTRADA: 'Incidencia registrada',
  VENTANA_EXCEDIDA: 'Ventana excedida',
  CIERRE_MANTENIMIENTO: 'Cierre de mantenimiento',
}
export function nombreAlerta(tipo: AlertaRespuesta['tipo']) {
  return tipo ? NOMBRES[tipo] : 'Notificación'
}
export function colorAlerta(tipo: AlertaRespuesta['tipo']) {
  if (['CONDICION_CRITICA', 'INCIDENCIA_REGISTRADA', 'VENTANA_EXCEDIDA'].includes(tipo ?? '')) return 'bg-danger'
  if (['ADVERTENCIA_UMBRAL', 'AUTORIZACION_VENCIDA', 'VALIDACION_VENCIDA', 'SIN_IMPLEMENTACION'].includes(tipo ?? ''))
    return 'bg-warning'
  return tipo === 'CIERRE_MANTENIMIENTO' ? 'bg-success' : 'bg-accent'
}
