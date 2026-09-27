import { useState } from 'react'
import type { FormEvent } from 'react'
import { CalendarX } from 'lucide-react'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'

interface CancelarModalProps {
  abierto: boolean
  codigo: string
  onCerrar: () => void
  onConfirmar: (motivo: string) => Promise<void>
}

/** Cancelación de una orden con motivo registrado (RF30, HU18 CA3). */
export function CancelarModal({ abierto, codigo, onCerrar, onConfirmar }: CancelarModalProps) {
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
      titulo={`Cancelar la orden ${codigo}`}
      descripcion="La orden deja de reservar su horario en el cronograma y conserva su historial."
      pie={
        <>
          <Boton onClick={onCerrar}>Volver</Boton>
          <Boton type="submit" form="form-cancelar" variante="peligro" icono={CalendarX} cargando={enviando} disabled={!motivo.trim()}>
            Cancelar orden
          </Boton>
        </>
      }
    >
      <form id="form-cancelar" onSubmit={confirmar} className="flex flex-col gap-4">
        <MensajeError error={error} />
        <Campo etiqueta="Motivo de la cancelación" obligatorio>
          <AreaTexto value={motivo} maxLength={1000} onChange={(e) => setMotivo(e.target.value)} required autoFocus />
        </Campo>
      </form>
    </Modal>
  )
}
