import { useMemo, useState } from 'react'
import { Save, Search } from 'lucide-react'
import { cuentasServicioApi } from '@/api/credenciales'
import type { FinalidadCuentaServicio } from '@/api/credenciales'
import type { CuentaServicioRespuesta, ServidorAsignableRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Campo, Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'
import { ETIQUETA_FAMILIA } from '@/utils/etiquetas'
import { FINALIDADES_CUENTA } from '@/utils/cuentasServicio'

interface AsignarServidoresModalProps {
  abierto: boolean
  cuenta: CuentaServicioRespuesta
  /** Para mostrar el nombre de la cuenta que cada servidor tiene ahora */
  cuentas: CuentaServicioRespuesta[]
  onCerrar: () => void
  onAsignar: (idsServidores: number[], finalidad: FinalidadCuentaServicio) => Promise<void>
}

/** RF07: la cuenta vive en la configuración de mantenimiento, por eso solo se listan servidores configurados */
export function AsignarServidoresModal({ abierto, cuenta, cuentas, onCerrar, onAsignar }: AsignarServidoresModalProps) {
  const [seleccion, setSeleccion] = useState<number[]>([])
  const [texto, setTexto] = useState('')
  const [finalidad, setFinalidad] = useState<FinalidadCuentaServicio>('MANTENIMIENTO')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()
  const servidores = useConsulta(() => cuentasServicioApi.asignaciones(), [])

  const nombres = useMemo(() => new Map(cuentas.map((c) => [c.id, c.nombre])), [cuentas])
  const campos = FINALIDADES_CUENTA.find((item) => item.finalidad === finalidad)!
  const predeterminada = cuentas.find((c) => c[campos.campoPredeterminada])
  const visibles = (servidores.datos ?? []).filter((s) => {
    const t = texto.trim().toLowerCase()
    return !t || s.hostname?.toLowerCase().includes(t) || s.direccionIp?.includes(t)
  })

  function motivoNoAsignable(s: ServidorAsignableRespuesta): string | null {
    if (s[campos.campoId] === cuenta.id) return 'Ya usa esta cuenta'
    if (s.familia === 'WINDOWS' && cuenta.tipoAutenticacion === 'LLAVE_SSH') return 'Windows: requiere contraseña'
    return null
  }

  const elegibles = visibles.filter((s) => !motivoNoAsignable(s)).map((s) => s.idServidor!)
  const todosMarcados = elegibles.length > 0 && elegibles.every((id) => seleccion.includes(id))

  function alternar(id: number) {
    setSeleccion((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  function alternarVisibles() {
    setSeleccion((prev) => (todosMarcados ? prev.filter((id) => !elegibles.includes(id)) : [...new Set([...prev, ...elegibles])]))
  }

  async function asignar() {
    setGuardando(true)
    setError(undefined)
    try {
      await onAsignar(seleccion, finalidad)
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  function cuentaActual(s: ServidorAsignableRespuesta) {
    const id = s[campos.campoId]
    if (id) return nombres.get(id) ?? `#${id}`
    return predeterminada ? (
      <>{predeterminada.nombre} <em className="text-ink-faint">predeterminada del sistema</em></>
    ) : (
      <><em>Predeterminada del sistema</em> (sin definir)</>
    )
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      ancho="lg"
      titulo={`Asignar ${cuenta.nombre} a servidores`}
      descripcion="Elija si la cuenta se usará para evaluar o para ejecutar el mantenimiento. Aplica a las órdenes que se generen después."
      pie={
        <>
          <span className="mr-auto self-center text-sm text-ink-soft">{seleccion.length} seleccionados</span>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" icono={Save} cargando={guardando} disabled={seleccion.length === 0} onClick={asignar}>
            Asignar cuenta
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <MensajeError error={error ?? servidores.error} />
        <Campo etiqueta="Finalidad de la asignación">
          <Selector value={finalidad} disabled={guardando} onChange={(evento) => {
            setFinalidad(evento.target.value as FinalidadCuentaServicio)
            setSeleccion([])
          }}>
            {FINALIDADES_CUENTA.map((item) => <option key={item.finalidad} value={item.finalidad}>{item.etiqueta}</option>)}
          </Selector>
        </Campo>
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex min-w-0 flex-1 items-center gap-2 rounded-lg border border-line px-3 py-2">
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
          <Boton variante="fantasma" onClick={alternarVisibles} disabled={elegibles.length === 0}>
            {todosMarcados ? 'Quitar visibles' : 'Marcar visibles'}
          </Boton>
        </div>
        {servidores.cargando ? (
          <Cargando />
        ) : (
          <ul className="flex max-h-[45dvh] flex-col divide-y divide-line overflow-y-auto rounded-lg border border-line">
            {visibles.map((s) => {
              const motivo = motivoNoAsignable(s)
              return (
                <li key={s.idServidor}>
                  <label className={`flex items-center gap-3 px-3 py-2 text-sm ${motivo ? 'opacity-50' : 'cursor-pointer hover:bg-panel-muted'}`}>
                    <input
                      type="checkbox"
                      checked={motivo === 'Ya usa esta cuenta' || seleccion.includes(s.idServidor!)}
                      disabled={!!motivo}
                      onChange={() => alternar(s.idServidor!)}
                      className="accent-[var(--color-accent)]"
                    />
                    <span className="flex-1">
                      <span className="font-mono text-ink">{s.hostname}</span>
                      <span className="ml-2 font-mono text-xs text-ink-faint">{s.direccionIp}</span>
                      <span className="block text-xs text-ink-soft">
                        {s.familia ? ETIQUETA_FAMILIA[s.familia] : ''} · {campos.etiqueta}: {cuentaActual(s)}
                      </span>
                    </span>
                    {motivo && <span className="text-xs text-ink-faint">{motivo}</span>}
                  </label>
                </li>
              )
            })}
            {visibles.length === 0 && (
              <li className="px-3 py-6 text-center text-sm text-ink-soft">
                No hay servidores con configuración de mantenimiento. La cuenta se asigna sobre esa configuración.
              </li>
            )}
          </ul>
        )}
      </div>
    </Modal>
  )
}
