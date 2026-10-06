import type {
  DiaSemana,
  EstadoCredencial,
  EstadoDetalleOrden,
  EstadoGrupo,
  EstadoOrden,
  EtapaOrden,
  OrigenOrden,
  EstadoServidor,
  FamiliaSistemaOperativo,
  ModalidadPlanificacion,
  ModoEjecucion,
  Rol,
  TipoAutenticacion,
  TipoUsuario,
} from '@/api/dominio'
import type { StatusTone } from '@/components/ui/StatusPill'

export const ETIQUETA_ROL: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador',
  OPERADOR: 'Operador',
  RESPONSABLE: 'Responsable',
}

export const ESTADO_SERVIDOR: Record<EstadoServidor, { etiqueta: string; tono: StatusTone }> = {
  ACTIVO: { etiqueta: 'Activo', tono: 'success' },
  PENDIENTE_DE_CONFIGURACION: { etiqueta: 'Pendiente de configuración', tono: 'warning' },
  DADO_DE_BAJA: { etiqueta: 'Baja', tono: 'danger' },
}

export const ESTADO_GRUPO: Record<EstadoGrupo, { etiqueta: string; tono: StatusTone }> = {
  ACTIVO: { etiqueta: 'Activo', tono: 'success' },
  PENDIENTE_DE_CONFIGURACION: { etiqueta: 'Pendiente de configuración', tono: 'warning' },
  INACTIVO: { etiqueta: 'Inactivo', tono: 'neutral' },
}

export const ETIQUETA_MODALIDAD: Record<ModalidadPlanificacion, string> = {
  AUTOMATICA: 'Automática',
  BAJO_DEMANDA: 'Bajo demanda',
}

export const ETIQUETA_MODO: Record<ModoEjecucion, string> = {
  SECUENCIAL: 'Secuencial',
  PARALELO: 'Paralelo',
}

export const ETIQUETA_FAMILIA: Record<FamiliaSistemaOperativo, string> = {
  LINUX: 'Linux (SSH)',
  WINDOWS: 'Windows (WinRM)',
}

export const ETIQUETA_AUTENTICACION: Record<TipoAutenticacion, string> = {
  PASSWORD: 'Contraseña',
  LLAVE_SSH: 'Llave privada SSH',
}

export const ETIQUETA_TIPO_USUARIO: Record<TipoUsuario, string> = {
  ADMINISTRADOR: 'Administrador',
  GENERICO: 'Genérico',
}

export const ESTADO_CREDENCIAL: Record<EstadoCredencial, { etiqueta: string; tono: StatusTone }> = {
  VIGENTE: { etiqueta: 'Vigente', tono: 'success' },
  REVOCADA: { etiqueta: 'Revocada', tono: 'neutral' },
}

export const ETIQUETA_DIA: Record<DiaSemana, string> = {
  LUNES: 'Lunes',
  MARTES: 'Martes',
  MIERCOLES: 'Miércoles',
  JUEVES: 'Jueves',
  VIERNES: 'Viernes',
  SABADO: 'Sábado',
  DOMINGO: 'Domingo',
}

/** "Sábado 22:00 – Domingo 02:00" o "Sábado 01:00 – 05:00" si es el mismo día */
export function textoVentana(v: { diaInicio?: DiaSemana; horaInicio?: string; diaFin?: DiaSemana; horaFin?: string }): string {
  const hi = (v.horaInicio ?? '').slice(0, 5)
  const hf = (v.horaFin ?? '').slice(0, 5)
  if (!v.diaInicio || !v.diaFin) return '—'
  if (v.diaInicio === v.diaFin) return `${ETIQUETA_DIA[v.diaInicio]} ${hi} – ${hf}`
  return `${ETIQUETA_DIA[v.diaInicio]} ${hi} – ${ETIQUETA_DIA[v.diaFin]} ${hf}`
}

/** Estados del ciclo de la orden (R2.1, tabla 6) */
export const ETIQUETA_ESTADO_ORDEN: Record<EstadoOrden, string> = {
  PROGRAMADA: 'Programada',
  EN_EVALUACION: 'En evaluación',
  SIN_IMPLEMENTACION: 'Sin implementación',
  PENDIENTE_AUTORIZACION: 'Pendiente de autorización',
  AUTORIZADA: 'Autorizada',
  EN_COLA: 'En cola',
  EN_EJECUCION: 'En ejecución',
  PENDIENTE_VALIDACION: 'Pendiente de validación',
  INCIDENCIA: 'Incidencia',
  REPROGRAMADA: 'Reprogramada',
  RECHAZADA: 'Rechazada',
  AUTORIZACION_VENCIDA: 'Autorización vencida',
  VALIDACION_VENCIDA: 'Validación vencida',
  CANCELADA: 'Cancelada',
  CERRADA: 'Cerrada',
}

/** Estados del detalle de orden (R2.1, tabla 7) */
export const ETIQUETA_ESTADO_DETALLE: Record<EstadoDetalleOrden, string> = {
  PENDIENTE: 'Pendiente',
  EN_COLA: 'En cola',
  EN_EJECUCION: 'En ejecución',
  FINALIZADO: 'Finalizado',
  FALLIDO: 'Fallido',
  NO_INICIADO: 'No iniciado',
}

export const ETIQUETA_ETAPA: Record<EtapaOrden, string> = {
  PLANIFICACION: 'Planificación',
  EVALUACION: 'Evaluación',
  AUTORIZACION: 'Autorización',
  EJECUCION: 'Ejecución',
  VALIDACION: 'Validación',
  INCIDENCIA: 'Incidencia',
  CIERRE: 'Cierre',
}

export const ETIQUETA_ORIGEN: Record<OrigenOrden, string> = {
  PLANIFICACION_AUTOMATICA: 'Ciclo automático',
  SOLICITUD_BAJO_DEMANDA: 'Solicitud del administrador',
  LANZAMIENTO_INMEDIATO: 'Lanzamiento inmediato',
  CONDICION_CRITICA: 'Condición crítica',
}

export const ESTADO_FILA_IMPORTACION: Record<'NUEVA' | 'DUPLICADA' | 'ERRONEA', { etiqueta: string; tono: StatusTone }> = {
  NUEVA: { etiqueta: 'Nuevo', tono: 'success' },
  DUPLICADA: { etiqueta: 'Duplicado', tono: 'warning' },
  ERRONEA: { etiqueta: 'Erróneo', tono: 'danger' },
}

export const RESULTADO_IMPORTACION: Record<
  'CREADO' | 'ACTUALIZADO' | 'OMITIDO' | 'RECHAZADO',
  { etiqueta: string; tono: StatusTone }
> = {
  CREADO: { etiqueta: 'Creado', tono: 'success' },
  ACTUALIZADO: { etiqueta: 'Sobrescrito', tono: 'success' },
  OMITIDO: { etiqueta: 'Omitido', tono: 'neutral' },
  RECHAZADO: { etiqueta: 'Rechazado', tono: 'danger' },
}
