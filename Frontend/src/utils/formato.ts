/**
 * Formato de fechas en español. El backend envía instantes ISO-8601 en UTC;
 * aquí se muestran en la zona horaria del navegador.
 */
const LOCALE = 'es-PE'

const fechaHora = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' })
const fecha = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium' })
const hora = new Intl.DateTimeFormat(LOCALE, { hour: '2-digit', minute: '2-digit', hour12: false })
const fechaLarga = new Intl.DateTimeFormat(LOCALE, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
const mesAnio = new Intl.DateTimeFormat(LOCALE, { month: 'long', year: 'numeric' })

export function formatearFechaHora(iso?: string | null): string {
  return iso ? fechaHora.format(new Date(iso)) : '—'
}

export function formatearFecha(iso?: string | null): string {
  return iso ? fecha.format(new Date(iso)) : '—'
}

export function formatearHora(iso?: string | Date | null): string {
  if (!iso) return '—'
  return hora.format(typeof iso === 'string' ? new Date(iso) : iso)
}

export function formatearFechaLarga(d: Date): string {
  const texto = fechaLarga.format(d)
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

export function formatearMesAnio(d: Date): string {
  const texto = mesAnio.format(d)
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

/** "4 h 30 min" a partir de minutos. */
export function formatearDuracion(minutos?: number | null): string {
  if (minutos === undefined || minutos === null) return '—'
  const h = Math.floor(minutos / 60)
  const m = minutos % 60
  if (h === 0) return `${m} min`
  return m === 0 ? `${h} h` : `${h} h ${m} min`
}

/** Clave de día local "AAAA-MM-DD" para agrupar en el calendario. */
export function claveDia(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

/** Valor para <input type="datetime-local"> en hora local. */
export function aValorFechaHoraLocal(d: Date): string {
  return `${claveDia(d)}T${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
