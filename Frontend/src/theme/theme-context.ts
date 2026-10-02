import { createContext, useContext } from 'react'

export type Theme = 'light' | 'dark'

/** Lo que elige el usuario: un tema fijo o seguir al sistema operativo */
export type PreferenciaTema = Theme | 'system'

export const THEME_STORAGE_KEY = 'komainos:theme'

export interface ThemeContextValue {
  /** Tema que se ve en este momento */
  theme: Theme
  preferencia: PreferenciaTema
  setPreferencia: (preferencia: PreferenciaTema) => void
}

export const ThemeContext = createContext<ThemeContextValue | null>(null)

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme debe usarse dentro de <ThemeProvider>')
  return ctx
}
