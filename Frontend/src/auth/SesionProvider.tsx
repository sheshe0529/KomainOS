import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { autenticacionApi } from '@/api/autenticacion'
import { EVENTO_SESION_EXPIRADA, borrarToken, guardarToken, leerToken } from '@/api/cliente'
import type { Rol } from '@/api/dominio'
import type { UsuarioSesion } from '@/api/types'
import { SesionContext } from './sesion-context'

/**
 * Estado de la sesión del usuario (componente "Gestión de autenticación y
 * sesión" de R2.2). Concentrarlo aquí evita que cada vista resuelva por su
 * cuenta si el usuario está autenticado.
 */
export function SesionProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioSesion | null>(null)
  const [cargando, setCargando] = useState(() => leerToken() !== null)
  const [expirada, setExpirada] = useState(false)

  // Al abrir el panel con un token guardado, se valida contra el backend: el
  // token puede haber vencido o la cuenta pudo desactivarse (RNF01).
  useEffect(() => {
    if (!leerToken()) return
    let cancelado = false
    autenticacionApi
      .sesionActual()
      .then((u) => {
        if (!cancelado) setUsuario(u)
      })
      .catch(() => {
        borrarToken()
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })
    return () => {
      cancelado = true
    }
  }, [])

  // Cualquier 401 del backend cierra la sesión en el panel (RF02).
  useEffect(() => {
    function alExpirar() {
      setUsuario(null)
      setExpirada(true)
    }
    window.addEventListener(EVENTO_SESION_EXPIRADA, alExpirar)
    return () => window.removeEventListener(EVENTO_SESION_EXPIRADA, alExpirar)
  }, [])

  const iniciarSesion = useCallback(async (codigo: string, contrasena: string) => {
    const respuesta = await autenticacionApi.iniciarSesion({ codigo, contrasena })
    if (!respuesta.token || !respuesta.usuario) {
      throw new Error('Respuesta de inicio de sesión incompleta')
    }
    guardarToken(respuesta.token)
    setExpirada(false)
    setUsuario(respuesta.usuario)
  }, [])

  const cerrarSesion = useCallback(() => {
    borrarToken()
    setExpirada(false)
    setUsuario(null)
  }, [])

  const tieneRol = useCallback((...roles: Rol[]) => !!usuario?.rol && roles.includes(usuario.rol), [usuario])

  const valor = useMemo(
    () => ({ usuario, cargando, expirada, iniciarSesion, cerrarSesion, tieneRol }),
    [usuario, cargando, expirada, iniciarSesion, cerrarSesion, tieneRol],
  )

  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>
}
