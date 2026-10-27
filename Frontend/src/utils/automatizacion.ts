import type {
  EstadoAutorizacion,
  EstadoComprobacion,
  EstadoDetalleOrden,
  EstadoEvaluacion,
  EstadoIncidencia,
  EstadoOrden,
  EstadoTareaMop,
  EstadoValidacionFuncional,
  NivelAfectacion,
  NivelAlerta,
  OrigenIncidencia,
  TipoEvaluacion,
  TipoFallo,
  TipoScript,
} from '@/api/dominio'
import type { StatusTone } from '@/components/ui/StatusPill'

type Presentacion = { etiqueta: string; tono: StatusTone }

export const ETIQUETA_TIPO_SCRIPT: Record<TipoScript, string> = {
  ANALISIS: 'Análisis',
  MANTENIMIENTO: 'Mantenimiento',
}

export const DESCRIPCION_TIPO_SCRIPT: Record<TipoScript, string> = {
  ANALISIS: 'Recoge métricas del servidor y las informa en JSON; no modifica nada.',
  MANTENIMIENTO: 'Corrige una condición del servidor; se ejecuta como tarea de una MOP autorizada.',
}

/** RF24: la afectación decide quién autoriza según la política de cambio del entorno */
export const AFECTACION: Record<NivelAfectacion, Presentacion & { descripcion: string }> = {
  SIN_AFECTACION: {
    etiqueta: 'Sin afectación',
    tono: 'success',
    descripcion: 'No interrumpe el servicio: consulta o limpia sin reiniciar nada.',
  },
  PARCIAL: {
    etiqueta: 'Parcial',
    tono: 'warning',
    descripcion: 'Degrada o reinicia un componente; el servicio sigue disponible.',
  },
  TOTAL: {
    etiqueta: 'Total',
    tono: 'danger',
    descripcion: 'Interrumpe el servicio, por ejemplo un reinicio del servidor.',
  },
}

export const ESTADO_TAREA_MOP: Record<EstadoTareaMop, Presentacion> = {
  PENDIENTE: { etiqueta: 'Pendiente', tono: 'neutral' },
  EN_EJECUCION: { etiqueta: 'En ejecución', tono: 'warning' },
  FINALIZADA: { etiqueta: 'Finalizada', tono: 'success' },
  FALLIDA: { etiqueta: 'Fallida', tono: 'danger' },
  NO_INICIADA: { etiqueta: 'No iniciada', tono: 'neutral' },
}

/** Tono de cada estado de la orden: en curso, a la espera de alguien, terminado bien o mal */
export const TONO_ESTADO_ORDEN: Record<EstadoOrden, StatusTone> = {
  PROGRAMADA: 'neutral',
  EN_EVALUACION: 'warning',
  SIN_IMPLEMENTACION: 'danger',
  PENDIENTE_AUTORIZACION: 'warning',
  AUTORIZADA: 'success',
  EN_COLA: 'neutral',
  EN_EJECUCION: 'warning',
  PENDIENTE_VALIDACION: 'warning',
  INCIDENCIA: 'danger',
  REPROGRAMADA: 'neutral',
  RECHAZADA: 'danger',
  AUTORIZACION_VENCIDA: 'danger',
  VALIDACION_VENCIDA: 'danger',
  CANCELADA: 'neutral',
  CERRADA: 'success',
}

export const TONO_ESTADO_DETALLE: Record<EstadoDetalleOrden, StatusTone> = {
  PENDIENTE: 'neutral',
  EN_COLA: 'neutral',
  EN_EJECUCION: 'warning',
  FINALIZADO: 'success',
  FALLIDO: 'danger',
  NO_INICIADO: 'neutral',
}

export const NIVEL: Record<NivelAlerta, Presentacion> = {
  NORMAL: { etiqueta: 'Normal', tono: 'success' },
  ADVERTENCIA: { etiqueta: 'Advertencia', tono: 'warning' },
  CRITICO: { etiqueta: 'Crítico', tono: 'danger' },
}

export const TIPO_EVALUACION: Record<TipoEvaluacion, string> = {
  REVISION_PERIODICA: 'Revisión periódica',
  PREVIA: 'Evaluación previa',
  POSTERIOR: 'Validación técnica',
}

export const ESTADO_EVALUACION: Record<EstadoEvaluacion, Presentacion> = {
  EN_CURSO: { etiqueta: 'En curso', tono: 'warning' },
  FINALIZADA: { etiqueta: 'Finalizada', tono: 'success' },
  FALLIDA: { etiqueta: 'Fallida', tono: 'danger' },
}

/** Etiquetas de TipoFallo en el backend: encabezan el mensaje del error («Etiqueta: detalle») */
const ETIQUETA_BACKEND: Record<TipoFallo, string> = {
  COMUNICACION: 'Error de comunicación',
  AUTENTICACION: 'Error de autenticación',
  PERMISOS: 'Permisos insuficientes',
  TIEMPO_EXCEDIDO: 'Tiempo de ejecución excedido',
  EJECUCION: 'Error de ejecución',
  CONTRATO: 'Salida no válida',
  CONFIGURACION: 'Configuración del controlador',
}

/** Quita del mensaje el tipo de fallo cuando ya se muestra como título */
export function detalleFallo(fallo: TipoFallo | undefined, mensaje?: string): string | undefined {
  if (!fallo || !mensaje) return mensaje
  const prefijo = `${ETIQUETA_BACKEND[fallo]}: `
  if (!mensaje.startsWith(prefijo)) return mensaje
  const detalle = mensaje.slice(prefijo.length)
  return detalle.charAt(0).toUpperCase() + detalle.slice(1)
}

/** RF71: cada fallo de acceso se distingue y orienta la corrección */
export const FALLO: Record<TipoFallo, { etiqueta: string; sugerencia: string }> = {
  COMUNICACION: {
    etiqueta: 'Error de comunicación',
    sugerencia: 'Revise la dirección IP, el puerto SSH o WinRM y el firewall entre el controlador y el servidor.',
  },
  AUTENTICACION: {
    etiqueta: 'Error de autenticación',
    sugerencia: 'Revise el usuario, la contraseña o la llave de la cuenta de servicio y su vigencia.',
  },
  PERMISOS: {
    etiqueta: 'Permisos insuficientes',
    sugerencia: 'La cuenta entra pero no tiene privilegios de administrador (sudo o grupo Administradores).',
  },
  TIEMPO_EXCEDIDO: {
    etiqueta: 'Tiempo excedido',
    sugerencia: 'El script superó su duración estimada. Revise si quedó esperando una entrada o un recurso.',
  },
  EJECUCION: {
    etiqueta: 'Error de ejecución',
    sugerencia: 'El script terminó con un código distinto de cero. Revise la salida de error.',
  },
  CONTRATO: {
    etiqueta: 'Salida no válida',
    sugerencia: 'El análisis no imprimió el JSON de métricas esperado en su última línea.',
  },
  CONFIGURACION: {
    etiqueta: 'Configuración del controlador',
    sugerencia: 'Revise la instalación de Ansible y las variables KOMAINOS_ANSIBLE_* del backend.',
  },
}

export const ESTADO_AUTORIZACION: Record<EstadoAutorizacion, Presentacion> = {
  PENDIENTE: { etiqueta: 'Pendiente', tono: 'warning' },
  APROBADA: { etiqueta: 'Aprobada', tono: 'success' },
  RECHAZADA: { etiqueta: 'Rechazada', tono: 'danger' },
  FECHA_PROPUESTA: { etiqueta: 'Otra fecha propuesta', tono: 'warning' },
  VENCIDA: { etiqueta: 'Vencida', tono: 'danger' },
}

export const ESTADO_VALIDACION: Record<EstadoValidacionFuncional, Presentacion> = {
  PENDIENTE: { etiqueta: 'Pendiente', tono: 'warning' },
  CONFORME: { etiqueta: 'Conforme', tono: 'success' },
  RECHAZADA: { etiqueta: 'No conforme', tono: 'danger' },
  VENCIDA: { etiqueta: 'Vencida', tono: 'danger' },
}

export const ESTADO_INCIDENCIA: Record<EstadoIncidencia, Presentacion> = {
  ABIERTA: { etiqueta: 'Abierta', tono: 'danger' },
  EN_ATENCION: { etiqueta: 'En atención', tono: 'warning' },
  CERRADA: { etiqueta: 'Cerrada', tono: 'success' },
}

export const ORIGEN_INCIDENCIA: Record<OrigenIncidencia, string> = {
  PRUEBA_PILOTO: 'Falla en el servidor piloto',
  FALLO_EJECUCION: 'Falla de ejecución',
  TIEMPO_EXCEDIDO: 'Tiempo excedido',
  VALIDACION_TECNICA_NO_CONFORME: 'Validación técnica no conforme',
  RECHAZO_FUNCIONAL: 'Rechazo del responsable',
}

export const COMPROBACION: Record<EstadoComprobacion, StatusTone> = {
  OK: 'success',
  INFO: 'neutral',
  ADVERTENCIA: 'warning',
  ERROR: 'danger',
}

/** "1 min 05 s" a partir de segundos: las tareas suelen durar segundos, no horas */
export function formatearSegundos(segundos?: number | null): string {
  if (segundos === undefined || segundos === null) return '—'
  if (segundos < 60) return `${segundos} s`
  const minutos = Math.floor(segundos / 60)
  const resto = segundos % 60
  if (minutos < 60) return resto === 0 ? `${minutos} min` : `${minutos} min ${String(resto).padStart(2, '0')} s`
  const horas = Math.floor(minutos / 60)
  return `${horas} h ${minutos % 60} min`
}

/** Valor de una medición: un número con su unidad o los elementos de una lista */
export function valorMedicion(m: { valor?: number; elementos?: string[]; unidad?: string }): string {
  if (m.valor !== undefined && m.valor !== null) return m.unidad ? `${m.valor} ${m.unidad}` : String(m.valor)
  if (m.elementos) return m.elementos.length ? m.elementos.join(', ') : 'Ninguno'
  return '—'
}

/**
 * Descarga un texto como archivo. Los .ps1 llevan BOM: Windows PowerShell 5.1 lee sin BOM como ANSI y rompe las
 * tildes de los mensajes
 */
export function descargarTexto(nombre: string, contenido: string, tipo = 'text/plain') {
  const conBom = nombre.toLowerCase().endsWith('.ps1') || tipo === 'text/csv'
  const blob = new Blob([conBom ? '﻿' : '', contenido], { type: `${tipo};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  enlace.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Una fila CSV con comillas solo donde hacen falta; separador punto y coma, como lo abre Excel en es-PE */
export function filaCsv(valores: (string | number | boolean | undefined | null)[]): string {
  return valores
    .map((v) => {
      const texto = v === undefined || v === null ? '' : String(v)
      return /[";\n\r]/.test(texto) ? `"${texto.replaceAll('"', '""')}"` : texto
    })
    .join(';')
}
