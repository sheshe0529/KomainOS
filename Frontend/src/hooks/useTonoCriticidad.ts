import { useCallback, useEffect, useState } from 'react'
import { catalogosApi } from '@/api/catalogos'
import type { StatusTone } from '@/components/ui/StatusPill'

let prioridadesActivas: Promise<number[]> | null = null

/** Prioridades de los niveles activos, cargadas una vez y compartidas por todas las vistas. */
function cargarPrioridades(): Promise<number[]> {
  if (!prioridadesActivas) {
    prioridadesActivas = catalogosApi
      .criticidades()
      .then((niveles) =>
        niveles
          .filter((n) => n.activo && n.prioridad !== undefined)
          .map((n) => n.prioridad!)
          .sort((a, b) => a - b),
      )
      .catch((e: unknown) => {
        prioridadesActivas = null
        throw e
      })
  }
  return prioridadesActivas
}

/** Descarta el catálogo en memoria; se llama al crear o editar niveles. */
export function invalidarCriticidades(): void {
  prioridadesActivas = null
}

/**
 * Tono de una criticidad según su posición entre los niveles activos (menor
 * prioridad = más crítica, DEC-13): el más crítico en rojo, el segundo en
 * ámbar y el resto neutro. Depende del orden y no del valor, así que funciona
 * aunque se agreguen niveles o las prioridades no sean consecutivas.
 */
export function tonoSegunCatalogo(prioridad: number | undefined, prioridadesActivas: number[]): StatusTone {
  if (prioridad === undefined || prioridadesActivas.length === 0) return 'neutral'
  const masCriticos = prioridadesActivas.filter((p) => p < prioridad).length
  if (masCriticos === 0) return 'danger'
  if (masCriticos === 1) return 'warning'
  return 'neutral'
}

/** {@link tonoSegunCatalogo} con el catálogo vigente, cargado una sola vez por sesión. */
export function useTonoCriticidad(): (prioridad?: number) => StatusTone {
  const [orden, setOrden] = useState<number[]>([])

  useEffect(() => {
    let vigente = true
    cargarPrioridades()
      .then((p) => {
        if (vigente) setOrden(p)
      })
      .catch(() => {
        // Sin catálogo se muestra en tono neutro; la etiqueta sigue visible.
      })
    return () => {
      vigente = false
    }
  }, [])

  return useCallback((prioridad?: number) => tonoSegunCatalogo(prioridad, orden), [orden])
}
