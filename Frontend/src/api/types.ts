/**
 * Generado desde el contrato OpenAPI del backend de KomainOS.
 * No editar a mano: se regenera con scripts/generar_tipos_ts.py.
 *
 * Si el panel necesita otra forma, el cambio va en el DTO del backend,
 * que es la fuente del contrato.
 */


export interface ActualizarUsuarioPeticion {
  nombreCompleto: string
  rol: 'ADMINISTRADOR' | 'OPERADOR' | 'RESPONSABLE'
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

export interface ColumnaInventarioRespuesta {
  clave?: string
  etiqueta?: string
  importable?: boolean
  obligatoria?: boolean
}

export interface ConfiguracionGrupoPeticion {
  frecuenciaRevisionDias?: number
  frecuenciaMantenimientoDias?: number
  modalidadPlanificacion: 'AUTOMATICA' | 'BAJO_DEMANDA'
  modoEjecucion: 'SECUENCIAL' | 'PARALELO'
  idCuentaServicio?: number
}

export interface ConfiguracionPeticion {
  frecuenciaRevisionDias?: number
  frecuenciaMantenimientoDias?: number
  modalidadPlanificacion: 'AUTOMATICA' | 'BAJO_DEMANDA'
  idCuentaServicio?: number
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
  secreto: string
}

export interface CredencialRespuesta {
  id?: number
  nombre?: string
  usuarioAcceso?: string
  descripcion?: string
  estado?: 'VIGENTE' | 'REVOCADA'
  tipoAutenticacion?: 'PASSWORD' | 'LLAVE_SSH'
  numeroVersion?: number
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
  numeroVersion?: number
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

export interface IntegrantesPeticion {
  idsServidores: number[]
}

export interface LoginPeticion {
  codigo: string
  contrasena: string
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
  programaciones?: Programacion[]
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

export interface PaginaRespuestaOrdenResumenRespuesta {
  contenido?: OrdenResumenRespuesta[]
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

export interface PaginaRespuestaUsuarioRespuesta {
  contenido?: UsuarioRespuesta[]
  pagina?: number
  tamano?: number
  totalElementos?: number
  totalPaginas?: number
}

export interface ParametrosSistemaPeticion {
  maxEjecucionesConcurrentes: number
  maxDuracionTareaMinutos: number
  maxDuracionMopMinutos: number
  minCiclosRachaEstable: number
  minutosExpiracionToken: number
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

export interface Programacion {
  numeroVersion?: number
  fechaObjetivo?: string
  inicio?: string
  fin?: string
  fechaEvaluacion?: string
  inicioVentana?: string
  finVentana?: string
  motivo?: string
  registradoPor?: ReferenciaSimple
  fechaRegistro?: string
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

export interface ReactivacionRespuesta {
  fecha?: string
  usuario?: ReferenciaSimple
}

export interface ReferenciaSimple {
  id?: number
  nombre?: string
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

export interface ResumenPlanificacionRespuesta {
  objetivosEvaluados?: number
  ordenesGeneradas?: string[]
  sinIntervalo?: string[]
}

export interface ReveladoPeticion {
  contrasena: string
}

export interface RevocacionPeticion {
  motivo?: string
}

export interface SecretoPeticion {
  tipoAutenticacion: 'PASSWORD' | 'LLAVE_SSH'
  secreto: string
}

export interface SecretoReveladoRespuesta {
  id?: number
  nombre?: string
  usuarioAcceso?: string
  tipoAutenticacion?: 'PASSWORD' | 'LLAVE_SSH'
  numeroVersion?: number
  secreto?: string
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

export interface SolicitudBajaRespuesta {
  id?: number
  estado?: 'PENDIENTE' | 'APLICADA'
  motivo?: string
  solicitante?: ReferenciaSimple
  fechaSolicitud?: string
  fechaAplicacion?: string
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

export interface UsuarioRespuesta {
  id?: number
  codigo?: string
  nombreCompleto?: string
  rol?: 'ADMINISTRADOR' | 'OPERADOR' | 'RESPONSABLE'
  activo?: boolean
  fechaCreacion?: string
}

export interface UsuarioSesion {
  id?: number
  codigo?: string
  nombreCompleto?: string
  rol?: 'ADMINISTRADOR' | 'OPERADOR' | 'RESPONSABLE'
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

export interface VersionSistemaOperativoPeticion {
  version: string
  activo?: boolean
}
