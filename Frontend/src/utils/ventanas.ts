import { DIAS_SEMANA, type DiaSemana } from '@/api/dominio'

export interface Ventana {
  diaInicio?: DiaSemana
  horaInicio?: string
  diaFin?: DiaSemana
  horaFin?: string
  duracionMinutos?: number
}

/** Parte de un intervalo que cae en un solo día, en minutos desde las 00:00 */
export interface Tramo {
  dia: number
  desde: number
  hasta: number
  intervalo: number
}

export const MINUTOS_DIA = 24 * 60
const MINUTOS_SEMANA = 7 * MINUTOS_DIA

export function minutos(hora?: string): number {
  const [h, m] = (hora ?? '00:00').split(':').map(Number)
  return (h || 0) * 60 + (m || 0)
}

/** 1440 se muestra como 24:00, el fin de un día completo */
export function hhmm(minutosDelDia: number): string {
  const h = Math.floor(minutosDelDia / 60)
  return `${String(h).padStart(2, '0')}:${String(minutosDelDia % 60).padStart(2, '0')}`
}

/** La informada por el backend o, mientras se edita, calculada igual que él. Cero si no es válido */
export function duracion(v: Ventana): number {
  if (v.duracionMinutos !== undefined) return v.duracionMinutos
  if (!v.diaInicio || !v.diaFin) return 0
  const dias = (DIAS_SEMANA.indexOf(v.diaFin) - DIAS_SEMANA.indexOf(v.diaInicio) + 7) % 7
  const total = dias * MINUTOS_DIA + minutos(v.horaFin) - minutos(v.horaInicio)
  return total > 0 ? total : 0
}

/** Reparte cada intervalo en tramos de un solo día */
export function tramos(ventanas: Ventana[]): Tramo[] {
  const resultado: Tramo[] = []
  ventanas.forEach((v, intervalo) => {
    if (!v.diaInicio) return
    let posicion = DIAS_SEMANA.indexOf(v.diaInicio) * MINUTOS_DIA + minutos(v.horaInicio)
    let restante = Math.min(duracion(v), MINUTOS_SEMANA)
    while (restante > 0) {
      const dia = Math.floor(posicion / MINUTOS_DIA) % 7
      const desde = posicion % MINUTOS_DIA
      const largo = Math.min(restante, MINUTOS_DIA - desde)
      resultado.push({ dia, desde, hasta: desde + largo, intervalo })
      posicion = (posicion + largo) % MINUTOS_SEMANA
      restante -= largo
    }
  })
  return resultado
}

/** Une rangos solapados o contiguos, para contar cada minuto una sola vez */
export function unir(rangos: [number, number][]): [number, number][] {
  const ordenados = [...rangos].sort((a, b) => a[0] - b[0])
  const unidos: [number, number][] = []
  for (const [desde, hasta] of ordenados) {
    const ultimo = unidos[unidos.length - 1]
    if (ultimo && desde <= ultimo[1]) ultimo[1] = Math.max(ultimo[1], hasta)
    else unidos.push([desde, hasta])
  }
  return unidos
}

/** Tramos del día que caen dentro de [desde, hasta), recortados a ese rango */
export function tramosEn(partes: Tramo[], dia: number, desde: number, hasta: number): Tramo[] {
  return partes
    .filter((t) => t.dia === dia && t.desde < hasta && t.hasta > desde)
    .map((t) => ({ ...t, desde: Math.max(t.desde, desde), hasta: Math.min(t.hasta, hasta) }))
}
