import { consulta, http } from './cliente'
import type { Pagina, Rol } from './dominio'
import type { ActualizarUsuarioPeticion, CrearUsuarioPeticion, UsuarioRespuesta } from './types'

/** Usuarios y roles (RF03). */
export const usuariosApi = {
  listar: (filtro: { texto?: string; rol?: Rol; activo?: boolean; tamano?: number } = {}) =>
    http.get<Pagina<UsuarioRespuesta>>(
      `/usuarios${consulta({ texto: filtro.texto, rol: filtro.rol, activo: filtro.activo, size: filtro.tamano ?? 100 })}`,
    ),
  crear: (datos: CrearUsuarioPeticion) => http.post<UsuarioRespuesta>('/usuarios', datos),
  actualizar: (id: number, datos: ActualizarUsuarioPeticion) => http.put<UsuarioRespuesta>(`/usuarios/${id}`, datos),
  cambiarEstado: (id: number, activar: boolean) =>
    http.post<UsuarioRespuesta>(`/usuarios/${id}/${activar ? 'activacion' : 'desactivacion'}`),
}
