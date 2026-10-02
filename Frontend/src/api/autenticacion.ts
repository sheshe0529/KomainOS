import { http } from './cliente'
import type { LoginPeticion, PerfilRespuesta, TokenRespuesta, UsuarioSesion } from './types'

export const autenticacionApi = {
  iniciarSesion: (datos: LoginPeticion) => http.post<TokenRespuesta>('/auth/login', datos),
  sesionActual: () => http.get<UsuarioSesion>('/auth/yo'),
  perfil: () => http.get<PerfilRespuesta>('/auth/perfil'),
}
