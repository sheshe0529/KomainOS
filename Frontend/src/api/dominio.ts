/** Se derivan de los tipos generados: no pueden desalinearse del backend */
import type {
  ConfiguracionGrupoPeticion,
  CredencialRespuesta,
  SecretoPeticion,
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
export type TipoAutenticacion = SecretoPeticion['tipoAutenticacion']
export type TipoUsuario = NonNullable<SecretoPeticion['tipoUsuario']>
export type EstadoCredencial = NonNullable<CredencialRespuesta['estado']>

export const DIAS_SEMANA: DiaSemana[] = ['LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO']

export interface Pagina<T> {
  contenido?: T[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}
