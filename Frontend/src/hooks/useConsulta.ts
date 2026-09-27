import { useCallback, useEffect, useState } from 'react'

export interface Consulta<T> {
  datos: T | undefined
  error: unknown
  cargando: boolean
  recargar: () => void
  /** Reemplaza los datos sin volver a pedirlos (por ejemplo, con la respuesta de un guardado). */
  reemplazar: (datos: T) => void
}

/**
 * Carga datos del backend con estado de carga, error y recarga. Descarta la
 * respuesta si los parámetros cambiaron mientras la petición estaba en curso,
 * para que una respuesta lenta no pise a una más reciente.
 */
export function useConsulta<T>(cargar: () => Promise<T>, dependencias: readonly unknown[]): Consulta<T> {
  const [datos, setDatos] = useState<T>()
  const [error, setError] = useState<unknown>()
  const [cargando, setCargando] = useState(true)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let vigente = true
    setCargando(true)
    cargar()
      .then((resultado) => {
        if (!vigente) return
        setDatos(resultado)
        setError(undefined)
      })
      .catch((e: unknown) => {
        if (vigente) setError(e)
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- las dependencias las declara quien llama
  }, [...dependencias, version])

  const recargar = useCallback(() => setVersion((v) => v + 1), [])
  const reemplazar = useCallback((nuevos: T) => setDatos(nuevos), [])

  return { datos, error, cargando, recargar, reemplazar }
}
