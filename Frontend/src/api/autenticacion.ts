import { api, guardarToken, http, leerToken } from './cliente'
import type { LoginPeticion, PerfilRespuesta, TokenRespuesta, UsuarioSesion } from './types'

export const autenticacionApi = {
  iniciarSesion: (datos: LoginPeticion) => http.post<TokenRespuesta>('/auth/login', datos),
  sesionActual: () => http.get<UsuarioSesion>('/auth/yo'),
  perfil: () => http.get<PerfilRespuesta>('/auth/perfil'),
  /** Renueva la sesión por la actividad del usuario: solo vence por inactividad (RF02) */
  renovar: async () => {
    const actual = leerToken()
    if (!actual) return false

    const respuesta = await api<TokenRespuesta>('/auth/renovar', {
      method: 'POST', headers: { Authorization: `Bearer ${actual}` },
    })
    if (!respuesta.token || leerToken() !== actual) return false

    guardarToken(respuesta.token)
    return true
  },
  /**
   * Revoca la sesión en el servidor (RF02). Recibe el token porque el panel ya lo borró: así, si había vencido, el 401
   * no se informa como una sesión expirada
   */
  cerrarSesion: (token: string) =>
    api<void>('/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${token}` } }),
}
