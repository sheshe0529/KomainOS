import { consulta, http } from './cliente'
import type { EstadoOrden, Pagina } from './dominio'
import type {
  CancelarOrdenPeticion,
  OrdenDetalleRespuesta,
  OrdenResumenRespuesta,
  ProgramarOrdenPeticion,
  PropuestaRespuesta,
  ReprogramarOrdenPeticion,
} from './types'

export interface FiltroOrdenes {
  codigo?: string
  idServidor?: number
  idGrupo?: number
  estado?: EstadoOrden | ''
  idNivelCriticidad?: number | ''
  desde?: string
  hasta?: string
  pagina?: number
  tamano?: number
}

/** Cronograma y órdenes de mantenimiento (RF27-RF30, RF32, RF33, RF36). */
export const planificacionApi = {
  /** RF32: órdenes programadas en un rango (vista mensual y distribución por día). */
  cronograma: (desde: Date, hasta: Date) =>
    http.get<OrdenResumenRespuesta[]>(`/cronograma${consulta({ desde: desde.toISOString(), hasta: hasta.toISOString() })}`),

  ordenes: (f: FiltroOrdenes = {}) =>
    http.get<Pagina<OrdenResumenRespuesta>>(
      `/ordenes${consulta({
        codigo: f.codigo,
        idServidor: f.idServidor,
        idGrupo: f.idGrupo,
        estado: f.estado,
        idNivelCriticidad: f.idNivelCriticidad,
        desde: f.desde,
        hasta: f.hasta,
        page: f.pagina,
        size: f.tamano,
      })}`,
    ),
  orden: (id: number) => http.get<OrdenDetalleRespuesta>(`/ordenes/${id}`),

  programar: (datos: ProgramarOrdenPeticion) => http.post<OrdenDetalleRespuesta>('/ordenes', datos),
  reprogramar: (id: number, datos: ReprogramarOrdenPeticion) =>
    http.post<OrdenDetalleRespuesta>(`/ordenes/${id}/reprogramacion`, datos),
  cancelar: (id: number, datos: CancelarOrdenPeticion) => http.post<OrdenDetalleRespuesta>(`/ordenes/${id}/cancelacion`, datos),

  /** RF28: primer intervalo disponible sin crear la orden. */
  propuesta: (objetivo: { idServidor?: number; idGrupo?: number }, desde?: Date) =>
    http.get<PropuestaRespuesta>(
      `/planificacion/propuesta${consulta({ idServidor: objetivo.idServidor, idGrupo: objetivo.idGrupo, desde: desde?.toISOString() })}`,
    ),
  /** RF27/RF29: ejecuta ahora la planificación automática. */
}
