import { consulta, http } from './cliente'
import type {
  ActualizarExcepcionUmbralPeticion,
  ActualizarMetricaPeticion,
  ExcepcionUmbralPeticion,
  ExcepcionUmbralRespuesta,
  ListaReferenciaPeticion,
  ListaReferenciaRespuesta,
  MetricaPeticion,
  MetricaRespuesta,
  PruebaUmbralRespuesta,
  UmbralPeticion,
  UmbralRespuesta,
  UmbralServidorRespuesta,
} from './types'

const estado = (activar: boolean) => (activar ? 'activacion' : 'desactivacion')

/** Métricas, umbrales y sus excepciones por servidor, grupo, entorno o criticidad (RF16) */
export const umbralesApi = {
  metricas: () => http.get<MetricaRespuesta[]>('/metricas'),
  crearMetrica: (datos: MetricaPeticion) => http.post<MetricaRespuesta>('/metricas', datos),
  actualizarMetrica: (id: number, datos: ActualizarMetricaPeticion) => http.put<MetricaRespuesta>(`/metricas/${id}`, datos),
  cambiarEstadoMetrica: (id: number, activar: boolean) => http.post<MetricaRespuesta>(`/metricas/${id}/${estado(activar)}`),

  umbrales: (idMetrica?: number) => http.get<UmbralRespuesta[]>(`/umbrales${consulta({ idMetrica })}`),
  umbral: (id: number) => http.get<UmbralRespuesta>(`/umbrales/${id}`),
  crearUmbral: (datos: UmbralPeticion) => http.post<UmbralRespuesta>('/umbrales', datos),
  actualizarUmbral: (id: number, datos: UmbralPeticion) => http.put<UmbralRespuesta>(`/umbrales/${id}`, datos),
  cambiarEstadoUmbral: (id: number, activar: boolean) => http.post<UmbralRespuesta>(`/umbrales/${id}/${estado(activar)}`),
  eliminarUmbral: (id: number) => http.delete<void>(`/umbrales/${id}`),

  crearExcepcion: (idUmbral: number, datos: ExcepcionUmbralPeticion) =>
    http.post<ExcepcionUmbralRespuesta>(`/umbrales/${idUmbral}/excepciones`, datos),
  actualizarExcepcion: (idUmbral: number, idExcepcion: number, datos: ActualizarExcepcionUmbralPeticion) =>
    http.put<ExcepcionUmbralRespuesta>(`/umbrales/${idUmbral}/excepciones/${idExcepcion}`, datos),
  cambiarEstadoExcepcion: (idUmbral: number, idExcepcion: number, activar: boolean) =>
    http.post<ExcepcionUmbralRespuesta>(`/umbrales/${idUmbral}/excepciones/${idExcepcion}/${estado(activar)}`),
  eliminarExcepcion: (idUmbral: number, idExcepcion: number) =>
    http.delete<void>(`/umbrales/${idUmbral}/excepciones/${idExcepcion}`),

  delServidor: (idServidor: number) => http.get<UmbralServidorRespuesta[]>(`/servidores/${idServidor}/umbrales`),
  /** No registra nada: evalúa con lo que rige para el servidor, o como integrante de una orden de ese grupo */
  probar: (idServidor: number, idUmbral: number, p: { valor?: string; elementos?: string; idGrupo?: number }) =>
    http.get<PruebaUmbralRespuesta>(`/servidores/${idServidor}/umbrales/${idUmbral}/prueba${consulta(p)}`),
}

/** Listas de referencia con las que se comparan las métricas de lista */
export const listasApi = {
  listar: () => http.get<ListaReferenciaRespuesta[]>('/listas-referencia'),
  crear: (datos: ListaReferenciaPeticion) => http.post<ListaReferenciaRespuesta>('/listas-referencia', datos),
  actualizar: (id: number, datos: ListaReferenciaPeticion) =>
    http.put<ListaReferenciaRespuesta>(`/listas-referencia/${id}`, datos),
  cambiarEstado: (id: number, activar: boolean) =>
    http.post<ListaReferenciaRespuesta>(`/listas-referencia/${id}/${estado(activar)}`),
  eliminar: (id: number) => http.delete<void>(`/listas-referencia/${id}`),
}
