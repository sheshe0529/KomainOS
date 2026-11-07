import type { ParametroPeticion } from '@/api/types'

export type Lenguaje = 'bash' | 'powershell'

/** Fila en edición: la clave estable evita que React mezcle los campos al quitar o reordenar */
export interface ParametroEditable extends ParametroPeticion {
  clave: string
}

export const TIPOS_PARAMETRO: { valor: ParametroPeticion['tipo']; etiqueta: string; ejemplo: string }[] = [
  { valor: 'STRING', etiqueta: 'Texto', ejemplo: '/var/tmp' },
  { valor: 'INTEGER', etiqueta: 'Entero', ejemplo: '7' },
  { valor: 'DECIMAL', etiqueta: 'Decimal', ejemplo: '0.5' },
  { valor: 'BOOLEAN', etiqueta: 'Sí / No', ejemplo: 'true' },
  { valor: 'LISTA', etiqueta: 'Lista', ejemplo: '' },
]

const PATRON_NOMBRE = /^[A-Za-z_][A-Za-z0-9_]{0,63}$/

let siguienteClave = 0

export function nuevaClave(): string {
  siguienteClave += 1
  return `p${siguienteClave}`
}

export function editables(parametros: ParametroPeticion[] = []): ParametroEditable[] {
  return parametros.map((p) => ({ ...p, valorPorDefecto: p.valorPorDefecto ?? '', clave: nuevaClave() }))
}

export function aPeticion(parametros: ParametroEditable[]): ParametroPeticion[] {
  return parametros.map(({ nombre, tipo, valorPorDefecto, idListaReferencia }) => ({
    nombre: nombre.trim(),
    tipo,
    valorPorDefecto: tipo !== 'LISTA' && valorPorDefecto?.trim() ? valorPorDefecto.trim() : undefined,
    idListaReferencia: tipo === 'LISTA' ? idListaReferencia : undefined,
  }))
}

/** Problemas que se ven sin preguntar al servidor; los nombres reservados y el contenido los revisa la vista previa */
export function problemaLocal(p: ParametroPeticion, todos: ParametroPeticion[]): string | undefined {
  const nombre = p.nombre.trim()
  if (!nombre) return 'Escriba el nombre'
  if (!PATRON_NOMBRE.test(nombre)) return 'Solo letras sin tilde, números y guion bajo; no empieza con número'
  if (todos.filter((o) => o.nombre.trim().toUpperCase() === nombre.toUpperCase()).length > 1) return 'Nombre repetido'
  if (nombre.toUpperCase().startsWith('KOMAINOS_')) return 'El prefijo KOMAINOS_ está reservado para el sistema'
  if (p.tipo === 'LISTA') return p.idListaReferencia ? undefined : 'Seleccione una lista de referencia'
  const valor = p.valorPorDefecto?.trim() ?? ''
  if (!valor) return 'El parámetro necesita valor'
  if (p.tipo === 'INTEGER' && !/^-?\d{1,18}$/.test(valor)) return 'Debe ser un número entero'
  if (p.tipo === 'DECIMAL' && !/^-?\d+([.,]\d+)?$/.test(valor)) return 'Debe ser un número'
  if (p.tipo === 'BOOLEAN' && !/^(true|false)$/i.test(valor)) return 'Debe ser true o false'
  return undefined
}

/** Cómo se lee el parámetro dentro del script, según el intérprete */
export function usoEnScript(nombre: string, lenguaje: Lenguaje, lista = false): string {
  const n = nombre.trim() || 'NOMBRE'
  return lenguaje === 'bash' ? (lista ? `"\${${n}[@]}"` : `"$${n}"`) : `$${n}`
}
