import { useState } from 'react'
import { ArrowRight } from 'lucide-react'
import { gruposApi } from '@/api/inventario'
import { Boton } from '@/components/ui/Boton'
import { Campo, Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { BuscadorServidor } from '@/components/umbrales/BuscadorServidor'
import { useConsulta } from '@/hooks/useConsulta'
import type { ObjetivoProgramacion } from './ProgramarModal'

interface SeleccionObjetivoModalProps {
  abierto: boolean
  onCerrar: () => void
  onElegir: (objetivo: ObjetivoProgramacion) => void
}

export function SeleccionObjetivoModal({ abierto, onCerrar, onElegir }: SeleccionObjetivoModalProps) {
  const [tipo, setTipo] = useState<'servidor' | 'grupo'>('servidor')
  const [id, setId] = useState('')
  // El servidor se busca en el backend sobre todo el inventario; los grupos son pocos y se listan completos
  const [servidor, setServidor] = useState<{ id: number; nombre: string } | null>(null)
  const grupos = useConsulta(
    async () => (await gruposApi.listar()).filter((g) => g.estado === 'ACTIVO').map((g) => ({ id: g.id!, nombre: g.nombre! })),
    [],
  )

  function continuar() {
    if (tipo === 'servidor') {
      if (servidor) onElegir({ idServidor: servidor.id, nombre: servidor.nombre })
      return
    }
    const elegido = grupos.datos?.find((o) => o.id === Number(id))
    if (elegido) onElegir({ idGrupo: elegido.id, nombre: elegido.nombre })
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Programar mantenimiento"
      ancho="xl"
      descripcion="Solo servidores y grupos activos, con configuración de mantenimiento."
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" icono={ArrowRight} onClick={continuar} disabled={tipo === 'servidor' ? !servidor : !id}>
            Continuar
          </Boton>
        </>
      }
    >
      <div className="flex min-h-[min(28rem,55dvh)] flex-col gap-5">
        <MensajeError error={grupos.error} />
        <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Tipo de mantenimiento">
          {(['servidor', 'grupo'] as const).map((t) => (
            <label
              key={t}
              className="flex cursor-pointer items-center gap-2 rounded-lg border border-line p-3 text-sm has-[:checked]:border-accent has-[:checked]:bg-accent-soft"
            >
              <input
                type="radio"
                name="tipo"
                checked={tipo === t}
                onChange={() => {
                  setTipo(t)
                  setId('')
                  setServidor(null)
                }}
                className="accent-[var(--color-accent)]"
              />
              {t === 'servidor' ? 'Individual (servidor)' : 'Grupal (grupo)'}
            </label>
          ))}
        </div>
        {tipo === 'servidor' ? (
          <div className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-ink">
              Servidor<span className="ml-0.5 text-danger">*</span>
            </span>
            {servidor ? (
              <div className="flex items-center justify-between gap-3 rounded-lg border border-line px-3 py-2">
                <span className="font-mono text-ink">{servidor.nombre}</span>
                <Boton onClick={() => setServidor(null)}>Cambiar</Boton>
              </div>
            ) : (
              <BuscadorServidor estado="ACTIVO" onElegir={setServidor} />
            )}
          </div>
        ) : grupos.cargando ? (
          <Cargando />
        ) : (
          <Campo etiqueta="Grupo de mantenimiento" obligatorio>
            <Selector value={id} onChange={(e) => setId(e.target.value)}>
              <option value="">Seleccione…</option>
              {grupos.datos?.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.nombre}
                </option>
              ))}
            </Selector>
          </Campo>
        )}
      </div>
    </Modal>
  )
}
