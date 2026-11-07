import { consulta, http } from './cliente'
import type { Opciones, PaginaRespuestaAuditoriaRespuesta } from './types'

export interface FiltroAuditoria {
  texto?: string
  idUsuario?: number
  sistema?: boolean
  operacion?: string
  entidad?: string
  desde?: string
  hasta?: string
}

/** Registro administrativo de cambios (RNF06), distinto de la bitácora de cada mantenimiento. */
export const auditoriaApi = {
  listar: (filtro: FiltroAuditoria, pagina = 0, tamano = 25) =>
    http.get<PaginaRespuestaAuditoriaRespuesta>(`/auditoria${consulta({ ...filtro, page: pagina, size: tamano })}`),
  filtros: () => http.get<Opciones>('/auditoria/filtros'),
}
