import { useMemo, useState } from 'react'
import { Save, Search } from 'lucide-react'
import { servidoresApi } from '@/api/inventario'
import type { ServidorResumenRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'

interface IntegrantesModalProps {
  abierto: boolean
  nombreGrupo: string
  actuales: number[]
  onCerrar: () => void
  onGuardar: (ids: number[]) => Promise<void>
}

/**
 * Motivo por el que un servidor no puede sumarse a la selección actual
 * (RF20: mismo responsable, entorno y sistema operativo). Es solo una ayuda
 * visual; el backend vuelve a verificarlo.
 */
function incompatibilidad(candidato: ServidorResumenRespuesta, referencia?: ServidorResumenRespuesta): string | null {
  if (!referencia) return null
  if (candidato.responsable?.id !== referencia.responsable?.id) return 'Otro responsable'
  if (candidato.entorno?.id !== referencia.entorno?.id) return 'Otro entorno'
  if (candidato.sistemaOperativo?.id !== referencia.sistemaOperativo?.id) return 'Otro sistema operativo'
  return null
}

export function IntegrantesModal({ abierto, nombreGrupo, actuales, onCerrar, onGuardar }: IntegrantesModalProps) {
  const [seleccion, setSeleccion] = useState<number[]>(actuales)
  const [texto, setTexto] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  // Servidores que pueden integrar grupos: todos menos los dados de baja.
  const servidores = useConsulta(async () => {
    const pagina = await servidoresApi.listar({ tamano: 500, orden: 'hostname,asc' })
    return (pagina.contenido ?? []).filter((s) => s.estado !== 'DADO_DE_BAJA')
  }, [])

  const porId = useMemo(() => new Map((servidores.datos ?? []).map((s) => [s.id, s])), [servidores.datos])
  const referencia = seleccion.length > 0 ? porId.get(seleccion[0]) : undefined
  const visibles = (servidores.datos ?? []).filter((s) => {
    const t = texto.trim().toLowerCase()
    return !t || s.hostname?.toLowerCase().includes(t) || s.direccionIp?.includes(t)
  })

  function alternar(id: number) {
    setSeleccion((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  async function guardar() {
    setGuardando(true)
    setError(undefined)
    try {
      await onGuardar(seleccion)
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      ancho="lg"
      titulo={`Integrantes de ${nombreGrupo}`}
      descripcion="Solo se admiten servidores con el mismo responsable, entorno y sistema operativo. Las órdenes ya generadas conservan sus integrantes."
      pie={
        <>
          <span className="mr-auto self-center text-sm text-ink-soft">{seleccion.length} seleccionados</span>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" icono={Save} cargando={guardando} onClick={guardar}>
            Guardar integrantes
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <MensajeError error={error ?? servidores.error} />
        <div className="flex items-center gap-2 rounded-lg border border-line px-3 py-2">
          <Search className="h-4 w-4 text-ink-faint" aria-hidden="true" />
          <input
            type="search"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Buscar por hostname o IP"
            aria-label="Buscar servidores"
            className="w-full bg-transparent text-sm text-ink placeholder:text-ink-faint focus:outline-none"
          />
        </div>
        {servidores.cargando ? (
          <Cargando />
        ) : (
          <ul className="flex max-h-[45dvh] flex-col divide-y divide-line overflow-y-auto rounded-lg border border-line">
            {visibles.map((s) => {
              const marcado = seleccion.includes(s.id!)
              const motivo = marcado ? null : incompatibilidad(s, referencia)
              return (
                <li key={s.id}>
                  <label className={`flex items-center gap-3 px-3 py-2 text-sm ${motivo ? 'opacity-50' : 'cursor-pointer hover:bg-panel-muted'}`}>
                    <input
                      type="checkbox"
                      checked={marcado}
                      disabled={!!motivo}
                      onChange={() => alternar(s.id!)}
                      className="accent-[var(--color-accent)]"
                    />
                    <span className="flex-1">
                      <span className="font-mono text-ink">{s.hostname}</span>
                      <span className="ml-2 font-mono text-xs text-ink-faint">{s.direccionIp}</span>
                      <span className="block text-xs text-ink-soft">
                        {s.responsable?.nombre} · {s.entorno?.nombre} · {s.versionSistemaOperativo?.nombre}
                      </span>
                    </span>
                    {motivo && <span className="text-xs text-ink-faint">{motivo}</span>}
                  </label>
                </li>
              )
            })}
            {visibles.length === 0 && <li className="px-3 py-6 text-center text-sm text-ink-soft">No hay servidores disponibles.</li>}
          </ul>
        )}
      </div>
    </Modal>
  )
}
