/**
 * Nombres para los enumerados del contrato. Se derivan de los tipos
 * generados (types.ts), así que no pueden desalinearse del backend.
 */
import type {
  ConfiguracionGrupoPeticion,
  Detalle,
  OrdenResumenRespuesta,
  FichaGrupoRespuesta,
  ServidorResumenRespuesta,
  SistemaOperativoRespuesta,
  SolicitudBajaRespuesta,
  UsuarioSesion,
  VentanaPeticion,
} from './types'

export type Rol = NonNullable<UsuarioSesion['rol']>
export type EstadoServidor = NonNullable<ServidorResumenRespuesta['estado']>
export type EstadoGrupo = NonNullable<FichaGrupoRespuesta['estado']>
export type FamiliaSistemaOperativo = NonNullable<SistemaOperativoRespuesta['familia']>
export type DiaSemana = VentanaPeticion['diaInicio']
export type ModalidadPlanificacion = ConfiguracionGrupoPeticion['modalidadPlanificacion']
export type ModoEjecucion = ConfiguracionGrupoPeticion['modoEjecucion']
export type EstadoSolicitudBaja = NonNullable<SolicitudBajaRespuesta['estado']>
export type EstadoOrden = NonNullable<OrdenResumenRespuesta['estado']>
export type EtapaOrden = NonNullable<OrdenResumenRespuesta['etapa']>
export type OrigenOrden = NonNullable<OrdenResumenRespuesta['origen']>
export type EstadoDetalleOrden = NonNullable<Detalle['estado']>

export const DIAS_SEMANA: DiaSemana[] = ['LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO']

/** Página genérica con la forma de PaginaRespuesta del backend. */
export interface Pagina<T> {
  contenido?: T[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}
