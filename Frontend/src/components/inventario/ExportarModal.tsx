import { useEffect, useState } from 'react'
import { Download } from 'lucide-react'
import { FORMATOS, intercambioApi, type FormatoArchivo } from '@/api/intercambio'
import type { FiltroServidores } from '@/api/inventario'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'

interface ExportarModalProps {
  abierto: boolean
  /** Filtros vigentes del listado: se exporta lo mismo que se está viendo. */
  filtro: FiltroServidores & { texto?: string }
  total: number
  onCerrar: () => void
}

/** Exportación del inventario (RF13, HU09): formato y columnas autorizadas. */
export function ExportarModal({ abierto, filtro, total, onCerrar }: ExportarModalProps) {
  const { avisar } = useAvisos()
  const columnas = useConsulta(() => intercambioApi.columnas(), [])
  const [formato, setFormato] = useState<FormatoArchivo>('XLSX')
  const [elegidas, setElegidas] = useState<string[]>([])
  const [descargando, setDescargando] = useState(false)
  const [error, setError] = useState<unknown>()

  // Todas marcadas al inicio (HU09 CA1: el usuario desmarca lo que no necesita).
  useEffect(() => {
    if (columnas.datos) setElegidas(columnas.datos.map((c) => c.clave!))
  }, [columnas.datos])

  function alternar(clave: string) {
    setElegidas((actual) => (actual.includes(clave) ? actual.filter((c) => c !== clave) : [...actual, clave]))
  }

  async function exportar() {
    setDescargando(true)
    setError(undefined)
    try {
      // Se envían en el orden del catálogo, no en el orden en que se marcaron.
      const orden = (columnas.datos ?? []).map((c) => c.clave!).filter((c) => elegidas.includes(c))
      const nombre = await intercambioApi.exportar(formato, orden, filtro)
      avisar(`Se descargó ${nombre}.`)
      onCerrar()
    } catch (e) {
      setError(e)
    } finally {
      setDescargando(false)
    }
  }

  const todas = columnas.datos?.length ?? 0

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Exportar inventario"
      descripcion={`Se exportarán ${total} ${total === 1 ? 'servidor' : 'servidores'} con los filtros aplicados en la lista.`}
      ancho="lg"
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" icono={Download} cargando={descargando} disabled={elegidas.length === 0 || total === 0} onClick={exportar}>
            Descargar
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-5">
        <MensajeError error={error ?? columnas.error} />

        <fieldset>
          <legend className="mb-2 text-sm font-medium text-ink">Formato</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {FORMATOS.map((f) => (
              <label
                key={f.valor}
                className={`flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5 text-sm transition ${
                  formato === f.valor ? 'border-accent bg-accent-soft' : 'border-line hover:bg-panel-muted'
                }`}
              >
                <input
                  type="radio"
                  name="formato-exportacion"
                  checked={formato === f.valor}
                  onChange={() => setFormato(f.valor)}
                  className="mt-0.5 accent-[var(--color-accent)]"
                />
                <span>
                  <span className="block font-medium text-ink">{f.etiqueta}</span>
                  <span className="block text-xs text-ink-soft">{f.descripcion}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <div className="mb-2 flex items-center justify-between gap-2">
            <legend className="text-sm font-medium text-ink">
              Columnas <span className="font-normal text-ink-faint">({elegidas.length} de {todas})</span>
            </legend>
            <div className="flex gap-3 text-xs">
              <button type="button" className="text-accent hover:underline" onClick={() => setElegidas((columnas.datos ?? []).map((c) => c.clave!))}>
                Todas
              </button>
              <button type="button" className="text-accent hover:underline" onClick={() => setElegidas([])}>
                Ninguna
              </button>
            </div>
          </div>
          {columnas.cargando && !columnas.datos ? (
            <Cargando />
          ) : (
            <div className="grid gap-x-4 gap-y-1 rounded-lg border border-line p-3 sm:grid-cols-2 lg:grid-cols-3">
              {columnas.datos?.map((c) => (
                <label key={c.clave} className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 text-sm text-ink hover:bg-panel-muted">
                  <input
                    type="checkbox"
                    checked={elegidas.includes(c.clave!)}
                    onChange={() => alternar(c.clave!)}
                    className="accent-[var(--color-accent)]"
                  />
                  {c.etiqueta}
                </label>
              ))}
            </div>
          )}
          <p className="mt-2 text-xs text-ink-faint">
            Las credenciales de los servidores no se incluyen en la exportación. Un archivo exportado puede volver a
            importarse sin cambios.
          </p>
        </fieldset>
      </div>
    </Modal>
  )
}
