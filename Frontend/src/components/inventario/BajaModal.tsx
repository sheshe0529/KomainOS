import { useState } from 'react'
import type { FormEvent } from 'react'
import { ArchiveX } from 'lucide-react'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'

interface BajaModalProps {
  abierto: boolean
  hostname: string
  onCerrar: () => void
  onConfirmar: (motivo: string) => Promise<void>
}

export function BajaModal({ abierto, hostname, onCerrar, onConfirmar }: BajaModalProps) {
  const [motivo, setMotivo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function confirmar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setError(undefined)
    try {
      await onConfirmar(motivo.trim())
    } catch (e) {
      setError(e)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={`Dar de baja ${hostname}`}
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-baja" variante="peligro" icono={ArchiveX} cargando={enviando} disabled={!motivo.trim()}>
            Dar de baja
          </Boton>
        </>
      }
    >
      <form id="form-baja" onSubmit={confirmar} className="flex flex-col gap-4">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <ul className="list-disc space-y-1 pl-5 text-sm text-ink-soft">
          <li>Si hay un mantenimiento en curso, la baja se aplicará cuando finalice.</li>
          <li>Se retiran sus mantenimientos pendientes y sus pertenencias a grupos.</li>
          <li>Se elimina su configuración de mantenimiento; el historial se conserva.</li>
          <li>No se iniciarán nuevos mantenimientos mientras permanezca dado de baja.</li>
        </ul>
        <Campo etiqueta="Motivo de la baja" obligatorio error={errorDeCampo(error, 'motivo')}>
          <AreaTexto value={motivo} maxLength={500} onChange={(e) => setMotivo(e.target.value)} required autoFocus />
        </Campo>
      </form>
    </Modal>
  )
}
