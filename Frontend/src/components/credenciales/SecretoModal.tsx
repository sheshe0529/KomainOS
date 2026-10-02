import { useState } from 'react'
import type { FormEvent } from 'react'
import { KeyRound } from 'lucide-react'
import type { FamiliaSistemaOperativo, TipoAutenticacion } from '@/api/dominio'
import type { CredencialRespuesta, SecretoPeticion } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { CampoSecreto, SelectorMecanismo } from './CampoSecreto'

interface SecretoModalProps {
  abierto: boolean
  credencial: CredencialRespuesta
  familia?: FamiliaSistemaOperativo
  onCerrar: () => void
  onGuardar: (datos: SecretoPeticion) => Promise<void>
}

/** El secreto anterior no se edita: se conserva como versión para el historial (RF04) */
export function SecretoModal({ abierto, credencial, familia, onCerrar, onGuardar }: SecretoModalProps) {
  const [tipo, setTipo] = useState<TipoAutenticacion>(credencial.tipoAutenticacion ?? 'PASSWORD')
  const [secreto, setSecreto] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()
  const siguiente = (credencial.numeroVersion ?? 0) + 1

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      await onGuardar({ tipoAutenticacion: tipo, secreto })
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
      titulo={`Nuevo secreto de ${credencial.nombre}`}
      descripcion={`Se registrará como versión ${siguiente}. Las versiones anteriores se conservan cifradas para el historial.`}
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-secreto" variante="primario" icono={KeyRound} cargando={guardando}>
            Registrar versión {siguiente}
          </Boton>
        </>
      }
    >
      <form id="form-secreto" onSubmit={guardar} className="flex flex-col gap-4" autoComplete="off">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <p className="rounded-lg bg-panel-muted px-3 py-2 text-sm text-ink-soft">
          Usuario de acceso: <span className="font-mono text-ink">{credencial.usuarioAcceso}</span>
        </p>
        <SelectorMecanismo valor={tipo} onCambiar={setTipo} familia={familia} />
        <CampoSecreto tipo={tipo} valor={secreto} onCambiar={setSecreto} error={errorDeCampo(error, 'secreto')} />
      </form>
    </Modal>
  )
}
