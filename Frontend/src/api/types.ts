/**
 * Generado desde el contrato OpenAPI del backend de KomainOS.
 * No editar a mano: se regenera con scripts/generar_tipos_ts.py.
 *
 * Si el panel necesita otra forma, el cambio va en el DTO del backend,
 * que es la fuente del contrato.
 */


export interface AccionesOrden {
  evaluar?: boolean
  decidir?: boolean
  proponerFecha?: boolean
  decidirPropuesta?: boolean
  validar?: boolean
  cerrarSinImplementacion?: boolean
  procesarCola?: boolean
}

export interface ActividadDiaria {
  fecha?: string
  altas?: number
  mantenimientos?: number
  incidencias?: number
}

export interface ActualizarExcepcionUmbralPeticion {
  valorAdvertencia?: number
  valorCritico?: number
  idListaReferencia?: number
  motivo?: string
  versionEsperada?: string
}

export interface ActualizarMetricaPeticion {
  nombre: string
  descripcion?: string
  unidad?: string
  tipoDato: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  esLista?: boolean
  soAplica?: 'LINUX' | 'WINDOWS'
}

export interface ActualizarUsuarioPeticion {
  nombreCompleto: string
  rol: 'ADMINISTRADOR' | 'OPERADOR' | 'RESPONSABLE'
  versionEsperada?: string
}

export interface AlertaRespuesta {
  id?: number
  tipo?: 'ADVERTENCIA_UMBRAL' | 'CONDICION_CRITICA' | 'SIN_IMPLEMENTACION' | 'SOLICITUD_AUTORIZACION' | 'AUTORIZACION_VENCIDA' | 'SOLICITUD_VALIDACION' | 'VALIDACION_VENCIDA' | 'INCIDENCIA_REGISTRADA' | 'VENTANA_EXCEDIDA' | 'CIERRE_MANTENIMIENTO'
  titulo?: string
  mensaje?: string
  idServidor?: number
  idGrupo?: number
  idOrden?: number
  idIncidencia?: number
  fecha?: string
  leida?: boolean
}

export interface AnalisisGrupoRespuesta {
  tipo?: 'REVISION_PERIODICA' | 'PREVIA' | 'POSTERIOR'
  idOrden?: number
  orden?: string
  fecha?: string
  analisis?: AnalisisRespuesta[]
}

export interface AnalisisImportacionRespuesta {
  formato?: string
  registros?: number
  nuevos?: number
  duplicados?: number
  erroneos?: number
  sobrescribibles?: number
  columnasReconocidas?: string[]
  columnasIgnoradas?: string[]
  filas?: FilaAnalisisRespuesta[]
}

export interface AnalisisRespuesta {
  id?: number
  idDetalle?: number
  idServidor?: number
  servidor?: string
  tipo?: 'REVISION_PERIODICA' | 'PREVIA' | 'POSTERIOR'
  estado?: 'EN_CURSO' | 'FINALIZADA' | 'FALLIDA'
  nivel?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  script?: string
  idVersion?: number
  numeroVersion?: number
  idCuentaServicio?: number
  cuenta?: string
  fechaInicio?: string
  fechaFin?: string
  error?: string
  mediciones?: MedicionRespuesta[]
}

export interface Aprobador {
  codigo?: string
  nombre?: string
}

export interface AsignacionServidoresPeticion {
  idsServidores: number[]
  finalidad: 'EVALUACION' | 'MANTENIMIENTO'
}

export interface AtenderIncidenciaPeticion {
  diagnostico: string
  acciones?: string
  resultado: string
  cerrar?: boolean
}

export interface AuditoriaRespuesta {
  id?: number
  fechaHora?: string
  idUsuario?: number
  codigoUsuario?: string
  actor?: string
  proceso?: string
  operacion?: string
  entidad?: string
  idEntidad?: number
  idServidor?: number
  idGrupo?: number
  idOrden?: number
  motivo?: string
  valorAnterior?: JsonNode
  valorNuevo?: JsonNode
}

export interface Autor {
  id?: number
  codigo?: string
  nombre?: string
}

export interface BajaPeticion {
  motivo: string
}

export interface Bitacora {
  objetivo?: string
  mantenimientos?: MantenimientoBitacora[]
  truncada?: boolean
}

export interface CambioEstado {
  estadoAnterior?: 'PROGRAMADA' | 'EN_EVALUACION' | 'SIN_IMPLEMENTACION' | 'PENDIENTE_AUTORIZACION' | 'AUTORIZADA' | 'EN_COLA' | 'EN_EJECUCION' | 'PENDIENTE_VALIDACION' | 'INCIDENCIA' | 'REPROGRAMADA' | 'RECHAZADA' | 'AUTORIZACION_VENCIDA' | 'VALIDACION_VENCIDA' | 'CANCELADA' | 'CERRADA'
  estadoNuevo?: 'PROGRAMADA' | 'EN_EVALUACION' | 'SIN_IMPLEMENTACION' | 'PENDIENTE_AUTORIZACION' | 'AUTORIZADA' | 'EN_COLA' | 'EN_EJECUCION' | 'PENDIENTE_VALIDACION' | 'INCIDENCIA' | 'REPROGRAMADA' | 'RECHAZADA' | 'AUTORIZACION_VENCIDA' | 'VALIDACION_VENCIDA' | 'CANCELADA' | 'CERRADA'
  motivo?: string
  usuario?: ReferenciaSimple
  fechaHora?: string
}

export interface CancelarOrdenPeticion {
  motivo: string
}

export interface ColaRespuesta {
  idDetalle?: number
  idOrden?: number
  orden?: string
  idServidor?: number
  servidor?: string
  prioridad?: 'NORMAL' | 'ALTA'
  estado?: 'PENDIENTE' | 'EN_COLA' | 'EN_EJECUCION' | 'FINALIZADO' | 'FALLIDO' | 'NO_INICIADO'
  posicion?: number
  piloto?: boolean
  inicioPrevisto?: string
  finPrevisto?: string
  inicioReal?: string
}

export interface ColumnaInventarioRespuesta {
  clave?: string
  etiqueta?: string
  importable?: boolean
  obligatoria?: boolean
}

export interface Comprobacion {
  codigo?: string
  titulo?: string
  estado?: 'OK' | 'INFO' | 'ADVERTENCIA' | 'ERROR'
  detalle?: string
}

export interface ConexionRespuesta {
  idServidor?: number
  servidor?: string
  idCuenta?: number
  cuenta?: string
  correcto?: boolean
  fallo?: 'COMUNICACION' | 'AUTENTICACION' | 'PERMISOS' | 'TIEMPO_EXCEDIDO' | 'EJECUCION' | 'CONTRATO' | 'CONFIGURACION'
  mensaje?: string
  administrador?: boolean
}

export interface ConfiguracionGrupoPeticion {
  frecuenciaRevisionDias?: number
  revisionPeriodicaHabilitada?: boolean
  frecuenciaMantenimientoDias?: number
  modalidadPlanificacion: 'AUTOMATICA' | 'BAJO_DEMANDA'
  modoEjecucion: 'SECUENCIAL' | 'PARALELO'
  idCuentaServicioEvaluacion?: number
  idCuentaServicioMantenimiento?: number
  versionEsperada?: string
}

export interface ConfiguracionPeticion {
  frecuenciaRevisionDias?: number
  revisionPeriodicaHabilitada?: boolean
  frecuenciaMantenimientoDias?: number
  modalidadPlanificacion: 'AUTOMATICA' | 'BAJO_DEMANDA'
  idCuentaServicioEvaluacion?: number
  idCuentaServicioMantenimiento?: number
  versionEsperada?: string
}

export interface ConfiguracionRespuesta {
  frecuenciaRevisionDias?: number
  revisionPeriodicaHabilitada?: boolean
  frecuenciaMantenimientoDias?: number
  modalidadPlanificacion?: 'AUTOMATICA' | 'BAJO_DEMANDA'
  modoEjecucion?: 'SECUENCIAL' | 'PARALELO'
  idCuentaServicioEvaluacion?: number
  idCuentaServicioMantenimiento?: number
  usaPredeterminadaEvaluacion?: boolean
  usaPredeterminadaMantenimiento?: boolean
  cuentaServicioEvaluacion?: ReferenciaSimple
  cuentaServicioMantenimiento?: ReferenciaSimple
  fechaCreacion?: string
  fechaActualizacion?: string
}

export interface Conteo {
  clave?: string
  cantidad?: number
}

export interface CrearUsuarioPeticion {
  codigo: string
  nombreCompleto: string
  contrasena: string
  rol: 'ADMINISTRADOR' | 'OPERADOR' | 'RESPONSABLE'
}

export interface CredencialPeticion {
  nombre: string
  usuarioAcceso: string
  descripcion?: string
  tipoAutenticacion: 'PASSWORD' | 'LLAVE_SSH'
  metodoElevacion?: 'NINGUNO' | 'SUDO' | 'SU' | 'RUNAS'
  secreto: string
  elevacionConContrasena?: boolean
  secretoElevacion?: string
}

export interface CredencialRespuesta {
  id?: number
  nombre?: string
  usuarioAcceso?: string
  descripcion?: string
  estado?: 'VIGENTE' | 'REVOCADA'
  tipoAutenticacion?: 'PASSWORD' | 'LLAVE_SSH'
  metodoElevacion?: 'NINGUNO' | 'SUDO' | 'SU' | 'RUNAS'
  conContrasenaElevacion?: boolean
  principal?: boolean
  fechaRegistro?: string
  fechaSecreto?: string
  fechaRevocacion?: string
}

export interface CriticidadResumen {
  id?: number
  nombre?: string
  prioridad?: number
}

export interface CuentaPredeterminadaPeticion {
  idCuentaServicio?: number
  finalidad: 'EVALUACION' | 'MANTENIMIENTO'
}

export interface CuentaServicioRespuesta {
  id?: number
  nombre?: string
  usuarioAcceso?: string
  descripcion?: string
  estado?: 'VIGENTE' | 'REVOCADA'
  tipoAutenticacion?: 'PASSWORD' | 'LLAVE_SSH'
  metodoElevacion?: 'NINGUNO' | 'SUDO' | 'SU' | 'RUNAS'
  conContrasenaElevacion?: boolean
  fechaRegistro?: string
  fechaSecreto?: string
  fechaRevocacion?: string
  servidores?: number
  grupos?: number
  predeterminadaEvaluacion?: boolean
  predeterminadaMantenimiento?: boolean
}

export interface DatosCredencialPeticion {
  nombre: string
  usuarioAcceso: string
  descripcion?: string
}

export interface DecidirAutorizacionPeticion {
  decision: 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'FECHA_PROPUESTA' | 'VENCIDA'
  motivo: string
  fechaPropuesta?: string
  confirmarFueraVentana?: boolean
}

export interface DecisionAutorizacion {
  id?: number
  tipo?: 'RESPONSABLE' | 'ADMINISTRADOR'
  secuencia?: number
  estado?: 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'FECHA_PROPUESTA' | 'VENCIDA'
  usuario?: string
  codigoUsuario?: string
  fechaSolicitud?: string
  fechaDecision?: string
  fechaPropuesta?: string
  motivo?: string
  esperados?: Aprobador[]
}

export interface DespachoRespuesta {
  despachados?: number
  mensaje?: string
}

export interface Detalle {
  id?: number
  servidor?: ReferenciaSimple
  direccionIp?: string
  posicionEjecucion?: number
  esServidorPiloto?: boolean
  estado?: 'PENDIENTE' | 'EN_COLA' | 'EN_EJECUCION' | 'FINALIZADO' | 'FALLIDO' | 'NO_INICIADO'
  fechaPrevistaInicio?: string
  fechaPrevistaFin?: string
  fechaRealInicio?: string
  fechaRealFin?: string
}

export interface Diagnostico {
  procesoHabilitado?: boolean
  intervaloSegundos?: number
  maxConexionesSimultaneas?: number
  controlador?: string
  controladorCompatible?: boolean
  ansibleDisponible?: boolean
  versionAnsible?: string
  ejecutable?: string
  transporte?: Transporte
  avisos?: string[]
  verificado?: string
}

export interface DireccionIpRespuesta {
  id?: number
  direccion?: string
  principal?: boolean
}

export interface EjecucionDetalleRespuesta {
  ejecucion?: EjecucionRespuesta
  salida?: string
  prioridad?: number
  duracionEstimadaMinutos?: number
}

export interface EjecucionDia {
  fecha?: string
  exito?: number
  advertencia?: number
  error?: number
}

export interface EjecucionRespuesta {
  id?: number
  idOrden?: number
  orden?: string
  idServidor?: number
  servidor?: string
  tarea?: string
  idScript?: number
  script?: string
  idVersion?: number
  numeroVersion?: number
  sistemaOperativo?: string
  cuenta?: string
  estado?: 'PENDIENTE' | 'EN_EJECUCION' | 'FINALIZADA' | 'FALLIDA' | 'NO_INICIADA'
  codigoRetorno?: number
  inicio?: string
  fin?: string
  duracionSegundos?: number
  error?: string
}

export interface EntornoPeticion {
  nombre: string
  descripcion?: string
}

export interface EntornoRespuesta {
  id?: number
  nombre?: string
  descripcion?: string
  activo?: boolean
}

export interface ErrorCampo {
  campo?: string
  mensaje?: string
}

export interface ErrorRespuesta {
  marcaTiempo?: string
  estado?: number
  codigo?: string
  mensaje?: string
  ruta?: string
  errores?: ErrorCampo[]
}

export interface EstadoInventario {
  estado?: string
  cantidad?: number
}

export interface EstadoVersionPeticion {
  habilitada?: boolean
}

export interface ExcepcionAplicadaRespuesta {
  id?: number
  alcance?: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD' | 'SISTEMA_OPERATIVO'
  objetivo?: ReferenciaSimple
  motivo?: string
}

export interface ExcepcionUmbralPeticion {
  alcance: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD' | 'SISTEMA_OPERATIVO'
  idObjetivo: number
  valorAdvertencia?: number
  valorCritico?: number
  idListaReferencia?: number
  motivo?: string
}

export interface ExcepcionUmbralRespuesta {
  id?: number
  alcance?: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD' | 'SISTEMA_OPERATIVO'
  objetivo?: ReferenciaSimple
  valorAdvertencia?: number
  valorCritico?: number
  lista?: ListaResumenRespuesta
  motivo?: string
  activo?: boolean
  fechaActualizacion?: string
}

export interface FichaAutomatizacionRespuesta {
  idOrden?: number
  orden?: OrdenResumenRespuesta
  siguientePaso?: string
  acciones?: AccionesOrden
  mops?: PlanMop[]
  resumenPlan?: ResumenPlan
  evaluaciones?: AnalisisRespuesta[]
  autorizaciones?: RondaAutorizacion[]
  validacionFuncional?: ValidacionFuncionalRespuesta
  validacionesTecnicas?: ValidacionTecnicaRespuesta[]
  incidencias?: IncidenciaRespuesta[]
  reporte?: ReporteServidor[]
  historial?: CambioEstado[]
}

export interface FichaGrupoRespuesta {
  id?: number
  nombre?: string
  descripcion?: string
  estado?: 'PENDIENTE_DE_CONFIGURACION' | 'ACTIVO' | 'INACTIVO'
  criticidad?: CriticidadResumen
  entorno?: ReferenciaSimple
  responsable?: ReferenciaSimple
  sistemaOperativo?: ReferenciaSimple
  configuracion?: ConfiguracionRespuesta
  integrantes?: ServidorResumenRespuesta[]
  ventanaEfectiva?: VentanaRespuesta[]
  fechaCreacion?: string
  fechaActualizacion?: string
}

export interface FichaServidorRespuesta {
  id?: number
  hostname?: string
  direccionIp?: string
  direccionesIp?: DireccionIpRespuesta[]
  vdc?: string
  servidorFisico?: string
  vlan?: string
  cluster?: string
  dns?: string
  plataforma?: string
  descripcion?: string
  cantidadCpu?: number
  ramGb?: number
  hdVirtualGb?: number
  versionSistemaOperativo?: ReferenciaSimple
  sistemaOperativo?: ReferenciaSimple
  familiaSistemaOperativo?: 'LINUX' | 'WINDOWS'
  entorno?: ReferenciaSimple
  criticidad?: CriticidadResumen
  responsable?: ReferenciaSimple
  estado?: 'PENDIENTE_DE_CONFIGURACION' | 'ACTIVO' | 'DADO_DE_BAJA'
  fechaAlta?: string
  fechaActualizacion?: string
  configuracion?: ConfiguracionRespuesta
  ventanas?: VentanaRespuesta[]
  grupos?: ReferenciaSimple[]
  bajaPendiente?: SolicitudBajaRespuesta
  historialBajas?: SolicitudBajaRespuesta[]
  reactivaciones?: ReactivacionRespuesta[]
}

export interface FilaAnalisisRespuesta {
  fila?: number
  estado?: 'NUEVA' | 'DUPLICADA' | 'ERRONEA'
  hostname?: string
  direccionIp?: string
  motivos?: string[]
  servidorExistente?: ReferenciaSimple
  sobrescribible?: boolean
  camposModificados?: string[]
  idNivelCriticidad?: number
  idVersionSistemaOperativo?: number
  versionServidor?: string
}

export interface FilaComparativa {
  codigo?: string
  nombre?: string
  unidad?: string
  instancia?: string
  antes?: string
  despues?: string
  nivelAntes?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  nivelDespues?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
}

export interface FilaImportacionRespuesta {
  fila?: number
  hostname?: string
  resultado?: 'CREADO' | 'ACTUALIZADO' | 'OMITIDO' | 'RECHAZADO'
  idServidor?: number
  detalle?: string
}

export interface GrupoPeticion {
  nombre: string
  descripcion?: string
  versionEsperada?: string
}

export interface GrupoResumenRespuesta {
  id?: number
  nombre?: string
  descripcion?: string
  estado?: 'PENDIENTE_DE_CONFIGURACION' | 'ACTIVO' | 'INACTIVO'
  cantidadIntegrantes?: number
  criticidad?: CriticidadResumen
  entorno?: ReferenciaSimple
  responsable?: ReferenciaSimple
  sistemaOperativo?: ReferenciaSimple
  modalidadPlanificacion?: 'AUTOMATICA' | 'BAJO_DEMANDA'
  modoEjecucion?: 'SECUENCIAL' | 'PARALELO'
}

export interface ImplementacionPeticion {
  idVersionSistemaOperativo: number
  contenido: string
  notasCambio?: string
  parametros: ParametroPeticion[]
}

export interface ImplementacionRespuesta {
  id?: number
  idVersionSistemaOperativo?: number
  sistemaOperativo?: string
  familia?: 'LINUX' | 'WINDOWS'
  lenguaje?: string
  versionVigente?: number
  versiones?: VersionRespuesta[]
}

export interface ImplementacionResumen {
  id?: number
  idVersionSistemaOperativo?: number
  sistemaOperativo?: string
  familia?: 'LINUX' | 'WINDOWS'
  versionVigente?: number
  totalVersiones?: number
  fechaUltimaVersion?: string
}

export interface IncidenciaRespuesta {
  id?: number
  idOrden?: number
  orden?: string
  idServidor?: number
  servidor?: string
  origen?: 'PRUEBA_PILOTO' | 'FALLO_EJECUCION' | 'TIEMPO_EXCEDIDO' | 'VALIDACION_TECNICA_NO_CONFORME' | 'RECHAZO_FUNCIONAL'
  estado?: 'ABIERTA' | 'EN_ATENCION' | 'CERRADA'
  error?: string
  diagnostico?: string
  acciones?: string
  resultado?: string
  atendidaPor?: string
  fechaApertura?: string
  fechaCierre?: string
}

export interface IncidenciasOrigen {
  origen?: string
  abiertas?: number
  cerradas?: number
}

export interface IndicadorKpi {
  codigo?: string
  nombre?: string
  unidad?: string
  numerador?: number
  denominador?: number
  valor?: number
  meta?: number
}

export interface InformeKpi {
  desde?: string
  hasta?: string
  fechaActualizacion?: string
  indicadores?: IndicadorKpi[]
  inventario?: EstadoInventario[]
  actividadDiaria?: ActividadDiaria[]
}

export interface IntegrantesPeticion {
  idsServidores: number[]
}

export type JsonNode = Record<string, unknown>

export interface LanzamientoInmediatoPeticion {
  idServidor?: number
  idGrupo?: number
  confirmarFueraVentana?: boolean
  motivo: string
}

export interface ListaReferenciaPeticion {
  nombre: string
  tipoDato: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  ignoraMayusculas?: boolean
  elementos: string[]
  versionEsperada?: string
}

export interface ListaReferenciaRespuesta {
  id?: number
  nombre?: string
  tipoDato?: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  ignoraMayusculas?: boolean
  activo?: boolean
  elementos?: string[]
  umbrales?: number
  excepciones?: number
  referenciasScript?: number
  fechaActualizacion?: string
}

export interface ListaResumenRespuesta {
  id?: number
  nombre?: string
  tipoDato?: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  activo?: boolean
  elementos?: string[]
}

export interface LoginPeticion {
  codigo: string
  contrasena: string
}

export interface MantenimientoBitacora {
  idOrden?: number
  codigo?: string
  grupal?: boolean
  grupo?: string
  origen?: 'PLANIFICACION_AUTOMATICA' | 'SOLICITUD_BAJO_DEMANDA' | 'LANZAMIENTO_INMEDIATO' | 'CONDICION_CRITICA'
  prioridad?: 'NORMAL' | 'ALTA'
  estado?: 'PROGRAMADA' | 'EN_EVALUACION' | 'SIN_IMPLEMENTACION' | 'PENDIENTE_AUTORIZACION' | 'AUTORIZADA' | 'EN_COLA' | 'EN_EJECUCION' | 'PENDIENTE_VALIDACION' | 'INCIDENCIA' | 'REPROGRAMADA' | 'RECHAZADA' | 'AUTORIZACION_VENCIDA' | 'VALIDACION_VENCIDA' | 'CANCELADA' | 'CERRADA'
  etapa?: 'PLANIFICACION' | 'EVALUACION' | 'AUTORIZACION' | 'EJECUCION' | 'VALIDACION' | 'INCIDENCIA' | 'CIERRE'
  resultado?: 'PROGRAMADO' | 'EN_CURSO' | 'EXITOSO' | 'SIN_INTERVENCION' | 'CON_INCIDENCIA' | 'REQUIERE_ATENCION' | 'NO_AUTORIZADO' | 'CANCELADO'
  inicioProgramado?: string
  finProgramado?: string
  inicioReal?: string
  finReal?: string
  fechaCierre?: string
  validacionFuncional?: 'PENDIENTE' | 'CONFORME' | 'RECHAZADA' | 'VENCIDA'
  servidores?: ParticipacionServidor[]
}

export interface MantenimientoRegistroPeticion {
  configuracion: ConfiguracionPeticion
  ventanas: VentanaPeticion[]
}

export interface MantenimientosMes {
  mes?: string
  finalizados?: number
  fallidos?: number
}

export interface MedicionRespuesta {
  codigo?: string
  nombre?: string
  unidad?: string
  instancia?: string
  valor?: number
  elementos?: string[]
  nivel?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  umbrales?: UmbralEvaluadoRespuesta[]
}

export interface MetricaPeticion {
  codigo: string
  nombre: string
  descripcion?: string
  unidad?: string
  tipoDato: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  esLista?: boolean
  soAplica?: 'LINUX' | 'WINDOWS'
}

export interface MetricaRespuesta {
  id?: number
  codigo?: string
  nombre?: string
  descripcion?: string
  unidad?: string
  tipoDato?: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  esLista?: boolean
  soAplica?: 'LINUX' | 'WINDOWS'
  activo?: boolean
}

export interface MetricaRevisada {
  codigo?: string
  instancia?: string
  valor?: number
  elementos?: string[]
  reconocida?: boolean
  nombre?: string
  unidad?: string
}

export interface MotivoPeticion {
  motivo: string
}

export interface NivelCriticidadPeticion {
  nombre: string
  prioridad: number
  frecuenciaRevisionDias: number
  frecuenciaMantenimientoDias: number
  plazoAutorizacionHoras: number
  plazoValidacionHoras: number
}

export interface NivelCriticidadRespuesta {
  id?: number
  nombre?: string
  prioridad?: number
  frecuenciaRevisionDias?: number
  frecuenciaMantenimientoDias?: number
  plazoAutorizacionHoras?: number
  plazoValidacionHoras?: number
  activo?: boolean
}

export interface Opciones {
  usuarios?: Autor[]
  operaciones?: string[]
  entidades?: string[]
}

export interface OrdenDetalleRespuesta {
  resumen?: OrdenResumenRespuesta
  solicitante?: ReferenciaSimple
  modoEjecucionAplicado?: 'SECUENCIAL' | 'PARALELO'
  cuentaServicioEvaluacion?: ReferenciaSimple
  cuentaServicioMantenimiento?: ReferenciaSimple
  inicioVentanaAplicada?: string
  finVentanaAplicada?: string
  detalles?: Detalle[]
  historial?: CambioEstado[]
}

export interface OrdenResumenRespuesta {
  id?: number
  codigo?: string
  tipoObjetivo?: string
  objetivo?: ReferenciaSimple
  cantidadServidores?: number
  criticidad?: CriticidadResumen
  origen?: 'PLANIFICACION_AUTOMATICA' | 'SOLICITUD_BAJO_DEMANDA' | 'LANZAMIENTO_INMEDIATO' | 'CONDICION_CRITICA'
  prioridad?: 'NORMAL' | 'ALTA'
  estado?: 'PROGRAMADA' | 'EN_EVALUACION' | 'SIN_IMPLEMENTACION' | 'PENDIENTE_AUTORIZACION' | 'AUTORIZADA' | 'EN_COLA' | 'EN_EJECUCION' | 'PENDIENTE_VALIDACION' | 'INCIDENCIA' | 'REPROGRAMADA' | 'RECHAZADA' | 'AUTORIZACION_VENCIDA' | 'VALIDACION_VENCIDA' | 'CANCELADA' | 'CERRADA'
  etapa?: 'PLANIFICACION' | 'EVALUACION' | 'AUTORIZACION' | 'EJECUCION' | 'VALIDACION' | 'INCIDENCIA' | 'CIERRE'
  fechaObjetivo?: string
  inicioProgramado?: string
  finProgramado?: string
  fechaEvaluacion?: string
  fechaCreacion?: string
}

export interface Pageable {
  page?: number
  size?: number
  sort?: string[]
}

export interface PaginaRespuestaAlertaRespuesta {
  contenido?: AlertaRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaAnalisisRespuesta {
  contenido?: AnalisisRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaAuditoriaRespuesta {
  contenido?: AuditoriaRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaColaRespuesta {
  contenido?: ColaRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaEjecucionRespuesta {
  contenido?: EjecucionRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaIncidenciaRespuesta {
  contenido?: IncidenciaRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaOrdenResumenRespuesta {
  contenido?: OrdenResumenRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaPoliticaCambioRespuesta {
  contenido?: PoliticaCambioRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaScriptResumenRespuesta {
  contenido?: ScriptResumenRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaServidorResumenRespuesta {
  contenido?: ServidorResumenRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaTareaRespuesta {
  contenido?: TareaRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaUsoVersionRespuesta {
  contenido?: UsoVersionRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaUsuarioRespuesta {
  contenido?: UsuarioRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface ParametroPeticion {
  nombre: string
  tipo: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'LISTA'
  valorPorDefecto?: string
  idListaReferencia?: number
}

export interface ParametroRespuesta {
  nombre?: string
  tipo?: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'LISTA'
  valorPorDefecto?: string
  idListaReferencia?: number
  listaReferencia?: ReferenciaSimple
}

export interface ParametrosSistemaPeticion {
  maxEjecucionesConcurrentes: number
  maxDuracionTareaMinutos: number
  maxDuracionMopMinutos: number
  minCiclosRachaEstable: number
  minutosExpiracionToken: number
  versionEsperada?: string
}

export interface ParametrosSistemaRespuesta {
  maxEjecucionesConcurrentes?: number
  maxDuracionTareaMinutos?: number
  maxDuracionMopMinutos?: number
  minCiclosRachaEstable?: number
  minutosExpiracionToken?: number
  fechaActualizacion?: string
  cuentaServicioEvaluacionPredeterminada?: ReferenciaSimple
  cuentaServicioMantenimientoPredeterminada?: ReferenciaSimple
}

export interface ParticipacionServidor {
  idServidor?: number
  servidor?: string
  estado?: 'PENDIENTE' | 'EN_COLA' | 'EN_EJECUCION' | 'FINALIZADO' | 'FALLIDO' | 'NO_INICIADO'
  piloto?: boolean
  nivelAntes?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  nivelDespues?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  validacionTecnica?: 'CONFORME' | 'NO_CONFORME'
  incidencias?: number
  resultado?: 'PROGRAMADO' | 'EN_CURSO' | 'EXITOSO' | 'SIN_INTERVENCION' | 'CON_INCIDENCIA' | 'REQUIERE_ATENCION' | 'NO_AUTORIZADO' | 'CANCELADO'
  inicioReal?: string
  finReal?: string
  tareas?: TareaBitacora[]
}

export interface PerfilRespuesta {
  id?: number
  codigo?: string
  nombreCompleto?: string
  rol?: 'ADMINISTRADOR' | 'OPERADOR' | 'RESPONSABLE'
  activo?: boolean
  fechaCreacion?: string
  fechaActualizacion?: string
  servidoresACargo?: number
  minutosSesion?: number
}

export interface PlanMop {
  id?: number
  idDetalle?: number
  idServidor?: number
  servidor?: string
  estadoServidor?: 'PENDIENTE' | 'EN_COLA' | 'EN_EJECUCION' | 'FINALIZADO' | 'FALLIDO' | 'NO_INICIADO'
  piloto?: boolean
  duracionMinutos?: number
  maximoMinutos?: number
  superaDuracionMaxima?: boolean
  afectacion?: 'SIN_AFECTACION' | 'PARCIAL' | 'TOTAL'
  inicioReal?: string
  finReal?: string
  tareas?: TareaMopRespuesta[]
}

export interface PoliticaCambioPeticion {
  idEntorno: number
  nivelAfectacion: 'SIN_AFECTACION' | 'PARCIAL' | 'TOTAL'
  requiereResponsable?: boolean
  requiereAdministrador?: boolean
  activo?: boolean
  notificaciones: ('RESPONSABLE' | 'ADMINISTRADOR' | 'OPERADOR')[]
  versionEsperada?: string
}

export interface PoliticaCambioRespuesta {
  id?: number
  idEntorno?: number
  entorno?: string
  nivelAfectacion?: 'SIN_AFECTACION' | 'PARCIAL' | 'TOTAL'
  requiereResponsable?: boolean
  requiereAdministrador?: boolean
  activo?: boolean
  fechaActualizacion?: string
  notificaciones?: ('RESPONSABLE' | 'ADMINISTRADOR' | 'OPERADOR')[]
}

export interface Preparacion {
  idServidor?: number
  servidor?: string
  listo?: boolean
  comprobaciones?: Comprobacion[]
}

export interface PreparacionImportacionPeticion {
  fila?: number
  idServidorExistente?: number
  versionServidor?: string
  mantenimiento: MantenimientoRegistroPeticion
}

export interface PreparacionesImportacionPeticion {
  servidores: PreparacionImportacionPeticion[]
}

export interface ProbarAnalisisPeticion {
  idServidor: number
}

export interface ProgramarOrdenPeticion {
  idServidor?: number
  idGrupo?: number
  inicio?: string
  motivo?: string
}

export interface PropuestaRespuesta {
  fechaObjetivo?: string
  inicio?: string
  fin?: string
  fechaEvaluacion?: string
  inicioVentana?: string
  finVentana?: string
  tramos?: Tramo[]
}

export interface PruebaAnalisisRespuesta {
  correcto?: boolean
  fallo?: 'COMUNICACION' | 'AUTENTICACION' | 'PERMISOS' | 'TIEMPO_EXCEDIDO' | 'EJECUCION' | 'CONTRATO' | 'CONFIGURACION'
  error?: string
  salida?: string
  revision?: RevisionSalidaRespuesta
  advertencia?: string
}

export interface PruebaUmbralRespuesta {
  nivel?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  origen?: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD' | 'SISTEMA_OPERATIVO' | 'GLOBAL'
  excepcion?: ExcepcionAplicadaRespuesta
  aplicado?: ValoresUmbralRespuesta
  valor?: number
  faltantes?: string[]
  sobrantes?: string[]
}

export interface ReactivacionRespuesta {
  fecha?: string
  usuario?: ReferenciaSimple
}

export interface ReautenticacionPeticion {
  contrasena: string
}

export interface ReferenciaSimple {
  id?: number
  nombre?: string
}

export interface RegistroServidorPeticion {
  servidor: ServidorPeticion
  mantenimiento: MantenimientoRegistroPeticion
}

export interface ReporteServidor {
  idServidor?: number
  servidor?: string
  metricas?: FilaComparativa[]
  tareasFinalizadas?: number
  tareasFallidas?: number
  tareasNoIniciadas?: number
  errores?: string[]
  advertencias?: string[]
}

export interface ReprogramarOrdenPeticion {
  inicio?: string
  motivo: string
}

export interface ResultadoAsignacionRespuesta {
  asignados?: number
  mensaje?: string
}

export interface ResultadoBajaRespuesta {
  aplicada?: boolean
  ordenesRetiradas?: number
  mensaje?: string
  solicitud?: SolicitudBajaRespuesta
  servidor?: FichaServidorRespuesta
}

export interface ResultadoImportacionRespuesta {
  creados?: number
  actualizados?: number
  omitidos?: number
  rechazados?: number
  filas?: FilaImportacionRespuesta[]
}

export interface ResultadoMes {
  mes?: string
  exitosos?: number
  sinIntervencion?: number
  conIncidencia?: number
  noRealizados?: number
}

export interface ResumenGraficos {
  fechaActualizacion?: string
  desde?: string
  hasta?: string
  ejecuciones?: EjecucionDia[]
  mantenimientosPorMes?: ResultadoMes[]
  ordenesPorEstado?: Conteo[]
  incidenciasPorOrigen?: IncidenciasOrigen[]
  saludServidores?: SaludServidores
  servidoresPorMes?: ServidoresMes[]
  mantenimientosEjecutadosPorMes?: MantenimientosMes[]
}

export interface ResumenPlan {
  servidores?: number
  servidoresConTareas?: number
  tareas?: number
  duracionTotalMinutos?: number
  modoEjecucion?: 'SECUENCIAL' | 'PARALELO'
}

export interface ResumenPlanificacionRespuesta {
  objetivosEvaluados?: number
  ordenesGeneradas?: string[]
  sinIntervalo?: string[]
}

export interface ResumenProcesoRespuesta {
  ejecutado?: boolean
  evaluadas?: number
  revisadas?: number
  gruposRevisados?: number
  despachadas?: number
  vencidas?: number
  mensajes?: string[]
}

export interface RevisarSalidaPeticion {
  salida: string
  familia?: 'LINUX' | 'WINDOWS'
}

export interface RevisionSalidaRespuesta {
  valida?: boolean
  errores?: string[]
  metricas?: MetricaRevisada[]
  faltantes?: string[]
}

export interface RevocacionPeticion {
  motivo?: string
}

export interface RondaAutorizacion {
  id?: number
  ronda?: number
  fechaLimite?: string
  entorno?: string
  afectacion?: 'SIN_AFECTACION' | 'PARCIAL' | 'TOTAL'
  automatica?: boolean
  decisiones?: DecisionAutorizacion[]
}

export interface SaludServidores {
  normal?: number
  advertencia?: number
  critico?: number
  sinAnalisis?: number
}

export interface ScriptPeticion {
  nombre: string
  tipo: 'ANALISIS' | 'MANTENIMIENTO'
  descripcion?: string
  activo?: boolean
}

export interface ScriptPreparadoRespuesta {
  nombreArchivo?: string
  lenguaje?: string
  contenido?: string
  lineaInicioParametros?: number
  lineaFinParametros?: number
  problemas?: string[]
}

export interface ScriptRespuesta {
  id?: number
  nombre?: string
  tipo?: 'ANALISIS' | 'MANTENIMIENTO'
  descripcion?: string
  activo?: boolean
  creadoPor?: string
  tareasActivas?: number
  implementaciones?: ImplementacionRespuesta[]
}

export interface ScriptResumenRespuesta {
  id?: number
  nombre?: string
  tipo?: 'ANALISIS' | 'MANTENIMIENTO'
  descripcion?: string
  activo?: boolean
  creadoPor?: string
  implementaciones?: ImplementacionResumen[]
}

export interface SecretoPeticion {
  tipoAutenticacion: 'PASSWORD' | 'LLAVE_SSH'
  metodoElevacion?: 'NINGUNO' | 'SUDO' | 'SU' | 'RUNAS'
  secreto?: string
  elevacionConContrasena?: boolean
  secretoElevacion?: string
}

export interface SecretoReveladoRespuesta {
  id?: number
  nombre?: string
  usuarioAcceso?: string
  tipoAutenticacion?: 'PASSWORD' | 'LLAVE_SSH'
  metodoElevacion?: 'NINGUNO' | 'SUDO' | 'SU' | 'RUNAS'
  fechaSecreto?: string
  secreto?: string
  secretoElevacion?: string
  segundosVisible?: number
}

export interface ServidorAsignableRespuesta {
  idServidor?: number
  hostname?: string
  direccionIp?: string
  familia?: 'LINUX' | 'WINDOWS'
  idCuentaServicioEvaluacion?: number
  idCuentaServicioMantenimiento?: number
}

export interface ServidorPeticion {
  hostname: string
  direccionIp: string
  direccionesIpAdicionales?: string[]
  vdc?: string
  servidorFisico?: string
  vlan?: string
  cluster?: string
  dns?: string
  idVersionSistemaOperativo: number
  plataforma?: string
  idEntorno: number
  idNivelCriticidad: number
  idResponsable: number
  descripcion?: string
  cantidadCpu?: number
  ramGb?: number
  hdVirtualGb?: number
  versionEsperada?: string
}

export interface ServidorResumenRespuesta {
  id?: number
  hostname?: string
  direccionIp?: string
  cantidadDireccionesIp?: number
  vdc?: string
  servidorFisico?: string
  vlan?: string
  cluster?: string
  dns?: string
  plataforma?: string
  descripcion?: string
  cantidadCpu?: number
  ramGb?: number
  hdVirtualGb?: number
  sistemaOperativo?: ReferenciaSimple
  versionSistemaOperativo?: ReferenciaSimple
  familiaSistemaOperativo?: 'LINUX' | 'WINDOWS'
  entorno?: ReferenciaSimple
  criticidad?: CriticidadResumen
  responsable?: ReferenciaSimple
  estado?: 'PENDIENTE_DE_CONFIGURACION' | 'ACTIVO' | 'DADO_DE_BAJA'
  fechaAlta?: string
  fechaActualizacion?: string
}

export interface ServidoresMes {
  mes?: string
  registrados?: number
  altas?: number
}

export interface SistemaOperativoPeticion {
  nombre: string
  familia: 'LINUX' | 'WINDOWS'
  activo?: boolean
}

export interface SistemaOperativoRespuesta {
  id?: number
  nombre?: string
  familia?: 'LINUX' | 'WINDOWS'
  canalRemoto?: string
  activo?: boolean
  versiones?: Version[]
}

export interface SolicitudBajaRespuesta {
  id?: number
  estado?: 'PENDIENTE' | 'APLICADA'
  motivo?: string
  solicitante?: ReferenciaSimple
  fechaSolicitud?: string
  fechaAplicacion?: string
}

export interface TareaBitacora {
  nombre?: string
  estado?: 'PENDIENTE' | 'EN_EJECUCION' | 'FINALIZADA' | 'FALLIDA' | 'NO_INICIADA'
  duracionSegundos?: number
}

export interface TareaMopRespuesta {
  id?: number
  orden?: number
  idTarea?: number
  tarea?: string
  script?: string
  idVersion?: number
  numeroVersion?: number
  prioridad?: number
  duracionMinutos?: number
  afectacion?: 'SIN_AFECTACION' | 'PARCIAL' | 'TOTAL'
  idCuentaServicio?: number
  cuenta?: string
  estado?: 'PENDIENTE' | 'EN_EJECUCION' | 'FINALIZADA' | 'FALLIDA' | 'NO_INICIADA'
  codigoRetorno?: number
  salida?: string
  error?: string
  fechaInicio?: string
  fechaFin?: string
  duracionSegundos?: number
}

export interface TareaPeticion {
  idScript: number
  nombre: string
  descripcion?: string
  nivelAfectacion: 'SIN_AFECTACION' | 'PARCIAL' | 'TOTAL'
  prioridad: number
  duracionEstimadaMinutos: number
  activo?: boolean
  idsUmbrales: number[]
}

export interface TareaRespuesta {
  id?: number
  idScript?: number
  script?: string
  scriptActivo?: boolean
  nombre?: string
  descripcion?: string
  nivelAfectacion?: 'SIN_AFECTACION' | 'PARCIAL' | 'TOTAL'
  prioridad?: number
  duracionEstimadaMinutos?: number
  activo?: boolean
  idsUmbrales?: number[]
  sistemasCompatibles?: string[]
}

export interface TokenRespuesta {
  token?: string
  tipo?: string
  expiraEnMinutos?: number
  usuario?: UsuarioSesion
}

export interface Tramo {
  idServidor?: number
  posicion?: number
  inicio?: string
  fin?: string
}

export interface Transporte {
  puertoSsh?: number
  verificarClaveHost?: boolean
  puertoWinrm?: number
  esquemaWinrm?: string
  transporteWinrm?: string
  validarCertificadoWinrm?: boolean
  timeoutConexionSegundos?: number
}

export interface UmbralEnGrupoRespuesta {
  grupo?: ReferenciaSimple
  excepcion?: ExcepcionAplicadaRespuesta
  valores?: ValoresUmbralRespuesta
}

export interface UmbralEvaluadoRespuesta {
  idUmbral?: number
  idExcepcion?: number
  nivel?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  operador?: string
  advertencia?: number
  critico?: number
  idLista?: number
  lista?: string
  nivelFaltante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  nivelSobrante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
}

export interface UmbralPeticion {
  idMetrica: number
  nombre: string
  operador?: 'MAYOR' | 'MAYOR_IGUAL' | 'MENOR' | 'MENOR_IGUAL' | 'IGUAL' | 'DISTINTO'
  valorAdvertencia?: number
  valorCritico?: number
  idListaReferencia?: number
  nivelFaltante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  nivelSobrante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  versionEsperada?: string
}

export interface UmbralRespuesta {
  id?: number
  nombre?: string
  metrica?: MetricaRespuesta
  activo?: boolean
  operador?: 'MAYOR' | 'MAYOR_IGUAL' | 'MENOR' | 'MENOR_IGUAL' | 'IGUAL' | 'DISTINTO'
  valorAdvertencia?: number
  valorCritico?: number
  lista?: ListaResumenRespuesta
  nivelFaltante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  nivelSobrante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  cantidadExcepciones?: number
  excepcionesActivas?: number
  excepciones?: ExcepcionUmbralRespuesta[]
  fechaActualizacion?: string
}

export interface UmbralServidorRespuesta {
  umbral?: ReferenciaSimple
  metrica?: MetricaRespuesta
  origen?: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD' | 'SISTEMA_OPERATIVO' | 'GLOBAL'
  excepcion?: ExcepcionAplicadaRespuesta
  aplicado?: ValoresUmbralRespuesta
  global?: ValoresUmbralRespuesta
  enGrupos?: UmbralEnGrupoRespuesta[]
}

export interface UsoVersionRespuesta {
  idTareaMop?: number
  idOrden?: number
  orden?: string
  servidor?: string
  tarea?: string
  estado?: 'PENDIENTE' | 'EN_EJECUCION' | 'FINALIZADA' | 'FALLIDA' | 'NO_INICIADA'
  inicio?: string
  fin?: string
}

export interface UsuarioRespuesta {
  id?: number
  codigo?: string
  nombreCompleto?: string
  rol?: 'ADMINISTRADOR' | 'OPERADOR' | 'RESPONSABLE'
  activo?: boolean
  fechaCreacion?: string
  fechaActualizacion?: string
}

export interface UsuarioSesion {
  id?: number
  codigo?: string
  nombreCompleto?: string
  rol?: 'ADMINISTRADOR' | 'OPERADOR' | 'RESPONSABLE'
}

export interface ValidacionFuncionalRespuesta {
  id?: number
  idResponsable?: number
  responsable?: string
  estado?: 'PENDIENTE' | 'CONFORME' | 'RECHAZADA' | 'VENCIDA'
  fechaSolicitud?: string
  fechaLimite?: string
  fechaRespuesta?: string
  observaciones?: string
}

export interface ValidacionTecnicaRespuesta {
  idDetalle?: number
  servidor?: string
  resultado?: 'CONFORME' | 'NO_CONFORME'
  observaciones?: string
  fecha?: string
}

export interface ValidarOrdenPeticion {
  conforme?: boolean
  observaciones: string
}

export interface ValoresUmbralRespuesta {
  operador?: 'MAYOR' | 'MAYOR_IGUAL' | 'MENOR' | 'MENOR_IGUAL' | 'IGUAL' | 'DISTINTO'
  valorAdvertencia?: number
  valorCritico?: number
  lista?: ListaResumenRespuesta
  nivelFaltante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  nivelSobrante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
}

export interface VariableAnsibleRespuesta {
  nombre?: string
  valor?: string
}

export interface VentanaPeticion {
  diaInicio: 'LUNES' | 'MARTES' | 'MIERCOLES' | 'JUEVES' | 'VIERNES' | 'SABADO' | 'DOMINGO'
  horaInicio: string
  diaFin: 'LUNES' | 'MARTES' | 'MIERCOLES' | 'JUEVES' | 'VIERNES' | 'SABADO' | 'DOMINGO'
  horaFin: string
}

export interface VentanaRespuesta {
  diaInicio?: 'LUNES' | 'MARTES' | 'MIERCOLES' | 'JUEVES' | 'VIERNES' | 'SABADO' | 'DOMINGO'
  horaInicio?: string
  diaFin?: 'LUNES' | 'MARTES' | 'MIERCOLES' | 'JUEVES' | 'VIERNES' | 'SABADO' | 'DOMINGO'
  horaFin?: string
  duracionMinutos?: number
}

export interface VentanasPeticion {
  ventanas: VentanaPeticion[]
}

export interface Version {
  id?: number
  version?: string
  activo?: boolean
}

export interface VersionRespuesta {
  id?: number
  numero?: number
  contenido?: string
  notasCambio?: string
  habilitada?: boolean
  vigente?: boolean
  fechaCreacion?: string
  parametros?: ParametroRespuesta[]
}

export interface VersionScriptPeticion {
  contenido: string
  notasCambio?: string
  parametros: ParametroPeticion[]
}

export interface VersionSistemaOperativoPeticion {
  version: string
  activo?: boolean
}

export interface VistaAnsiblePeticion {
  usuarioAcceso?: string
  tipoAutenticacion: 'PASSWORD' | 'LLAVE_SSH'
  metodoElevacion?: 'NINGUNO' | 'SUDO' | 'SU' | 'RUNAS'
  elevacionConContrasena?: boolean
  familia?: 'LINUX' | 'WINDOWS'
}

export interface VistaAnsibleRespuesta {
  familia?: 'LINUX' | 'WINDOWS'
  cuenta?: VariableAnsibleRespuesta[]
  controlador?: VariableAnsibleRespuesta[]
  servidor?: VariableAnsibleRespuesta[]
}

export interface VistaPreviaPeticion {
  familia: 'LINUX' | 'WINDOWS'
  contenido?: string
  parametros?: ParametroPeticion[]
}
