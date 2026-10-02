import { catalogosApi } from '@/api/catalogos'
import { usuariosApi } from '@/api/usuarios'
import type { EntornoRespuesta, NivelCriticidadRespuesta, SistemaOperativoRespuesta, UsuarioRespuesta } from '@/api/types'
import { useConsulta } from './useConsulta'

export interface Catalogos {
  entornos: EntornoRespuesta[]
  criticidades: NivelCriticidadRespuesta[]
  sistemasOperativos: SistemaOperativoRespuesta[]
}

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

/** Solo el administrador puede listar usuarios: se carga únicamente cuando se necesita (DEC-24) */
export function useResponsables(habilitado: boolean) {
  return useConsulta<UsuarioRespuesta[]>(
    async () => (habilitado ? ((await usuariosApi.listar({ rol: 'RESPONSABLE', activo: true })).contenido ?? []) : []),
    [habilitado],
  )
}
