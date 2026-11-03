import { claveDia } from './formato'

/**
 * Fechas de los selectores: «AAAA-MM-DD» y «AAAA-MM-DDTHH:mm» en hora local, el mismo formato que usaban los campos
 * nativos. Nunca se pasan por new Date(texto): «2026-10-31» se leería como medianoche UTC y en Lima sería el día 30
 */

/** Fecha local de un valor «AAAA-MM-DD» o «AAAA-MM-DDTHH:mm»; undefined si no es válido */
export function leerFecha(valor?: string): Date | undefined {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?$/.exec(valor ?? '')
  if (!m) return undefined
  const fecha = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), Number(m[4] ?? 0), Number(m[5] ?? 0))
  return Number.isNaN(fecha.getTime()) ? undefined : fecha
}

export function valorFechaHora(d: Date): string {
  return `${claveDia(d)}T${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export function mismoDia(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

export function sumarDias(d: Date, dias: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + dias, d.getHours(), d.getMinutes())
}

/** Mismo día del mes en otro mes; si no existe (31 de febrero), el último día de ese mes */
export function sumarMeses(d: Date, meses: number): Date {
  const ultimo = new Date(d.getFullYear(), d.getMonth() + meses + 1, 0).getDate()
  return new Date(d.getFullYear(), d.getMonth() + meses, Math.min(d.getDate(), ultimo), d.getHours(), d.getMinutes())
}

/** Seis semanas que empiezan en lunes y contienen el mes: la grilla siempre tiene el mismo alto */
export function semanasDelMes(anio: number, mes: number): Date[][] {
  const primero = new Date(anio, mes, 1)
  const inicio = sumarDias(primero, -((primero.getDay() + 6) % 7))
  return Array.from({ length: 6 }, (_, s) => Array.from({ length: 7 }, (_, d) => sumarDias(inicio, s * 7 + d)))
}

/** Compara por día: los límites min y max son días completos */
export function fueraDeRango(d: Date, min?: string, max?: string): boolean {
  const clave = claveDia(d)
  return (!!min && clave < min.slice(0, 10)) || (!!max && clave > max.slice(0, 10))
}
