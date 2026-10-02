import { useCallback, useEffect, useMemo, useState } from 'react'

export interface DefinicionColumna {
  id: string
  etiqueta: string
  /** Siempre visible (por ejemplo, el identificador de la fila o sus acciones) */
  fija?: boolean
  /** Disponible, pero oculta hasta que el usuario la agregue */
  ocultaPorDefecto?: boolean
}

export interface ColumnasVisibles {
  columnas: DefinicionColumna[]
  ve: (id: string) => boolean
  alternar: (id: string) => void
  restablecer: () => void
  cantidadVisibles: number
}

const PREFIJO = 'komainos:columnas:'

/** La elección se recuerda en este navegador y sin almacenamiento la tabla funciona igual (DEC-32) */
export function useColumnasVisibles(tabla: string, columnas: DefinicionColumna[]): ColumnasVisibles {
  const porDefecto = useMemo(() => columnas.filter((c) => c.fija || !c.ocultaPorDefecto).map((c) => c.id), [columnas])
  const fijas = useMemo(() => columnas.filter((c) => c.fija).map((c) => c.id), [columnas])

  const [elegidas, setElegidas] = useState<string[]>(() => {
    try {
      const guardado: unknown = JSON.parse(window.localStorage.getItem(PREFIJO + tabla) ?? 'null')
      // Formato actual: { visibles, conocidas }. El anterior era solo la lista de visibles
      const { visibles, conocidas } = Array.isArray(guardado)
        ? { visibles: guardado as unknown[], conocidas: guardado as unknown[] }
        : ((guardado ?? {}) as { visibles?: unknown[]; conocidas?: unknown[] })
      if (Array.isArray(visibles)) {
        const vistas = Array.isArray(conocidas) ? conocidas : []
        // Las columnas nuevas toman su visibilidad por defecto y las fijas siempre se ven
        return columnas
          .filter((c) => c.fija || (vistas.includes(c.id) ? visibles.includes(c.id) : porDefecto.includes(c.id)))
          .map((c) => c.id)
      }
    } catch {
      // Sin almacenamiento o con un valor corrupto: selección por defecto
    }
    return porDefecto
  })

  useEffect(() => {
    try {
      window.localStorage.setItem(
        PREFIJO + tabla,
        JSON.stringify({ visibles: elegidas, conocidas: columnas.map((c) => c.id) }),
      )
    } catch {
      // La elección dura solo mientras la página esté abierta
    }
  }, [tabla, elegidas, columnas])

  const ve = useCallback((id: string) => elegidas.includes(id), [elegidas])

  const alternar = useCallback(
    (id: string) => {
      if (fijas.includes(id)) return
      setElegidas((actual) => (actual.includes(id) ? actual.filter((c) => c !== id) : [...actual, id]))
    },
    [fijas],
  )

  const restablecer = useCallback(() => setElegidas(porDefecto), [porDefecto])

  return { columnas, ve, alternar, restablecer, cantidadVisibles: columnas.filter((c) => elegidas.includes(c.id)).length }
}
