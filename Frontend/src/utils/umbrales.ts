import type { AlcanceExcepcion, NivelAlerta, OperadorComparacion, OrigenUmbral, TipoDato } from '@/api/dominio'
import type { MetricaRespuesta, ValoresUmbralRespuesta } from '@/api/types'
import type { StatusTone } from '@/components/ui/StatusPill'

export const OPERADORES: { valor: OperadorComparacion; simbolo: string; etiqueta: string }[] = [
  { valor: 'MAYOR', simbolo: '>', etiqueta: 'Mayor que' },
  { valor: 'MAYOR_IGUAL', simbolo: '≥', etiqueta: 'Mayor o igual que' },
  { valor: 'MENOR', simbolo: '<', etiqueta: 'Menor que' },
  { valor: 'MENOR_IGUAL', simbolo: '≤', etiqueta: 'Menor o igual que' },
  { valor: 'IGUAL', simbolo: '=', etiqueta: 'Igual a' },
  { valor: 'DISTINTO', simbolo: '≠', etiqueta: 'Distinto de' },
]

export function simbolo(operador?: OperadorComparacion): string {
  return OPERADORES.find((o) => o.valor === operador)?.simbolo ?? ''
}

export const ETIQUETA_TIPO_DATO: Record<TipoDato, string> = {
  STRING: 'Texto',
  INTEGER: 'Entero',
  DECIMAL: 'Decimal',
  BOOLEAN: 'Booleano',
}

/** Tipos que admite cada clase de métrica: la medición escalar es numérica y una lista no es de booleanos (R2.4) */
export const TIPOS_ESCALAR: TipoDato[] = ['DECIMAL', 'INTEGER', 'BOOLEAN']
export const TIPOS_LISTA: TipoDato[] = ['STRING', 'INTEGER', 'DECIMAL']

/** "Numérica · %", "Lista de texto" */
export function describirMetrica(m?: Pick<MetricaRespuesta, 'esLista' | 'tipoDato' | 'unidad'>): string {
  if (!m?.tipoDato) return ''
  if (m.esLista) return `Lista de ${ETIQUETA_TIPO_DATO[m.tipoDato].toLowerCase()}`
  const base = m.tipoDato === 'BOOLEAN' ? 'Booleana' : 'Numérica'
  return m.unidad ? `${base} · ${m.unidad}` : base
}

/** Orden de prioridad al resolver: el primero que alcanza al servidor gana (R2.4) */
export const PRIORIDAD: OrigenUmbral[] = ['SERVIDOR', 'GRUPO', 'ENTORNO', 'CRITICIDAD', 'GLOBAL']

export const ETIQUETA_ORIGEN: Record<OrigenUmbral, string> = {
  SERVIDOR: 'Servidor',
  GRUPO: 'Grupo',
  ENTORNO: 'Entorno',
  CRITICIDAD: 'Criticidad',
  GLOBAL: 'Global',
}

export const ETIQUETA_ALCANCE: Record<AlcanceExcepcion, string> = {
  SERVIDOR: 'Servidor',
  GRUPO: 'Grupo',
  ENTORNO: 'Entorno',
  CRITICIDAD: 'Criticidad',
}

export const NIVEL: Record<NivelAlerta, { etiqueta: string; tono: StatusTone }> = {
  NORMAL: { etiqueta: 'Normal', tono: 'success' },
  ADVERTENCIA: { etiqueta: 'Advertencia', tono: 'warning' },
  CRITICO: { etiqueta: 'Crítico', tono: 'danger' },
}

const numero = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 6 })

/** "80", "0,5" o "80 %"; sin valor, un guion: ese nivel no se evalúa */
export function formatearLimite(valor?: number | null, unidad?: string | null): string {
  if (valor === undefined || valor === null) return '—'
  const texto = numero.format(valor)
  return unidad ? `${texto} ${unidad}` : texto
}

/** "> 80 / 90 %" en uno numérico; "Puertos web · 3" en uno de lista */
export function describirValores(v?: ValoresUmbralRespuesta, unidad?: string | null): string {
  if (!v) return '—'
  if (v.lista) return `${v.lista.nombre} · ${v.lista.elementos?.length ?? 0}`
  return `${simbolo(v.operador)} ${formatearLimite(v.valorAdvertencia)} / ${formatearLimite(v.valorCritico, unidad)}`
}

/** Mismo criterio que TipoDato.canonico del backend: un número sin ceros sobrantes, un texto recortado */
export function normalizarElemento(crudo: string, tipo: TipoDato, ignoraMayusculas: boolean): string | null {
  const valor = crudo.trim()
  if (!valor || valor.length > 255) return null
  if (tipo === 'INTEGER') return /^[+-]?\d+$/.test(valor) ? String(BigInt(valor)) : null
  if (tipo === 'DECIMAL') {
    if (!/^[+-]?(\d+\.?\d*|\.\d+)$/.test(valor)) return null
    return String(Number(valor))
  }
  if (tipo === 'STRING') return ignoraMayusculas ? valor.toLowerCase() : valor
  return null
}

export function compararElementos(tipo: TipoDato) {
  return (a: string, b: string) => (tipo === 'STRING' ? a.localeCompare(b) : Number(a) - Number(b))
}

/** Separadores al escribir o pegar elementos: los mismos que acepta la prueba en el backend */
export const SEPARADORES = /[,;\n\r]+/

export const EJEMPLO_ELEMENTOS: Record<TipoDato, string> = {
  INTEGER: '22, 80, 443',
  DECIMAL: '0.5, 1.5',
  STRING: 'sshd, crond, rsyslog',
  BOOLEAN: '',
}
