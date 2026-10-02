import { useState } from 'react'
import type { FormEvent } from 'react'
import { Download } from 'lucide-react'
import { credencialesApi } from '@/api/credenciales'
import { FORMATOS, type FormatoArchivo } from '@/api/intercambio'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { errorDeCampo } from '@/utils/errores'
import { CampoReautenticacion } from './CampoReautenticacion'

/** Exportación adicional a la del inventario: una fila por credencial documental vigente de cada servidor (DEC-39) */
export function ExportarCredencialesModal({ abierto, onCerrar }: { abierto: boolean; onCerrar: () => void }) {
  const { avisar } = useAvisos()
  const [formato, setFormato] = useState<FormatoArchivo>('XLSX')
  const [contrasena, setContrasena] = useState('')
  const [descargando, setDescargando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function exportar(evento: FormEvent) {
    evento.preventDefault()
    setDescargando(true)
    setError(undefined)
    try {
      const nombre = await credencialesApi.exportarTodas(formato, contrasena)
      avisar(`Se descargó ${nombre}. Contiene contraseñas en claro: guárdelo en un lugar seguro.`)
      onCerrar()
    } catch (e) {
      setError(e)
    } finally {
      setDescargando(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Exportar credenciales"
      descripcion="Todas las credenciales documentales vigentes de todos los servidores, con sus contraseñas y llaves en claro. La exportación queda registrada en la auditoría."
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-exportar-credenciales" variante="primario" icono={Download} cargando={descargando}>
            Descargar
          </Boton>
        </>
      }
    >
      <form id="form-exportar-credenciales" onSubmit={exportar} className="flex flex-col gap-4">
        {!errorDeCampo(error, 'contrasena') && <MensajeError error={error} />}
        <fieldset>
          <legend className="mb-2 text-sm font-medium text-ink">Formato</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {FORMATOS.map((f) => (
              <label
                key={f.valor}
                className="flex cursor-pointer items-center gap-2 rounded-lg border border-line px-3 py-2 text-sm has-[:checked]:border-accent has-[:checked]:bg-accent-soft"
              >
                <input
                  type="radio"
                  name="formato-credenciales"
                  checked={formato === f.valor}
                  onChange={() => setFormato(f.valor)}
                  className="accent-[var(--color-accent)]"
                />
                {f.etiqueta}
              </label>
            ))}
          </div>
        </fieldset>
        <CampoReautenticacion
          valor={contrasena}
          onCambiar={setContrasena}
          error={errorDeCampo(error, 'contrasena')}
          ayuda="Se pide de nuevo para exportar secretos (RF13)."
        />
      </form>
    </Modal>
  )
}
