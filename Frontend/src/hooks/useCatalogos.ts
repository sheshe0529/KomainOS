import { catalogosApi } from '@/api/catalogos'
import { usuariosApi } from '@/api/usuarios'
import type { EntornoRespuesta, NivelCriticidadRespuesta, SistemaOperativoRespuesta, UsuarioRespuesta } from '@/api/types'
import { useConsulta } from './useConsulta'

export interface Catalogos {
  entornos: EntornoRespuesta[]
  criticidades: NivelCriticidadRespuesta[]
  sistemasOperativos: SistemaOperativoRespuesta[]
}

/** Catálogos de referencia del inventario, cargados en paralelo. */
export function useCatalogos() {
  return useConsulta<Catalogos>(async () => {
    const [entornos, criticidades, sistemasOperativos] = await Promise.all([
      catalogosApi.entornos(),
      catalogosApi.criticidades(),
      catalogosApi.sistemasOperativos(),
    ])
    return { entornos, criticidades, sistemasOperativos }
  }, [])
}

/**
 * Usuarios activos con rol Responsable (DEC-24). Solo el administrador
 * puede listar usuarios, por eso se carga únicamente cuando se necesita.
 */
export function useResponsables(habilitado: boolean) {
  return useConsulta<UsuarioRespuesta[]>(
    async () => (habilitado ? ((await usuariosApi.listar({ rol: 'RESPONSABLE', activo: true })).contenido ?? []) : []),
    [habilitado],
  )
}
