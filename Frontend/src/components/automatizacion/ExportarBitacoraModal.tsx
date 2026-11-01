import { useState } from 'react'
import type { FormEvent } from 'react'
import { Download } from 'lucide-react'
import type { FormatoBitacora } from '@/api/automatizacion'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'

const FORMATOS: { valor: FormatoBitacora; etiqueta: string; descripcion: string }[] = [
  { valor: 'XLSX', etiqueta: 'Excel (XLSX)', descripcion: 'Hoja de cálculo con encabezados y columnas ajustadas' },
  { valor: 'CSV', etiqueta: 'CSV', descripcion: 'Texto separado por comas, UTF-8' },
  { valor: 'JSON', etiqueta: 'JSON', descripcion: 'Lista de eventos para integraciones' },
]

export interface OpcionAlcance {
  /** Sin valor, toda la bitácora */
  idServidor?: number
  etiqueta: string
}

interface ExportarBitacoraModalProps {
  titulo: string
  descripcion: string
  /** En un grupo: la bitácora completa o la de un integrante */
  alcances?: OpcionAlcance[]
  onExportar: (formato: FormatoBitacora, idServidor?: number) => Promise<string>
  onCerrar: () => void
}

/**
 * Exportación de la bitácora (RF53): un evento por fila con fecha, orden, servidor, resultado y quién decidió o
 * ejecutó. Se elige el formato y, en un grupo, qué servidores incluir
 */
export function ExportarBitacoraModal({ titulo, descripcion, alcances, onExportar, onCerrar }: ExportarBitacoraModalProps) {
  const { avisar } = useAvisos()
  const [formato, setFormato] = useState<FormatoBitacora>('XLSX')
  const [alcance, setAlcance] = useState('')
  const [descargando, setDescargando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function exportar(evento: FormEvent) {
    evento.preventDefault()
    setDescargando(true)
    setError(undefined)
    try {
      const nombre = await onExportar(formato, alcance ? Number(alcance) : undefined)
      avisar(`Se descargó ${nombre}.`)
      onCerrar()
    } catch (e) {
      setError(e)
    } finally {
      setDescargando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho="lg"
      titulo={titulo}
      descripcion={descripcion}
      onCerrar={onCerrar}
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-exportar-bitacora" variante="primario" icono={Download} cargando={descargando}>
            Descargar
          </Boton>
        </>
      }
    >
      <form id="form-exportar-bitacora" onSubmit={exportar} className="flex flex-col gap-5">
        <MensajeError error={error} />

        {alcances && alcances.length > 1 && (
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium text-ink">Servidores</span>
            <Selector value={alcance} onChange={(e) => setAlcance(e.target.value)}>
              {alcances.map((a) => (
                <option key={a.idServidor ?? 'todos'} value={a.idServidor ?? ''}>
                  {a.etiqueta}
                </option>
              ))}
            </Selector>
          </label>
        )}

        <fieldset>
          <legend className="mb-2 text-sm font-medium text-ink">Formato</legend>
          <div className="grid gap-2 sm:grid-cols-3">
            {FORMATOS.map((f) => (
              <label
                key={f.valor}
                className={`flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5 text-sm transition ${
                  formato === f.valor ? 'border-accent bg-accent-soft' : 'border-line hover:bg-panel-muted'
                }`}
              >
                <input
                  type="radio"
                  name="formato-bitacora"
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

        <p className="text-xs text-ink-soft">
          Incluye cambios de estado, análisis, decisiones de autorización, tareas con su salida, validaciones e
          incidencias. Nunca incluye contraseñas ni llaves.
        </p>
      </form>
    </Modal>
  )
}
