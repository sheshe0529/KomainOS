import { createContext, useContext } from 'react'
import type { Rol } from '@/api/dominio'
import type { UsuarioSesion } from '@/api/types'

export interface SesionContextValue {
  usuario: UsuarioSesion | null
  /** Verdadero mientras se valida el token guardado al abrir el panel. */
  cargando: boolean
  /** Verdadero si la última sesión terminó porque el backend la rechazó (RF02). */
  expirada: boolean
  iniciarSesion: (codigo: string, contrasena: string) => Promise<void>
  cerrarSesion: () => void
  tieneRol: (...roles: Rol[]) => boolean
}

export const SesionContext = createContext<SesionContextValue | null>(null)

export function useSesion() {
  const ctx = useContext(SesionContext)
  if (!ctx) throw new Error('useSesion debe usarse dentro de <SesionProvider>')
  return ctx
}
