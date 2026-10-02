import { useState } from 'react'
import type { FormEvent } from 'react'
import { KeyRound } from 'lucide-react'
import type { FamiliaSistemaOperativo, TipoAutenticacion, TipoUsuario } from '@/api/dominio'
import type { CredencialRespuesta, SecretoPeticion } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { CampoContrasena, CampoSecreto, SelectorMecanismo, SelectorTipoUsuario } from './CampoSecreto'

interface SecretoModalProps {
  abierto: boolean
  credencial: CredencialRespuesta
  clase?: 'documental' | 'cuenta'
  familia?: FamiliaSistemaOperativo
  onCerrar: () => void
  onGuardar: (datos: SecretoPeticion) => Promise<void>
}

/** El secreto anterior no se edita: se conserva como versión, y un campo vacío copia el vigente si sigue aplicando (RF04) */
export function SecretoModal({ abierto, credencial, clase = 'cuenta', familia, onCerrar, onGuardar }: SecretoModalProps) {
  const [tipo, setTipo] = useState<TipoAutenticacion>(credencial.tipoAutenticacion ?? 'PASSWORD')
  const [tipoUsuario, setTipoUsuario] = useState<TipoUsuario>(credencial.tipoUsuario ?? 'ADMINISTRADOR')
  const [secreto, setSecreto] = useState('')
  const [su, setSu] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()
  const siguiente = (credencial.numeroVersion ?? 0) + 1

  const windows = clase === 'documental' && familia === 'WINDOWS'
  const conTipoUsuario = clase === 'documental' && !windows
  const conSu = conTipoUsuario && tipoUsuario === 'GENERICO'
  const conservaSecreto = tipo === credencial.tipoAutenticacion
  const conservaSu = conSu && !!credencial.conSu

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      await onGuardar({
        tipoAutenticacion: windows ? 'PASSWORD' : tipo,
        tipoUsuario: conTipoUsuario ? tipoUsuario : undefined,
        secreto: secreto || undefined,
        secretoSu: conSu ? su || undefined : undefined,
      })
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
        {!windows && <SelectorMecanismo valor={tipo} onCambiar={setTipo} familia={clase === 'documental' ? familia : undefined} />}
        {conTipoUsuario && <SelectorTipoUsuario valor={tipoUsuario} onCambiar={setTipoUsuario} />}
        <CampoSecreto
          tipo={windows ? 'PASSWORD' : tipo}
          valor={secreto}
          onCambiar={setSecreto}
          obligatorio={!conservaSecreto}
          ayuda={conservaSecreto ? 'Déjela vacía para conservar la actual.' : undefined}
          error={errorDeCampo(error, 'secreto')}
        />
        {conSu && (
          <CampoContrasena
            etiqueta="Contraseña su (root)"
            valor={su}
            onCambiar={setSu}
            obligatorio={!conservaSu}
            ayuda={conservaSu ? 'Déjela vacía para conservar la actual.' : 'La que pide su para subir a root.'}
            error={errorDeCampo(error, 'secretoSu')}
          />
        )}
      </form>
    </Modal>
  )
}
