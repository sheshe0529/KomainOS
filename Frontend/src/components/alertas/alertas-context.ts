import { createContext, useContext } from 'react'
import type { AlertaRespuesta } from '@/api/types'

export interface EstadoAlertas {
  recientes: AlertaRespuesta[]
  pendientes: number
  cargando: boolean
  error: unknown
  version: number
  recargar: () => void
  abrir: (id: number) => void
  marcarLeida: (id: number) => Promise<void>
}
export const AlertasContext = createContext<EstadoAlertas | null>(null)
export function useAlertas() {
  const contexto = useContext(AlertasContext)
  if (!contexto) throw new Error('Las alertas requieren AlertasProvider')
  return contexto
}
