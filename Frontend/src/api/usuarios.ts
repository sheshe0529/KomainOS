import { consulta, http, todasLasPaginas } from './cliente'
import type { Pagina, Rol } from './dominio'
import type { ActualizarUsuarioPeticion, CrearUsuarioPeticion, UsuarioRespuesta } from './types'

export const usuariosApi = {
  listar: (filtro: { texto?: string; rol?: Rol; activo?: boolean; tamano?: number } = {}) =>
    http.get<Pagina<UsuarioRespuesta>>(
      `/usuarios${consulta({ texto: filtro.texto, rol: filtro.rol, activo: filtro.activo, size: filtro.tamano ?? 100 })}`,
    ),
  /** Todas las cuentas que cumplen el filtro, sin el tope de una página */
  listarTodos: (filtro: { rol?: Rol; activo?: boolean } = {}) =>
    todasLasPaginas((pagina) =>
      http.get<Pagina<UsuarioRespuesta>>(
        `/usuarios${consulta({ rol: filtro.rol, activo: filtro.activo, page: pagina, size: 200, sort: ['nombreCompleto,asc', 'id,asc'] })}`,
      ),
    ),
  crear: (datos: CrearUsuarioPeticion) => http.post<UsuarioRespuesta>('/usuarios', datos),
  actualizar: (id: number, datos: ActualizarUsuarioPeticion) => http.put<UsuarioRespuesta>(`/usuarios/${id}`, datos),
  cambiarEstado: (id: number, activar: boolean) =>
    http.post<UsuarioRespuesta>(`/usuarios/${id}/${activar ? 'activacion' : 'desactivacion'}`),
}
