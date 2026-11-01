import type { NivelAlerta, ResultadoMantenimiento } from '@/api/dominio'
import type { MantenimientoBitacora } from '@/api/types'
import type { StatusTone } from '@/components/ui/StatusPill'

interface PresentacionResultado {
  etiqueta: string
  tono: StatusTone
  /** Color del punto en la línea de tiempo */
  color: string
  /** Punto hueco: todavía no ocurrió */
  hueco?: boolean
  descripcion: string
}

/** Cómo terminó cada mantenimiento, de un vistazo (RF53) */
export const RESULTADO: Record<ResultadoMantenimiento, PresentacionResultado> = {
  EXITOSO: {
    etiqueta: 'Exitoso',
    tono: 'success',
    color: 'var(--color-success)',
    descripcion: 'Se ejecutaron las tareas y el ciclo cerró conforme.',
  },
  SIN_INTERVENCION: {
    etiqueta: 'Sin intervención',
    tono: 'neutral',
    color: 'var(--color-detail)',
    descripcion: 'El análisis previo no encontró nada que corregir.',
  },
  CON_INCIDENCIA: {
    etiqueta: 'Con incidencia',
    tono: 'warning',
    color: 'var(--color-warning)',
    descripcion: 'Cerró después de atender una incidencia.',
  },
  REQUIERE_ATENCION: {
    etiqueta: 'Requiere atención',
    tono: 'danger',
    color: 'var(--color-danger)',
    descripcion: 'Está detenido: incidencia abierta, falta una implementación o venció la validación.',
  },
  EN_CURSO: {
    etiqueta: 'En curso',
    tono: 'warning',
    color: 'var(--color-accent)',
    descripcion: 'El ciclo avanza: evaluación, autorización, ejecución o validación.',
  },
  PROGRAMADO: {
    etiqueta: 'Programado',
    tono: 'neutral',
    color: 'var(--color-ink-faint)',
    hueco: true,
    descripcion: 'Todavía no empieza.',
  },
  NO_AUTORIZADO: {
    etiqueta: 'No autorizado',
    tono: 'neutral',
    color: 'var(--color-ink-soft)',
    descripcion: 'Se rechazó o venció el plazo de autorización.',
  },
  CANCELADO: {
    etiqueta: 'Cancelado',
    tono: 'neutral',
    color: 'var(--color-line)',
    descripcion: 'Se canceló antes de ejecutarse.',
  },
}

/** Orden de la leyenda: primero lo que terminó bien, al final lo que no llegó a hacerse */
export const ORDEN_RESULTADOS: ResultadoMantenimiento[] = [
  'EXITOSO',
  'SIN_INTERVENCION',
  'CON_INCIDENCIA',
  'REQUIERE_ATENCION',
  'EN_CURSO',
  'PROGRAMADO',
  'NO_AUTORIZADO',
  'CANCELADO',
]

export const TONO_NIVEL: Record<NivelAlerta, StatusTone> = {
  NORMAL: 'success',
  ADVERTENCIA: 'warning',
  CRITICO: 'danger',
}

/** Lo que ocurrió primero: el inicio real si se ejecutó, si no el programado */
export function fechaDe(m: MantenimientoBitacora): string | undefined {
  return m.inicioReal ?? m.inicioProgramado
}

export interface ResumenBitacora {
  /** Ciclos que terminaron: excluye los programados, en curso, cancelados y no autorizados */
  realizados: number
  exitosos: number
  sinIntervencion: number
  conProblemas: number
  /** Porcentaje de ciclos terminados sin incidencias */
  tasaExito?: number
  ultimo?: MantenimientoBitacora
  proximo?: MantenimientoBitacora
}

const TERMINADOS: ResultadoMantenimiento[] = ['EXITOSO', 'SIN_INTERVENCION', 'CON_INCIDENCIA', 'REQUIERE_ATENCION']

export function resumenBitacora(mantenimientos: MantenimientoBitacora[], ahora = new Date()): ResumenBitacora {
  const cuenta = (r: ResultadoMantenimiento) => mantenimientos.filter((m) => m.resultado === r).length
  const realizados = mantenimientos.filter((m) => m.resultado && TERMINADOS.includes(m.resultado)).length
  const exitosos = cuenta('EXITOSO')
  const sinIntervencion = cuenta('SIN_INTERVENCION')
  const conProblemas = cuenta('CON_INCIDENCIA') + cuenta('REQUIERE_ATENCION')
  const ejecutados = [...mantenimientos]
    .filter((m) => m.inicioReal)
    .sort((a, b) => (b.inicioReal ?? '').localeCompare(a.inicioReal ?? ''))
  const proximos = mantenimientos
    .filter((m) => m.resultado === 'PROGRAMADO' && m.inicioProgramado && new Date(m.inicioProgramado) >= ahora)
    .sort((a, b) => (a.inicioProgramado ?? '').localeCompare(b.inicioProgramado ?? ''))
  return {
    realizados,
    exitosos,
    sinIntervencion,
    conProblemas,
    tasaExito: realizados ? Math.round(((exitosos + sinIntervencion) / realizados) * 100) : undefined,
    ultimo: ejecutados[0],
    proximo: proximos[0],
  }
}

/** Posición en porcentaje dentro del periodo, para ubicar cada punto en la línea de tiempo */
export function posicionEnPeriodo(fecha: string | undefined, desde: number, hasta: number): number {
  if (!fecha) return 0
  if (hasta <= desde) return 50
  const t = new Date(fecha).getTime()
  return Math.min(100, Math.max(0, ((t - desde) / (hasta - desde)) * 100))
}

/** Límites del periodo con un pequeño margen a cada lado, para que los extremos no queden pegados al borde */
export function periodoDe(mantenimientos: MantenimientoBitacora[]): { desde: number; hasta: number } | undefined {
  const tiempos = mantenimientos
    .map(fechaDe)
    .filter((f): f is string => !!f)
    .map((f) => new Date(f).getTime())
  if (tiempos.length === 0) return undefined
  const minimo = Math.min(...tiempos)
  const maximo = Math.max(...tiempos)
  const margen = Math.max((maximo - minimo) * 0.04, 12 * 3600 * 1000)
  return { desde: minimo - margen, hasta: maximo + margen }
}

/** Años con mantenimientos, del más reciente al más antiguo */
export function aniosDe(mantenimientos: MantenimientoBitacora[]): number[] {
  const anios = new Set(
    mantenimientos
      .map(fechaDe)
      .filter((f): f is string => !!f)
      .map((f) => new Date(f).getFullYear()),
  )
  return [...anios].sort((a, b) => b - a)
}
