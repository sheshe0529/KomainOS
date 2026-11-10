import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { autenticacionApi } from '@/api/autenticacion'
import { EVENTO_SESION_EXPIRADA, borrarToken, guardarToken, leerToken } from '@/api/cliente'
import type { Rol } from '@/api/dominio'
import type { UsuarioSesion } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Modal } from '@/components/ui/Modal'
import { SesionContext } from './sesion-context'
import { emitidoEl, venceEl } from './sesionActiva'

/** Cada cuánto se revisa la sesión y, si hubo actividad, se renueva como mucho una vez por intervalo */
const REVISION_MS = 15_000
const RENOVACION_MINIMA_MS = 60_000
/** Sin actividad, se avisa este tiempo antes de que venza */
const AVISO_MS = 60_000
const EVENTOS_ACTIVIDAD = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'pointermove'] as const

export function SesionProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioSesion | null>(null)
  const [cargando, setCargando] = useState(() => leerToken() !== null)
  const [expirada, setExpirada] = useState(false)
  const [aviso, setAviso] = useState(false)
  const [segundos, setSegundos] = useState(0)
  const ultimaActividad = useRef(0)
  const ultimaRenovacion = useRef(0)

  // El token guardado se valida contra el backend: pudo vencer o la cuenta pudo desactivarse (RNF01)
  useEffect(() => {
    const token = leerToken()
    if (!token) return
    let cancelado = false
    autenticacionApi
      .sesionActual()
      .then((u) => {
        if (!cancelado && leerToken()) setUsuario(u)
      })
      .catch(() => {
        if (!cancelado && leerToken() === token) borrarToken()
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })
    return () => {
      cancelado = true
    }
  }, [])

  // El rechazo del token vigente cierra la sesión en el panel (RF02)
  useEffect(() => {
    function alExpirar() {
      setUsuario(null)
      setExpirada(true)
      setAviso(false)
    }
    window.addEventListener(EVENTO_SESION_EXPIRADA, alExpirar)
    return () => window.removeEventListener(EVENTO_SESION_EXPIRADA, alExpirar)
  }, [])

  const renovar = useCallback(async () => {
    try {
      if (await autenticacionApi.renovar()) {
        ultimaRenovacion.current = Date.now()
        setAviso(false)
      }
    } catch {
      // El 401 del token vigente se atiende por el evento. Un fallo de red se reintenta en la siguiente revisión
    }
  }, [])

  /**
   * RF02: la sesión vence por inactividad. Mientras el usuario usa el panel se renueva (como mucho una vez por minuto);
   * sin actividad, un minuto antes de vencer se pregunta si sigue ahí
   */
  useEffect(() => {
    if (!usuario) return
    ultimaActividad.current = Date.now()
    ultimaRenovacion.current = emitidoEl(leerToken()) ?? Date.now()
    const registrar = () => {
      ultimaActividad.current = Date.now()
    }
    EVENTOS_ACTIVIDAD.forEach((e) => window.addEventListener(e, registrar, { passive: true }))
    const revisar = window.setInterval(() => {
      const vence = venceEl(leerToken())
      if (!vence) return
      const ahora = Date.now()
      const activo = ultimaActividad.current > ultimaRenovacion.current
      if (activo && ahora - ultimaRenovacion.current >= RENOVACION_MINIMA_MS) {
        void renovar()
      } else {
        // Otra pestaña pudo renovarla: el aviso depende del token vigente
        setAviso(!activo && vence - ahora <= AVISO_MS)
      }
    }, REVISION_MS)
    return () => {
      EVENTOS_ACTIVIDAD.forEach((e) => window.removeEventListener(e, registrar))
      window.clearInterval(revisar)
    }
  }, [usuario, renovar])

  // Cuenta regresiva del aviso; al llegar a cero la sesión ya venció y el backend la rechaza
  useEffect(() => {
    if (!aviso) return
    const actualizar = () => {
      const vence = venceEl(leerToken())
      const restante = vence ? Math.max(0, Math.round((vence - Date.now()) / 1000)) : 0
      setSegundos(restante)
      if (restante === 0) {
        borrarToken()
        window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA))
      }
    }
    actualizar()
    const reloj = window.setInterval(actualizar, 1000)
    return () => window.clearInterval(reloj)
  }, [aviso])

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
    const token = leerToken()
    borrarToken()
    setExpirada(false)
    setAviso(false)
    setUsuario(null)
    // Sin esperar la respuesta: la sesión local ya terminó, y si el servidor no responde el token vence solo
    if (token) autenticacionApi.cerrarSesion(token).catch(() => undefined)
  }, [])

  const tieneRol = useCallback((...roles: Rol[]) => !!usuario?.rol && roles.includes(usuario.rol), [usuario])

  const valor = useMemo(
    () => ({ usuario, cargando, expirada, iniciarSesion, cerrarSesion, tieneRol }),
    [usuario, cargando, expirada, iniciarSesion, cerrarSesion, tieneRol],
  )

  return (
    <SesionContext.Provider value={valor}>
      {children}
      {usuario && aviso && (
        <Modal
          abierto
          titulo="¿Sigue ahí?"
          onCerrar={() => void renovar()}
          pie={
            <>
              <Boton onClick={cerrarSesion}>Cerrar sesión</Boton>
              <Boton variante="primario" onClick={() => void renovar()}>
                Seguir conectado
              </Boton>
            </>
          }
        >
          <p className="text-sm text-ink-soft">
            Por inactividad, su sesión se cerrará en{' '}
            <span className="font-mono font-semibold tabular-nums text-ink">{segundos}</span> segundos.
          </p>
        </Modal>
      )}
    </SesionContext.Provider>
  )
}
