import { useCallback, useState } from 'react'
import type { Pagina } from '@/api/dominio'

export const TAMANOS_PAGINA = [25, 50, 100] as const
export const TAMANO_POR_DEFECTO = 25

const PREFIJO = 'komainos.filas.'

/** Para las pantallas que guardan la página dentro de su filtro */
export function leerTamanoGuardado(clave?: string): number {
  if (!clave) return TAMANO_POR_DEFECTO
  try {
    const guardado = Number(window.localStorage.getItem(PREFIJO + clave))
    return (TAMANOS_PAGINA as readonly number[]).includes(guardado) ? guardado : TAMANO_POR_DEFECTO
  } catch {
    return TAMANO_POR_DEFECTO
  }
}

export interface Paginado {
  pagina: number
  tamano: number
  setPagina: (pagina: number) => void
  /** Cambiar el tamaño vuelve a la primera página: la actual podría no existir */
  setTamano: (tamano: number) => void
  reiniciar: () => void
}

/**
 * Página y filas por página de una tabla. Con clave, el tamaño elegido se recuerda en este navegador para esa tabla;
 * si el almacenamiento no está disponible se usa el valor por defecto
 */
export function usePaginacion(clave?: string): Paginado {
  const [pagina, setPagina] = useState(0)
  const [tamano, setTamanoEstado] = useState(() => leerTamanoGuardado(clave))

  const setTamano = useCallback(
    (nuevo: number) => {
      setTamanoEstado(nuevo)
      setPagina(0)
      if (clave) guardarTamano(clave, nuevo)
    },
    [clave],
  )
  const reiniciar = useCallback(() => setPagina(0), [])

  return { pagina, tamano, setPagina, setTamano, reiniciar }
}

export function guardarTamano(clave: string, tamano: number) {
  try {
    window.localStorage.setItem(PREFIJO + clave, String(tamano))
  } catch {
    // Solo es una preferencia: sin almacenamiento se usa el tamaño en esta visita
  }
}

/** Una lista ya cargada en el panel, paginada igual que las que pagina el backend */
export function usePaginacionLocal<T>(elementos: readonly T[], clave?: string) {
  const { pagina, tamano, setPagina, setTamano } = usePaginacion(clave)
  const actual = paginarLocal(elementos, pagina, tamano)
  return {
    filas: actual.contenido ?? [],
    controles: {
      pagina: actual.pagina ?? 0,
      totalPaginas: actual.totalPaginas ?? 1,
      totalElementos: actual.totalElementos ?? 0,
      onCambiar: setPagina,
      tamano,
      onTamano: setTamano,
    },
  }
}

/** Una lista ya cargada, recortada como si viniera paginada del backend */
export function paginarLocal<T>(elementos: readonly T[], pagina: number, tamano: number): Pagina<T> {
  const totalPaginas = Math.max(1, Math.ceil(elementos.length / tamano))
  const actual = Math.min(pagina, totalPaginas - 1)
  return {
    contenido: elementos.slice(actual * tamano, (actual + 1) * tamano),
    pagina: actual,
    tamano,
    totalPaginas,
    totalElementos: elementos.length,
  }
}

/** Primera, última, la actual y sus vecinas; el resto se resume con «…» */
export function paginasVisibles(pagina: number, total: number): (number | '…')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i)
  const cerca = new Set([0, total - 1, pagina - 1, pagina, pagina + 1].filter((p) => p >= 0 && p < total))
  if (pagina <= 2) [1, 2, 3].forEach((p) => cerca.add(p))
  if (pagina >= total - 3) [total - 4, total - 3, total - 2].forEach((p) => cerca.add(p))
  const ordenadas = [...cerca].sort((a, b) => a - b)
  const resultado: (number | '…')[] = []
  ordenadas.forEach((p, i) => {
    if (i > 0 && p - ordenadas[i - 1] > 1) resultado.push('…')
    resultado.push(p)
  })
  return resultado
}
