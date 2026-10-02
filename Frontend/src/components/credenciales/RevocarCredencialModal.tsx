import { useState } from 'react'
import type { FormEvent } from 'react'
import { Ban } from 'lucide-react'
import type { CredencialRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'

interface RevocarCredencialModalProps {
  abierto: boolean
  credencial: CredencialRespuesta
  onCerrar: () => void
  onConfirmar: (motivo: string) => Promise<void>
}

export function RevocarCredencialModal({ abierto, credencial, onCerrar, onConfirmar }: RevocarCredencialModalProps) {
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
      titulo={`Revocar ${credencial.nombre}`}
      descripcion="La credencial deja de poder usarse, editarse y revelarse. Sus versiones se conservan para el historial."
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-revocar" variante="peligro" icono={Ban} cargando={enviando}>
            Revocar
          </Boton>
        </>
      }
    >
      <form id="form-revocar" onSubmit={confirmar} className="flex flex-col gap-4">
        <MensajeError error={error} />
        <Campo etiqueta="Motivo" ayuda="Opcional. Queda registrado en la auditoría.">
          <AreaTexto value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={1000} autoFocus />
        </Campo>
      </form>
    </Modal>
  )
}
