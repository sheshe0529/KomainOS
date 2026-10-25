import { api, http } from './cliente'
import type { LoginPeticion, PerfilRespuesta, TokenRespuesta, UsuarioSesion } from './types'

export const autenticacionApi = {
  iniciarSesion: (datos: LoginPeticion) => http.post<TokenRespuesta>('/auth/login', datos),
  sesionActual: () => http.get<UsuarioSesion>('/auth/yo'),
  perfil: () => http.get<PerfilRespuesta>('/auth/perfil'),
  /**
   * Revoca el token en el servidor (RF02). Recibe el token porque el panel ya lo borró: así, si había vencido, el 401
   * no se informa como una sesión expirada
   */
  cerrarSesion: (token: string) =>
    api<void>('/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${token}` } }),
}
