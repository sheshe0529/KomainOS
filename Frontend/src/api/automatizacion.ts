import { consulta, http, todasLasPaginas } from './cliente'
import type { Pagina } from './dominio'
import type {
  AlertaRespuesta,
  AnalisisRespuesta,
  ColaRespuesta,
  ConexionRespuesta,
  DecidirAutorizacionPeticion,
  DespachoRespuesta,
  Diagnostico,
  EjecucionDetalleRespuesta,
  EjecucionRespuesta,
  FichaAutomatizacionRespuesta,
  ImplementacionPeticion,
  IncidenciaRespuesta,
  InformeKpi,
  LanzamientoInmediatoPeticion,
  OrdenResumenRespuesta,
  PoliticaCambioPeticion,
  PoliticaCambioRespuesta,
  Preparacion,
  PruebaAnalisisRespuesta,
  ReferenciaSimple,
  ResumenGraficos,
  ResumenProcesoRespuesta,
  RevisionSalidaRespuesta,
  ScriptPeticion,
  ScriptPreparadoRespuesta,
  ScriptRespuesta,
  ScriptResumenRespuesta,
  TareaPeticion,
  TareaRespuesta,
  UsoVersionRespuesta,
  VersionRespuesta,
  VersionScriptPeticion,
  VistaPreviaPeticion,
} from './types'

export interface FiltroScripts {
  texto?: string
  tipo?: string
  activo?: boolean
}

/** Catálogo de scripts: implementaciones por sistema operativo y versiones inmutables (RF22, RF25, RF26) */
export const scriptsApi = {
  listar: (filtro: FiltroScripts, pagina = 0) =>
    http.get<Pagina<ScriptResumenRespuesta>>(`/scripts${consulta({ ...filtro, page: pagina })}`),
  todos: (filtro: FiltroScripts = {}) =>
    todasLasPaginas<ScriptResumenRespuesta>((page) => http.get(`/scripts${consulta({ ...filtro, page, size: 100 })}`)),
  obtener: (id: number) => http.get<ScriptRespuesta>(`/scripts/${id}`),
  registrar: (datos: ScriptPeticion) => http.post<ScriptRespuesta>('/scripts', datos),
  actualizar: (id: number, datos: ScriptPeticion) => http.put<ScriptRespuesta>(`/scripts/${id}`, datos),

  /** La implementación nace con su primera versión: no existen implementaciones vacías */
  implementar: (id: number, datos: ImplementacionPeticion) =>
    http.post<ScriptRespuesta>(`/scripts/${id}/implementaciones`, datos),
  publicar: (idImplementacion: number, datos: VersionScriptPeticion) =>
    http.post<VersionRespuesta>(`/scripts/implementaciones/${idImplementacion}/versiones`, datos),
  cambiarEstadoVersion: (idVersion: number, habilitada: boolean) =>
    http.put<VersionRespuesta>(`/scripts/versiones/${idVersion}/estado`, { habilitada }),

  /** El archivo tal como llega al servidor: el bloque de parámetros que antepone KomainOS y el código */
  vistaPrevia: (datos: VistaPreviaPeticion) => http.post<ScriptPreparadoRespuesta>('/scripts/vista-previa', datos),
  vistaPreviaVersion: (idVersion: number) =>
    http.get<ScriptPreparadoRespuesta>(`/scripts/versiones/${idVersion}/vista-previa`),
  revisarSalida: (salida: string) => http.post<RevisionSalidaRespuesta>('/scripts/revisar-salida', { salida }),
  probarAnalisis: (idVersion: number, idServidor: number) =>
    http.post<PruebaAnalisisRespuesta>(`/scripts/versiones/${idVersion}/prueba`, { idServidor }),
  usosVersion: (idVersion: number, pagina = 0) =>
    http.get<Pagina<UsoVersionRespuesta>>(`/scripts/versiones/${idVersion}/ejecuciones${consulta({ page: pagina })}`),
}

/** Tareas de mantenimiento: script, afectación, prioridad y umbrales que las activan (RF23, RF24) */
export const tareasApi = {
  listar: (pagina = 0, tamano = 20) =>
    http.get<Pagina<TareaRespuesta>>(`/tareas${consulta({ page: pagina, size: tamano })}`),
  obtener: (id: number) => http.get<TareaRespuesta>(`/tareas/${id}`),
  guardar: (id: number | undefined, datos: TareaPeticion) =>
    id ? http.put<TareaRespuesta>(`/tareas/${id}`, datos) : http.post<TareaRespuesta>('/tareas', datos),
}

/** Ciclo de una orden: evaluación previa, autorización, validación y bitácora (RF38-RF57) */
export const cicloApi = {
  ficha: (idOrden: number) => http.get<FichaAutomatizacionRespuesta>(`/ordenes/${idOrden}/automatizacion`),
  evaluar: (idOrden: number) => http.post<{ mensaje: string }>(`/ordenes/${idOrden}/evaluacion`),
  decidir: (idOrden: number, datos: DecidirAutorizacionPeticion) =>
    http.post<void>(`/ordenes/${idOrden}/autorizacion`, datos),
  aceptarFecha: (idOrden: number) => http.post<void>(`/ordenes/${idOrden}/aceptar-fecha`),
  validar: (idOrden: number, conforme: boolean, observaciones: string) =>
    http.post<void>(`/ordenes/${idOrden}/validacion`, { conforme, observaciones }),
  cerrarSinImplementacion: (idOrden: number, motivo: string) =>
    http.post<void>(`/ordenes/${idOrden}/cierre-sin-implementacion`, { motivo }),
}

export interface FiltroSala {
  etapa?: string
  idEntorno?: string
  idCriticidad?: string
  texto?: string
}

export interface FiltroEjecuciones {
  idServidor?: number
  idScript?: number
  estado?: string
  desde?: string
  hasta?: string
  texto?: string
}

/** Sala de operaciones: estado del motor, órdenes por etapa, cola e historial de ejecución */
export const operacionApi = {
  diagnostico: () => http.get<Diagnostico>('/automatizacion/modo'),
  sala: (filtro: FiltroSala, pagina = 0) =>
    http.get<Pagina<OrdenResumenRespuesta>>(`/automatizacion/sala${consulta({ ...filtro, page: pagina })}`),
  indicadores: () => http.get<Record<string, number>>('/automatizacion/indicadores'),
  cola: (pagina = 0) => http.get<Pagina<ColaRespuesta>>(`/automatizacion/cola${consulta({ page: pagina })}`),
  kpis: (desde?: string, hasta?: string) => http.get<InformeKpi>(`/automatizacion/kpis${consulta({ desde, hasta })}`),
  graficos: () => http.get<ResumenGraficos>('/automatizacion/graficos'),
  ejecuciones: (filtro: FiltroEjecuciones, pagina = 0, tamano = 20) =>
    http.get<Pagina<EjecucionRespuesta>>(
      `/automatizacion/ejecuciones${consulta({ ...filtro, page: pagina, size: tamano })}`,
    ),
  ejecucion: (id: number) => http.get<EjecucionDetalleRespuesta>(`/automatizacion/ejecuciones/${id}`),
  inmediata: (datos: LanzamientoInmediatoPeticion) => http.post<ReferenciaSimple>('/automatizacion/inmediata', datos),
  procesar: () => http.post<ResumenProcesoRespuesta>('/automatizacion/procesar'),
  ejecutarCola: () => http.post<DespachoRespuesta>('/automatizacion/ejecutar-cola'),
  demostracion: () => http.post<Record<string, number>>('/automatizacion/demostracion'),
}

/** Acceso y análisis de un servidor o grupo fuera del ciclo de una orden */
export const analisisApi = {
  probarServidor: (idServidor: number, idCuenta?: number) =>
    http.post<ConexionRespuesta>(`/servidores/${idServidor}/conectividad${consulta({ idCuenta })}`),
  probarGrupo: (idGrupo: number, idCuenta?: number) =>
    http.post<ConexionRespuesta[]>(`/grupos/${idGrupo}/conectividad${consulta({ idCuenta })}`),
  revisar: (idServidor: number) => http.post<AnalisisRespuesta>(`/servidores/${idServidor}/revision`),
  historial: (idServidor: number, pagina = 0) =>
    http.get<Pagina<AnalisisRespuesta>>(`/servidores/${idServidor}/evaluaciones${consulta({ page: pagina, size: 5 })}`),
  preparacion: (idServidor: number) => http.get<Preparacion>(`/servidores/${idServidor}/preparacion-automatizacion`),
}

export const politicasApi = {
  listar: () =>
    todasLasPaginas<PoliticaCambioRespuesta>((page) => http.get(`/politicas-cambio${consulta({ page, size: 100 })}`)),
  guardar: (id: number | undefined, datos: PoliticaCambioPeticion) =>
    id
      ? http.put<PoliticaCambioRespuesta>(`/politicas-cambio/${id}`, datos)
      : http.post<PoliticaCambioRespuesta>('/politicas-cambio', datos),
}

export interface FiltroIncidencias {
  estado?: string
  origen?: string
  idServidor?: number
  texto?: string
}

export const incidenciasApi = {
  listar: (filtro: FiltroIncidencias, pagina = 0) =>
    http.get<Pagina<IncidenciaRespuesta>>(`/incidencias${consulta({ ...filtro, page: pagina })}`),
  obtener: (id: number) => http.get<IncidenciaRespuesta>(`/incidencias/${id}`),
  atender: (id: number, datos: { diagnostico: string; acciones: string; resultado: string; cerrar: boolean }) =>
    http.put<IncidenciaRespuesta>(`/incidencias/${id}`, datos),
}

export const alertasApi = {
  listar: (pagina = 0, tamano = 20, soloNoLeidas = false) =>
    http.get<Pagina<AlertaRespuesta>>(`/alertas${consulta({ page: pagina, size: tamano, soloNoLeidas })}`),
  obtener: (id: number) => http.get<AlertaRespuesta>(`/alertas/${id}`),
  pendientes: () => http.get<{ pendientes: number }>('/alertas/pendientes'),
  leer: (id: number) => http.post<void>(`/alertas/${id}/lectura`),
  leerTodas: () => http.post<{ leidas: number }>('/alertas/lectura'),
}
