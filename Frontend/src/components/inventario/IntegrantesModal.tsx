import { useEffect, useMemo, useState } from 'react'
import { Save, Search } from 'lucide-react'
import { servidoresApi } from '@/api/inventario'
import type { ServidorResumenRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'

const RESULTADOS = 50
const ESPERA_MS = 250

interface IntegrantesModalProps {
  abierto: boolean
  nombreGrupo: string
  actuales: ServidorResumenRespuesta[]
  onCerrar: () => void
  onGuardar: (ids: number[]) => Promise<void>
}

/**
 * La búsqueda la resuelve el backend sobre todo el inventario, no sobre una primera página. Con un integrante elegido se
 * piden solo los compatibles (mismo responsable, entorno y sistema operativo); el backend lo vuelve a verificar (RF20).
 */
export function IntegrantesModal({ abierto, nombreGrupo, actuales, onCerrar, onGuardar }: IntegrantesModalProps) {
  const [seleccion, setSeleccion] = useState(() => new Map(actuales.map((s) => [s.id!, s])))
  const [texto, setTexto] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  useEffect(() => {
    const temporizador = window.setTimeout(() => setBusqueda(texto.trim()), ESPERA_MS)
    return () => window.clearTimeout(temporizador)
  }, [texto])

  const referencia = seleccion.values().next().value
  const idResponsable = referencia?.responsable?.id
  const idEntorno = referencia?.entorno?.id
  const idSistemaOperativo = referencia?.sistemaOperativo?.id

  const encontrados = useConsulta(async () => {
    const pagina = await servidoresApi.listar({
      texto: busqueda || undefined,
      idResponsable,
      idEntorno,
      idSistemaOperativo,
      tamano: RESULTADOS,
      orden: 'hostname,asc',
    })
    // Pueden integrar grupos todos menos los dados de baja
    return { items: (pagina.contenido ?? []).filter((s) => s.estado !== 'DADO_DE_BAJA'), total: pagina.totalElementos ?? 0 }
  }, [busqueda, idResponsable, idEntorno, idSistemaOperativo])

  // Los elegidos siempre a la vista, aunque la búsqueda actual no los incluya
  const visibles = useMemo(
    () => [...seleccion.values(), ...(encontrados.datos?.items ?? []).filter((s) => !seleccion.has(s.id!))],
    [seleccion, encontrados.datos],
  )
  const restantes = (encontrados.datos?.total ?? 0) - (encontrados.datos?.items.length ?? 0)

  function alternar(servidor: ServidorResumenRespuesta) {
    setSeleccion((previa) => {
      const nueva = new Map(previa)
      if (nueva.has(servidor.id!)) nueva.delete(servidor.id!)
      else nueva.set(servidor.id!, servidor)
      return nueva
    })
  }

  async function guardar() {
    setGuardando(true)
    setError(undefined)
    try {
      await onGuardar([...seleccion.keys()])
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
          <span className="mr-auto self-center text-sm text-ink-soft">{seleccion.size} seleccionados</span>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" icono={Save} cargando={guardando} onClick={guardar}>
            Guardar integrantes
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <MensajeError error={error ?? encontrados.error} />
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
        {referencia && (
          <p className="text-xs text-ink-soft">
            Se muestran solo los compatibles con <span className="font-mono">{referencia.hostname}</span>:{' '}
            {referencia.responsable?.nombre} · {referencia.entorno?.nombre} · {referencia.sistemaOperativo?.nombre}.
          </p>
        )}
        {encontrados.cargando && !encontrados.datos ? (
          <Cargando />
        ) : (
          <ul className="flex max-h-[45dvh] flex-col divide-y divide-line overflow-y-auto rounded-lg border border-line">
            {visibles.map((s) => (
              <li key={s.id}>
                <label className="flex cursor-pointer items-center gap-3 px-3 py-2 text-sm hover:bg-panel-muted">
                  <input
                    type="checkbox"
                    checked={seleccion.has(s.id!)}
                    onChange={() => alternar(s)}
                    className="accent-[var(--color-accent)]"
                  />
                  <span className="flex-1">
                    <span className="font-mono text-ink">{s.hostname}</span>
                    <span className="ml-2 font-mono text-xs text-ink-faint">{s.direccionIp}</span>
                    <span className="block text-xs text-ink-soft">
                      {s.responsable?.nombre} · {s.entorno?.nombre} · {s.versionSistemaOperativo?.nombre}
                    </span>
                  </span>
                </label>
              </li>
            ))}
            {visibles.length === 0 && <li className="px-3 py-6 text-center text-sm text-ink-soft">No hay servidores disponibles.</li>}
            {restantes > 0 && (
              <li className="px-3 py-2 text-center text-xs text-ink-faint">
                {restantes} servidor(es) más coinciden: escriba parte del hostname o la IP para acotar.
              </li>
            )}
          </ul>
        )}
      </div>
    </Modal>
  )
}
