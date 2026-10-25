import { consulta, http, todasLasPaginas } from './cliente'
import type { Pagina } from './dominio'
import type {
  AutomatizacionOrdenRespuesta,
  ScriptRespuesta,
  ScriptPeticion,
  VersionScriptPeticion,
  TareaPeticion,
  TareaRespuesta,
  PoliticaCambioPeticion,
  PoliticaCambioRespuesta,
  OrdenResumenRespuesta,
  ColaRespuesta,
  AlertaRespuesta,
  UsoVersionRespuesta,
  ConexionGrupoRespuesta,
  InformeKpi,
  ResumenGraficos,
} from './types'
export type Problema = NonNullable<AutomatizacionOrdenRespuesta['incidencias']>[number]
export type Analisis = NonNullable<AutomatizacionOrdenRespuesta['evaluaciones']>[number]
export const automatizacionApi = {
  modo: () => http.get<{ simulado: boolean; modo: string; procesoHabilitado: boolean }>('/automatizacion/modo'),
  indicadores: () => http.get<Record<string, number>>('/automatizacion/indicadores'),
  kpis: (desde?: string, hasta?: string) => http.get<InformeKpi>(`/automatizacion/kpis${consulta({ desde, hasta })}`),
  graficos: () => http.get<ResumenGraficos>('/automatizacion/graficos'),
  sala: (pagina = 0, etapa = '', idEntorno = '', idCriticidad = '') =>
    http.get<Pagina<OrdenResumenRespuesta>>(
      `/automatizacion/sala${consulta({ page: pagina, etapa, idEntorno, idCriticidad })}`,
    ),
  cola: (pagina = 0) => http.get<Pagina<ColaRespuesta>>(`/automatizacion/cola${consulta({ page: pagina })}`),
  procesar: () => http.post<{ despachados: number }>('/automatizacion/procesar'),
  ejecutar: () => http.post<{ despachados: number }>('/automatizacion/ejecutar-cola'),
  demostracion: () => http.post<Record<string, number>>('/automatizacion/demostracion'),
  inmediata: (datos: { idServidor?: number; idGrupo?: number; motivo: string; confirmarFueraVentana: boolean }) =>
    http.post<{ id: number; nombre: string }>('/automatizacion/inmediata', datos),
  ficha: (id: number) => http.get<AutomatizacionOrdenRespuesta>(`/ordenes/${id}/automatizacion`),
  evaluar: (id: number) => http.post(`/ordenes/${id}/evaluacion`),
  decidir: (
    id: number,
    datos: { decision: 'APROBADA' | 'RECHAZADA' | 'FECHA_PROPUESTA'; motivo: string; fechaPropuesta?: string },
  ) => http.post(`/ordenes/${id}/autorizacion`, datos),
  aceptarFecha: (id: number) => http.post(`/ordenes/${id}/aceptar-fecha`),
  validar: (id: number, conforme: boolean, observaciones: string) =>
    http.post(`/ordenes/${id}/validacion`, { conforme, observaciones }),
  cerrar: (id: number, motivo: string) => http.post(`/ordenes/${id}/cierre-sin-implementacion`, { motivo }),
  revisar: (id: number) => http.post<Analisis>(`/servidores/${id}/revision`),
  evaluaciones: (id: number, pagina = 0) =>
    http.get<Pagina<Analisis>>(`/servidores/${id}/evaluaciones${consulta({ page: pagina })}`),
  probarConexion: (id: number) =>
    http.post<{ simulado: boolean; correcto: boolean; mensaje: string }>(`/servidores/${id}/conectividad`),
  probarGrupo: (id: number) => http.post<ConexionGrupoRespuesta[]>(`/grupos/${id}/conectividad`),
  scripts: (pagina = 0) => http.get<Pagina<ScriptRespuesta>>(`/scripts${consulta({ page: pagina })}`),
  todosScripts: () => todasLasPaginas<ScriptRespuesta>((page) => http.get(`/scripts${consulta({ page, size: 100 })}`)),
  guardarScript: (id: number | undefined, datos: ScriptPeticion) =>
    id ? http.put<ScriptRespuesta>(`/scripts/${id}`, datos) : http.post<ScriptRespuesta>('/scripts', datos),
  implementar: (id: number, idVersionSistemaOperativo: number) =>
    http.post<number>(`/scripts/${id}/implementaciones`, { idVersionSistemaOperativo }),
  publicar: (id: number, datos: VersionScriptPeticion) => http.post(`/scripts/implementaciones/${id}/versiones`, datos),
  usosVersion: (id: number, pagina = 0) =>
    http.get<Pagina<UsoVersionRespuesta>>(`/scripts/versiones/${id}/ejecuciones${consulta({ page: pagina })}`),
  habilitarVersion: (id: number, habilitada: boolean) => http.put(`/scripts/versiones/${id}/estado`, { habilitada }),
  tareas: (pagina = 0) => http.get<Pagina<TareaRespuesta>>(`/tareas${consulta({ page: pagina })}`),
  guardarTarea: (id: number | undefined, datos: TareaPeticion) =>
    id ? http.put(`/tareas/${id}`, datos) : http.post('/tareas', datos),
  politicas: (pagina = 0) =>
    http.get<Pagina<PoliticaCambioRespuesta>>(`/politicas-cambio${consulta({ page: pagina })}`),
  todasPoliticas: () =>
    todasLasPaginas<PoliticaCambioRespuesta>((page) => http.get(`/politicas-cambio${consulta({ page, size: 100 })}`)),
  guardarPolitica: (id: number | undefined, datos: PoliticaCambioPeticion) =>
    id
      ? http.put<PoliticaCambioRespuesta>(`/politicas-cambio/${id}`, datos)
      : http.post<PoliticaCambioRespuesta>('/politicas-cambio', datos),
  incidencias: (pagina = 0, estado = '', texto = '') =>
    http.get<Pagina<Problema>>(`/incidencias${consulta({ page: pagina, estado, texto })}`),
  incidencia: (id: number) => http.get<Problema>(`/incidencias/${id}`),
  atender: (id: number, datos: { diagnostico: string; acciones: string; resultado: string; cerrar: boolean }) =>
    http.put(`/incidencias/${id}`, datos),
  alertas: (pagina = 0, tamano = 20) =>
    http.get<Pagina<AlertaRespuesta>>(`/alertas${consulta({ page: pagina, size: tamano })}`),
  alerta: (id: number) => http.get<AlertaRespuesta>(`/alertas/${id}`),
  alertasPendientes: () => http.get<{ pendientes: number }>('/alertas/pendientes'),
  leerAlerta: (id: number) => http.post(`/alertas/${id}/lectura`),
}
