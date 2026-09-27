import { http } from './cliente'
import type {
  EntornoPeticion,
  EntornoRespuesta,
  NivelCriticidadPeticion,
  NivelCriticidadRespuesta,
  ParametrosSistemaPeticion,
  ParametrosSistemaRespuesta,
  SistemaOperativoPeticion,
  SistemaOperativoRespuesta,
  VersionSistemaOperativoPeticion,
} from './types'

/** Catálogos del inventario (RF15, RF74) y parámetros de ejecución (RF68). */
export const catalogosApi = {
  entornos: () => http.get<EntornoRespuesta[]>('/entornos'),
  crearEntorno: (datos: EntornoPeticion) => http.post<EntornoRespuesta>('/entornos', datos),
  actualizarEntorno: (id: number, datos: EntornoPeticion) => http.put<EntornoRespuesta>(`/entornos/${id}`, datos),
  cambiarEstadoEntorno: (id: number, activar: boolean) =>
    http.post<EntornoRespuesta>(`/entornos/${id}/${activar ? 'activacion' : 'desactivacion'}`),

  criticidades: () => http.get<NivelCriticidadRespuesta[]>('/niveles-criticidad'),
  crearCriticidad: (datos: NivelCriticidadPeticion) => http.post<NivelCriticidadRespuesta>('/niveles-criticidad', datos),
  actualizarCriticidad: (id: number, datos: NivelCriticidadPeticion) =>
    http.put<NivelCriticidadRespuesta>(`/niveles-criticidad/${id}`, datos),
  cambiarEstadoCriticidad: (id: number, activar: boolean) =>
    http.post<NivelCriticidadRespuesta>(`/niveles-criticidad/${id}/${activar ? 'activacion' : 'desactivacion'}`),
  eliminarCriticidad: (id: number) => http.delete<void>(`/niveles-criticidad/${id}`),

  sistemasOperativos: () => http.get<SistemaOperativoRespuesta[]>('/sistemas-operativos'),
  crearSistemaOperativo: (datos: SistemaOperativoPeticion) =>
    http.post<SistemaOperativoRespuesta>('/sistemas-operativos', datos),
  actualizarSistemaOperativo: (id: number, datos: SistemaOperativoPeticion) =>
    http.put<SistemaOperativoRespuesta>(`/sistemas-operativos/${id}`, datos),
  agregarVersion: (idSistema: number, datos: VersionSistemaOperativoPeticion) =>
    http.post<SistemaOperativoRespuesta>(`/sistemas-operativos/${idSistema}/versiones`, datos),
  actualizarVersion: (idVersion: number, datos: VersionSistemaOperativoPeticion) =>
    http.put<SistemaOperativoRespuesta>(`/versiones-sistema-operativo/${idVersion}`, datos),

  parametros: () => http.get<ParametrosSistemaRespuesta>('/parametros-sistema'),
  actualizarParametros: (datos: ParametrosSistemaPeticion) =>
    http.put<ParametrosSistemaRespuesta>('/parametros-sistema', datos),
}
