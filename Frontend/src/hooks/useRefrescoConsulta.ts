import { useEffect } from 'react'
import type { Consulta } from './useConsulta'

/** Refresca el seguimiento cuando está visible y deja terminar la consulta anterior. */
export function useRefrescoConsulta(
  { cargando, recargar }: Pick<Consulta<unknown>, 'cargando' | 'recargar'>,
  activo: boolean,
  intervaloMs = 4000,
) {
  useEffect(() => {
    if (!activo || cargando) return

    function refrescar() {
      if (document.visibilityState === 'visible') recargar()
    }

    const intervalo = window.setInterval(refrescar, intervaloMs)
    document.addEventListener('visibilitychange', refrescar)
    return () => {
      window.clearInterval(intervalo)
      document.removeEventListener('visibilitychange', refrescar)
    }
  }, [activo, cargando, recargar, intervaloMs])
}
