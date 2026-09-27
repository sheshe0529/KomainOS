import { useState } from 'react'
import { ArrowRight } from 'lucide-react'
import { gruposApi, servidoresApi } from '@/api/inventario'
import { Boton } from '@/components/ui/Boton'
import { Campo, Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'
import type { ObjetivoProgramacion } from './ProgramarModal'

interface SeleccionObjetivoModalProps {
  abierto: boolean
  onCerrar: () => void
  onElegir: (objetivo: ObjetivoProgramacion) => void
}

/** Paso previo a programar desde el cronograma: elegir un servidor o un grupo activo. */
export function SeleccionObjetivoModal({ abierto, onCerrar, onElegir }: SeleccionObjetivoModalProps) {
  const [tipo, setTipo] = useState<'servidor' | 'grupo'>('servidor')
  const [id, setId] = useState('')
  const opciones = useConsulta(async () => {
    const [servidores, grupos] = await Promise.all([
      servidoresApi.listar({ estado: 'ACTIVO', tamano: 500, orden: 'hostname,asc' }),
      gruposApi.listar(),
    ])
    return {
      servidores: (servidores.contenido ?? []).map((s) => ({ id: s.id!, nombre: s.hostname! })),
      grupos: grupos.filter((g) => g.estado === 'ACTIVO').map((g) => ({ id: g.id!, nombre: g.nombre! })),
    }
  }, [])

  const lista = tipo === 'servidor' ? opciones.datos?.servidores : opciones.datos?.grupos

  function continuar() {
    const elegido = lista?.find((o) => o.id === Number(id))
    if (!elegido) return
    onElegir(tipo === 'servidor' ? { idServidor: elegido.id, nombre: elegido.nombre } : { idGrupo: elegido.id, nombre: elegido.nombre })
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Programar mantenimiento"
      descripcion="Solo servidores y grupos activos, con configuración de mantenimiento."
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" icono={ArrowRight} onClick={continuar} disabled={!id}>
            Continuar
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <MensajeError error={opciones.error} />
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
                }}
                className="accent-[var(--color-accent)]"
              />
              {t === 'servidor' ? 'Individual (servidor)' : 'Grupal (grupo)'}
            </label>
          ))}
        </div>
        {opciones.cargando ? (
          <Cargando />
        ) : (
          <Campo etiqueta={tipo === 'servidor' ? 'Servidor' : 'Grupo de mantenimiento'} obligatorio>
            <Selector value={id} onChange={(e) => setId(e.target.value)}>
              <option value="">Seleccione…</option>
              {lista?.map((o) => (
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
