import { http } from './cliente'
import type { LoginPeticion, PerfilRespuesta, TokenRespuesta, UsuarioSesion } from './types'

export const autenticacionApi = {
  /** RF01 / HU01. */
  iniciarSesion: (datos: LoginPeticion) => http.post<TokenRespuesta>('/auth/login', datos),
  sesionActual: () => http.get<UsuarioSesion>('/auth/yo'),
  /** "Mi cuenta": detalle de la cuenta autenticada. */
  perfil: () => http.get<PerfilRespuesta>('/auth/perfil'),
}
