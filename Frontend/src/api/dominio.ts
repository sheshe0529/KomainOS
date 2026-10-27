/** Se derivan de los tipos generados: no pueden desalinearse del backend */
import type {
  AlertaRespuesta,
  AnalisisRespuesta,
  Comprobacion,
  ConexionRespuesta,
  ConfiguracionGrupoPeticion,
  CredencialRespuesta,
  SecretoPeticion,
  Detalle,
  OrdenResumenRespuesta,
  DecisionAutorizacion,
  ExcepcionUmbralRespuesta,
  FichaGrupoRespuesta,
  IncidenciaRespuesta,
  MetricaRespuesta,
  PruebaUmbralRespuesta,
  ScriptRespuesta,
  ServidorResumenRespuesta,
  SistemaOperativoRespuesta,
  SolicitudBajaRespuesta,
  TareaMopRespuesta,
  TareaRespuesta,
  UmbralRespuesta,
  UsuarioSesion,
  ValidacionFuncionalRespuesta,
  ValidacionTecnicaRespuesta,
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
export type TipoDato = NonNullable<MetricaRespuesta['tipoDato']>
export type OperadorComparacion = NonNullable<UmbralRespuesta['operador']>
export type NivelAlerta = NonNullable<PruebaUmbralRespuesta['nivel']>
export type AlcanceExcepcion = NonNullable<ExcepcionUmbralRespuesta['alcance']>
export type OrigenUmbral = NonNullable<PruebaUmbralRespuesta['origen']>

export type TipoScript = NonNullable<ScriptRespuesta['tipo']>
export type NivelAfectacion = NonNullable<TareaRespuesta['nivelAfectacion']>
export type EstadoTareaMop = NonNullable<TareaMopRespuesta['estado']>
export type TipoEvaluacion = NonNullable<AnalisisRespuesta['tipo']>
export type EstadoEvaluacion = NonNullable<AnalisisRespuesta['estado']>
export type TipoFallo = NonNullable<ConexionRespuesta['fallo']>
export type EstadoAutorizacion = NonNullable<DecisionAutorizacion['estado']>
export type EstadoValidacionFuncional = NonNullable<ValidacionFuncionalRespuesta['estado']>
export type ResultadoValidacionTecnica = NonNullable<ValidacionTecnicaRespuesta['resultado']>
export type EstadoIncidencia = NonNullable<IncidenciaRespuesta['estado']>
export type OrigenIncidencia = NonNullable<IncidenciaRespuesta['origen']>
export type TipoAlerta = NonNullable<AlertaRespuesta['tipo']>
export type EstadoComprobacion = NonNullable<Comprobacion['estado']>

export const DIAS_SEMANA: DiaSemana[] = ['LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO']

export interface Pagina<T> {
  contenido?: T[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}
