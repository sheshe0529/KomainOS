import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { THEME_STORAGE_KEY, ThemeContext, type PreferenciaTema, type Theme } from './theme-context'

const CONSULTA_OSCURO = '(prefers-color-scheme: dark)'

function leerPreferencia(): PreferenciaTema {
  try {
    const guardada = window.localStorage.getItem(THEME_STORAGE_KEY)
    if (guardada === 'light' || guardada === 'dark' || guardada === 'system') return guardada
  } catch {
    // Sin almacenamiento disponible (modo privado, datos bloqueados): se sigue al sistema
  }
  return 'system'
}

function temaDelSistema(): Theme {
  return window.matchMedia(CONSULTA_OSCURO).matches ? 'dark' : 'light'
}

/** Con «Sistema» la interfaz cambia en cuanto cambia la preferencia del equipo, sin recargar */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preferencia, setPreferencia] = useState<PreferenciaTema>(leerPreferencia)
  const [sistema, setSistema] = useState<Theme>(temaDelSistema)
  const theme: Theme = preferencia === 'system' ? sistema : preferencia

  useEffect(() => {
    const media = window.matchMedia(CONSULTA_OSCURO)
    const alCambiar = (evento: MediaQueryListEvent) => setSistema(evento.matches ? 'dark' : 'light')
    media.addEventListener('change', alCambiar)
    return () => media.removeEventListener('change', alCambiar)
  }, [])

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  useEffect(() => {
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, preferencia)
    } catch {
      // La preferencia solo dura esta sesión
    }
  }, [preferencia])

  return <ThemeContext.Provider value={{ theme, preferencia, setPreferencia }}>{children}</ThemeContext.Provider>
}
