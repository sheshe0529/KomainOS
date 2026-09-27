import { createContext, useContext } from 'react'

export type TipoAviso = 'exito' | 'error' | 'info'

export interface Aviso {
  id: number
  tipo: TipoAviso
  texto: string
}

export interface AvisosContextValue {
  avisar: (texto: string, tipo?: TipoAviso) => void
}

export const AvisosContext = createContext<AvisosContextValue | null>(null)

/** Retroalimentación breve tras una acción del usuario. */
export function useAvisos() {
  const ctx = useContext(AvisosContext)
  if (!ctx) throw new Error('useAvisos debe usarse dentro de <AvisosProvider>')
  return ctx
}
