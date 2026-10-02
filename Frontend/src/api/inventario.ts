import { consulta, http } from './cliente'
import type { EstadoServidor, Pagina } from './dominio'
import type {
  BajaPeticion,
  ConfiguracionGrupoPeticion,
  ConfiguracionPeticion,
  FichaGrupoRespuesta,
  FichaServidorRespuesta,
  GrupoPeticion,
  GrupoResumenRespuesta,
  ResultadoBajaRespuesta,
  ServidorPeticion,
  ServidorResumenRespuesta,
  VentanaPeticion,
} from './types'

export interface FiltroServidores {
  texto?: string
  estado?: EstadoServidor | ''
  idEntorno?: number | ''
  idNivelCriticidad?: number | ''
  idSistemaOperativo?: number | ''
  idResponsable?: number | ''
  pagina?: number
  tamano?: number
  orden?: string
}

export const servidoresApi = {
  listar: (f: FiltroServidores = {}) =>
    http.get<Pagina<ServidorResumenRespuesta>>(
      `/servidores${consulta({
        texto: f.texto,
        estado: f.estado,
        idEntorno: f.idEntorno,
        idNivelCriticidad: f.idNivelCriticidad,
        idSistemaOperativo: f.idSistemaOperativo,
        idResponsable: f.idResponsable,
        page: f.pagina,
        size: f.tamano,
        sort: f.orden,
      })}`,
    ),
  ficha: (id: number) => http.get<FichaServidorRespuesta>(`/servidores/${id}`),
  crear: (datos: ServidorPeticion) => http.post<FichaServidorRespuesta>('/servidores', datos),
  actualizar: (id: number, datos: ServidorPeticion) => http.put<FichaServidorRespuesta>(`/servidores/${id}`, datos),
  configurar: (id: number, datos: ConfiguracionPeticion) =>
    http.put<FichaServidorRespuesta>(`/servidores/${id}/configuracion`, datos),
  reemplazarVentanas: (id: number, ventanas: VentanaPeticion[]) =>
    http.put<FichaServidorRespuesta>(`/servidores/${id}/ventanas`, { ventanas }),
  darDeBaja: (id: number, datos: BajaPeticion) => http.post<ResultadoBajaRespuesta>(`/servidores/${id}/baja`, datos),
  reactivar: (id: number) => http.post<FichaServidorRespuesta>(`/servidores/${id}/reactivacion`),
}

export const gruposApi = {
  listar: () => http.get<GrupoResumenRespuesta[]>('/grupos'),
  ficha: (id: number) => http.get<FichaGrupoRespuesta>(`/grupos/${id}`),
  crear: (datos: GrupoPeticion) => http.post<FichaGrupoRespuesta>('/grupos', datos),
  actualizar: (id: number, datos: GrupoPeticion) => http.put<FichaGrupoRespuesta>(`/grupos/${id}`, datos),
  reemplazarIntegrantes: (id: number, idsServidores: number[]) =>
    http.put<FichaGrupoRespuesta>(`/grupos/${id}/integrantes`, { idsServidores }),
  configurar: (id: number, datos: ConfiguracionGrupoPeticion) =>
    http.put<FichaGrupoRespuesta>(`/grupos/${id}/configuracion`, datos),
  cambiarEstado: (id: number, activar: boolean) =>
    http.post<FichaGrupoRespuesta>(`/grupos/${id}/${activar ? 'activacion' : 'desactivacion'}`),
}
