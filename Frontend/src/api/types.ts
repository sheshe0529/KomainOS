/**
 * Generado desde el contrato OpenAPI del backend de KomainOS.
 * No editar a mano: se regenera con scripts/generar_tipos_ts.py.
 *
 * Si el panel necesita otra forma, el cambio va en el DTO del backend,
 * que es la fuente del contrato.
 */


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
  unidad?: string
  tipoDato: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  esLista?: boolean
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
  idOrden?: number
  idIncidencia?: number
  fecha?: string
  leida?: boolean
  idGrupo?: number
}

export interface Analisis {
  id?: number
  idDetalle?: number
  idServidor?: number
  servidor?: string
  tipo?: 'REVISION_PERIODICA' | 'PREVIA' | 'POSTERIOR'
  estado?: 'EN_CURSO' | 'FINALIZADA' | 'FALLIDA'
  idVersion?: number
  idCuentaServicio?: number
  fechaInicio?: string
  fechaFin?: string
  error?: string
  mediciones?: Medicion[]
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

export interface AsignacionServidoresPeticion {
  idsServidores: number[]
}

export interface AtenderPeticion {
  diagnostico: string
  acciones: string
  resultado: string
  cerrar?: boolean
}

export interface AutomatizacionOrdenRespuesta {
  idOrden?: number
  simulado?: boolean
  mops?: Plan[]
  evaluaciones?: Analisis[]
  autorizaciones?: Solicitud[]
  validaciones?: Validacion[]
  validacionesTecnicas?: ValidacionTecnicaVista[]
  incidencias?: Problema[]
  orden?: OrdenResumenRespuesta
  historial?: CambioEstado[]
}

export interface BajaPeticion {
  motivo: string
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
  servidor?: string
  prioridad?: string
  estado?: 'PENDIENTE' | 'EN_COLA' | 'EN_EJECUCION' | 'FINALIZADO' | 'FALLIDO' | 'NO_INICIADO'
  posicion?: number
  piloto?: boolean
  inicioPrevisto?: string
  inicioReal?: string
}

export interface ColumnaInventarioRespuesta {
  clave?: string
  etiqueta?: string
  importable?: boolean
  obligatoria?: boolean
}

export interface ConexionGrupoRespuesta {
  idServidor?: number
  servidor?: string
  simulado?: boolean
  correcto?: boolean
  mensaje?: string
}

export interface ConexionRespuesta {
  simulado?: boolean
  correcto?: boolean
  mensaje?: string
}

export interface ConfiguracionGrupoPeticion {
  frecuenciaRevisionDias?: number
  frecuenciaMantenimientoDias?: number
  modalidadPlanificacion: 'AUTOMATICA' | 'BAJO_DEMANDA'
  modoEjecucion: 'SECUENCIAL' | 'PARALELO'
  idCuentaServicio?: number
  versionEsperada?: string
}

export interface ConfiguracionPeticion {
  frecuenciaRevisionDias?: number
  frecuenciaMantenimientoDias?: number
  modalidadPlanificacion: 'AUTOMATICA' | 'BAJO_DEMANDA'
  idCuentaServicio?: number
  versionEsperada?: string
}

export interface ConfiguracionRespuesta {
  frecuenciaRevisionDias?: number
  frecuenciaMantenimientoDias?: number
  modalidadPlanificacion?: 'AUTOMATICA' | 'BAJO_DEMANDA'
  modoEjecucion?: 'SECUENCIAL' | 'PARALELO'
  idCuentaServicio?: number
  usaCuentaPredeterminada?: boolean
  cuentaServicio?: ReferenciaSimple
  fechaCreacion?: string
  fechaActualizacion?: string
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
  tipoUsuario?: 'ADMINISTRADOR' | 'GENERICO'
  secreto: string
  secretoSu?: string
}

export interface CredencialRespuesta {
  id?: number
  nombre?: string
  usuarioAcceso?: string
  descripcion?: string
  estado?: 'VIGENTE' | 'REVOCADA'
  tipoAutenticacion?: 'PASSWORD' | 'LLAVE_SSH'
  tipoUsuario?: 'ADMINISTRADOR' | 'GENERICO'
  conSu?: boolean
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
}

export interface CuentaServicioRespuesta {
  id?: number
  nombre?: string
  usuarioAcceso?: string
  descripcion?: string
  estado?: 'VIGENTE' | 'REVOCADA'
  tipoAutenticacion?: 'PASSWORD' | 'LLAVE_SSH'
  fechaRegistro?: string
  fechaSecreto?: string
  fechaRevocacion?: string
  servidores?: number
  grupos?: number
  predeterminada?: boolean
}

export interface DatosCredencialPeticion {
  nombre: string
  usuarioAcceso: string
  descripcion?: string
}

export interface Decision {
  id?: number
  tipo?: 'RESPONSABLE' | 'ADMINISTRADOR'
  secuencia?: number
  estado?: 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'FECHA_PROPUESTA' | 'VENCIDA'
  usuario?: string
  fechaSolicitud?: string
  fechaDecision?: string
  fechaPropuesta?: string
  motivo?: string
}

export interface DecisionPeticion {
  decision: 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'FECHA_PROPUESTA' | 'VENCIDA'
  motivo: string
  fechaPropuesta?: string
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

export interface DireccionIpRespuesta {
  id?: number
  direccion?: string
  principal?: boolean
}

export interface EjecucionDia {
  fecha?: string
  exito?: number
  advertencia?: number
  error?: number
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
  alcance?: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD'
  objetivo?: ReferenciaSimple
  motivo?: string
}

export interface ExcepcionUmbralPeticion {
  alcance: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD'
  idObjetivo: number
  valorAdvertencia?: number
  valorCritico?: number
  idListaReferencia?: number
  motivo?: string
}

export interface ExcepcionUmbralRespuesta {
  id?: number
  alcance?: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD'
  objetivo?: ReferenciaSimple
  valorAdvertencia?: number
  valorCritico?: number
  lista?: ListaResumenRespuesta
  motivo?: string
  activo?: boolean
  fechaActualizacion?: string
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
}

export interface ImplementacionScriptVista {
  id?: number
  idVersionSistemaOperativo?: number
  sistemaOperativo?: string
  versiones?: VersionScriptVista[]
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

export interface InmediataPeticion {
  idServidor?: number
  idGrupo?: number
  confirmarFueraVentana?: boolean
  motivo: string
}

export interface IntegrantesPeticion {
  idsServidores: number[]
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

export interface MantenimientoRegistroPeticion {
  configuracion: ConfiguracionPeticion
  ventanas: VentanaPeticion[]
}

export interface Medicion {
  codigo?: string
  nombre?: string
  instancia?: string
  valor?: number
  elementos?: string[]
  umbrales?: UmbralEvaluado[]
}

export interface MetricaPeticion {
  codigo: string
  nombre: string
  unidad?: string
  tipoDato: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  esLista?: boolean
}

export interface MetricaRespuesta {
  id?: number
  codigo?: string
  nombre?: string
  unidad?: string
  tipoDato?: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  esLista?: boolean
  activo?: boolean
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

export interface OrdenDetalleRespuesta {
  resumen?: OrdenResumenRespuesta
  solicitante?: ReferenciaSimple
  modoEjecucionAplicado?: 'SECUENCIAL' | 'PARALELO'
  cuentaServicio?: ReferenciaSimple
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

export interface PaginaRespuestaAnalisis {
  contenido?: Analisis[]
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

export interface PaginaRespuestaProblema {
  contenido?: Problema[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface PaginaRespuestaScriptRespuesta {
  contenido?: ScriptRespuesta[]
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

export interface ParametroScriptVista {
  nombre?: string
  tipo?: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  obligatorio?: boolean
  valorPorDefecto?: string
}

export interface ParametroVersionPeticion {
  nombre: string
  tipo: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN'
  obligatorio?: boolean
  valorPorDefecto?: string
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
  cuentaServicioPredeterminada?: ReferenciaSimple
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

export interface Plan {
  id?: number
  idDetalle?: number
  servidor?: string
  duracionMinutos?: number
  piloto?: boolean
  superaDuracionMaxima?: boolean
  tareas?: TareaMop[]
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

export interface PreparacionImportacionPeticion {
  fila?: number
  idServidorExistente?: number
  versionServidor?: string
  mantenimiento: MantenimientoRegistroPeticion
}

export interface PreparacionesImportacionPeticion {
  servidores: PreparacionImportacionPeticion[]
}

export interface Problema {
  id?: number
  idOrden?: number
  idServidor?: number
  orden?: string
  servidor?: string
  origen?: 'PRUEBA_PILOTO' | 'FALLO_EJECUCION' | 'TIEMPO_EXCEDIDO' | 'VALIDACION_TECNICA_NO_CONFORME' | 'RECHAZO_FUNCIONAL'
  estado?: 'ABIERTA' | 'EN_ATENCION' | 'CERRADA'
  error?: string
  diagnostico?: string
  acciones?: string
  resultado?: string
  fechaApertura?: string
  fechaCierre?: string
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

export interface PruebaUmbralRespuesta {
  nivel?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  origen?: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD' | 'GLOBAL'
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

export interface RecursoHora {
  hora?: string
  cpu?: number
  memoria?: number
  servidores?: number
}

export interface ReferenciaSimple {
  id?: number
  nombre?: string
}

export interface RegistroServidorPeticion {
  servidor: ServidorPeticion
  mantenimiento: MantenimientoRegistroPeticion
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

export interface ResumenGraficos {
  fechaActualizacion?: string
  recursos?: RecursoHora[]
  ejecuciones?: EjecucionDia[]
}

export interface ResumenPlanificacionRespuesta {
  objetivosEvaluados?: number
  ordenesGeneradas?: string[]
  sinIntervalo?: string[]
}

export interface RevocacionPeticion {
  motivo?: string
}

export interface ScriptPeticion {
  nombre: string
  tipo: 'ANALISIS' | 'MANTENIMIENTO'
  descripcion?: string
  activo?: boolean
}

export interface ScriptRespuesta {
  id?: number
  nombre?: string
  tipo?: 'ANALISIS' | 'MANTENIMIENTO'
  descripcion?: string
  activo?: boolean
  implementaciones?: ImplementacionScriptVista[]
}

export interface SecretoPeticion {
  tipoAutenticacion: 'PASSWORD' | 'LLAVE_SSH'
  tipoUsuario?: 'ADMINISTRADOR' | 'GENERICO'
  secreto?: string
  secretoSu?: string
}

export interface SecretoReveladoRespuesta {
  id?: number
  nombre?: string
  usuarioAcceso?: string
  tipoAutenticacion?: 'PASSWORD' | 'LLAVE_SSH'
  tipoUsuario?: 'ADMINISTRADOR' | 'GENERICO'
  fechaSecreto?: string
  secreto?: string
  secretoSu?: string
  segundosVisible?: number
}

export interface ServidorAsignableRespuesta {
  idServidor?: number
  hostname?: string
  direccionIp?: string
  familia?: 'LINUX' | 'WINDOWS'
  idCuentaServicio?: number
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

export interface Solicitud {
  id?: number
  ronda?: number
  fechaLimite?: string
  entorno?: string
  afectacion?: string
  decisiones?: Decision[]
}

export interface SolicitudBajaRespuesta {
  id?: number
  estado?: 'PENDIENTE' | 'APLICADA'
  motivo?: string
  solicitante?: ReferenciaSimple
  fechaSolicitud?: string
  fechaAplicacion?: string
}

export interface TareaMop {
  id?: number
  idTarea?: number
  tarea?: string
  prioridad?: number
  duracionMinutos?: number
  afectacion?: string
  idVersion?: number
  numeroVersion?: number
  idCuentaServicio?: number
  estado?: 'PENDIENTE' | 'EN_EJECUCION' | 'FINALIZADA' | 'FALLIDA' | 'NO_INICIADA'
  codigoRetorno?: number
  salida?: string
  error?: string
  fechaInicio?: string
  fechaFin?: string
}

export interface TareaPeticion {
  idScript: number
  nombre: string
  descripcion?: string
  nivelAfectacion: 'SIN_AFECTACION' | 'PARCIAL' | 'TOTAL'
  prioridad?: number
  duracionEstimadaMinutos?: number
  activo?: boolean
  idsUmbrales: number[]
}

export interface TareaRespuesta {
  id?: number
  idScript?: number
  script?: string
  nombre?: string
  descripcion?: string
  nivelAfectacion?: 'SIN_AFECTACION' | 'PARCIAL' | 'TOTAL'
  prioridad?: number
  duracionEstimadaMinutos?: number
  activo?: boolean
  idsUmbrales?: number[]
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

export interface UmbralEnGrupoRespuesta {
  grupo?: ReferenciaSimple
  excepcion?: ExcepcionAplicadaRespuesta
  valores?: ValoresUmbralRespuesta
}

export interface UmbralEvaluado {
  idUmbral?: number
  idExcepcion?: number
  nivel?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  advertencia?: number
  critico?: number
  operador?: string
  idLista?: number
  nivelFaltante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  nivelSobrante?: 'NORMAL' | 'ADVERTENCIA' | 'CRITICO'
  fechaEvaluacion?: string
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
  origen?: 'SERVIDOR' | 'GRUPO' | 'ENTORNO' | 'CRITICIDAD' | 'GLOBAL'
  excepcion?: ExcepcionAplicadaRespuesta
  aplicado?: ValoresUmbralRespuesta
  global?: ValoresUmbralRespuesta
  enGrupos?: UmbralEnGrupoRespuesta[]
}

export interface UsoVersionRespuesta {
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

export interface Validacion {
  id?: number
  idResponsable?: number
  responsable?: string
  estado?: 'PENDIENTE' | 'CONFORME' | 'RECHAZADA' | 'VENCIDA'
  fechaLimite?: string
  fechaRespuesta?: string
  observaciones?: string
}

export interface ValidacionTecnicaVista {
  idDetalle?: number
  resultado?: 'CONFORME' | 'NO_CONFORME'
  observaciones?: string
  fecha?: string
}

export interface ValidarPeticion {
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

export interface VersionScriptPeticion {
  contenido: string
  notasCambio?: string
  parametros: ParametroVersionPeticion[]
}

export interface VersionScriptVista {
  id?: number
  numero?: number
  contenido?: string
  notasCambio?: string
  habilitada?: boolean
  fechaCreacion?: string
  parametros?: ParametroScriptVista[]
}

export interface VersionSistemaOperativoPeticion {
  version: string
  activo?: boolean
}
