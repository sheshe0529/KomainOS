import { useCallback, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { CircleCheck, CircleX, Info, X } from 'lucide-react'
import { AvisosContext, type Aviso, type TipoAviso } from './avisos-context'

const ESTILOS: Record<TipoAviso, string> = {
  exito: 'border-success/30 bg-success-soft text-success',
  error: 'border-danger/30 bg-danger-soft text-danger',
  info: 'border-accent/30 bg-accent-soft text-accent',
}

const ICONOS = { exito: CircleCheck, error: CircleX, info: Info }

export function AvisosProvider({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<Aviso[]>([])
  const siguiente = useRef(1)

  const quitar = useCallback((id: number) => setAvisos((prev) => prev.filter((a) => a.id !== id)), [])

  const avisar = useCallback(
    (texto: string, tipo: TipoAviso = 'exito') => {
      const id = siguiente.current++
      setAvisos((prev) => [...prev, { id, tipo, texto }])
      window.setTimeout(() => quitar(id), tipo === 'error' ? 8000 : 4500)
    },
    [quitar],
  )

  const valor = useMemo(() => ({ avisar }), [avisar])

  return (
    <AvisosContext.Provider value={valor}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[min(24rem,calc(100%-2rem))] flex-col gap-2">
        {avisos.map((aviso) => {
          const Icono = ICONOS[aviso.tipo]
          return (
            <div
              key={aviso.id}
              role={aviso.tipo === 'error' ? 'alert' : 'status'}
              className={`pointer-events-auto flex items-start gap-2 rounded-lg border px-3 py-2.5 text-sm shadow-lg ${ESTILOS[aviso.tipo]}`}
            >
              <Icono className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <p className="flex-1">{aviso.texto}</p>
              <button type="button" onClick={() => quitar(aviso.id)} aria-label="Cerrar aviso">
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          )
        })}
      </div>
    </AvisosContext.Provider>
  )
}
