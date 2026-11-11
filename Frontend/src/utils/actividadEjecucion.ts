import type { EventoActividadRespuesta, TareaMopRespuesta } from '@/api/types'
import { diagnosticarFallo } from './automatizacion'
import type { DiagnosticoFallo } from './automatizacion'

export type FaseActividad = NonNullable<EventoActividadRespuesta['fase']>
export type NivelActividad = NonNullable<EventoActividadRespuesta['nivel']>

/**
 * Un evento de «Ver actividad» (DEC-59). El backend registra cada conexión, la comprobación de acceso, los análisis y
 * las tareas con su hora exacta; para lo ejecutado antes de ese registro reconstruye el inicio y el fin que ya guardaba.
 */
export interface EventoActividad {
  clave: string
  accion: string
  script?: string
  nivel: NivelActividad
  fase: FaseActividad
  estado?: EventoActividadRespuesta['estado']
  fecha?: string
  servidor?: string
  idTarea?: number
  idEvaluacion?: number
  detalle?: string
  /** Mensaje del fallo con su categoría al principio («Error de autenticación: …») */
  error?: string
  diagnostico?: DiagnosticoFallo
}

export function deRespuesta(e: EventoActividadRespuesta, indice = 0): EventoActividad {
  const error = e.error?.trim() || undefined
  return {
    clave: e.clave ?? `evento-${indice}`,
    accion: e.accion ?? 'Evento sin descripción',
    script: e.script,
    nivel: e.nivel ?? 'INFO',
    fase: e.fase ?? 'ORDEN',
    estado: e.estado,
    fecha: e.fecha,
    servidor: e.servidor,
    idTarea: e.idTarea,
    idEvaluacion: e.idEvaluacion,
    detalle: e.detalle,
    error,
    // Una advertencia (por ejemplo, acceso sin privilegios) se explica con su detalle, no como fallo
    diagnostico: error && e.nivel === 'ERROR' ? diagnosticarFallo(error) : undefined,
  }
}

export function deRespuestas(eventos?: EventoActividadRespuesta[]): EventoActividad[] {
  return (eventos ?? []).map((evento, indice) => deRespuesta(evento, indice))
}

export function nombreDeTarea(tarea: TareaMopRespuesta): string {
  if (tarea.tarea) return `Tarea «${tarea.tarea}»`
  return tarea.orden == null ? 'Tarea' : `Tarea ${tarea.orden}`
}
