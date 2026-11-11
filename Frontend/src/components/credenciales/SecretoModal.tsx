import { useState } from 'react'
import type { FormEvent } from 'react'
import { KeyRound } from 'lucide-react'
import type { FamiliaSistemaOperativo, MetodoElevacion, TipoAutenticacion } from '@/api/dominio'
import type { CredencialRespuesta, SecretoPeticion } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { llevaContrasenaElevacion, metodosAdmitidos } from '@/utils/elevacion'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { CamposElevacion, CampoSecreto, SelectorElevacion, SelectorMecanismo } from './CampoSecreto'
import { VistaAnsible } from './VistaAnsible'

interface SecretoModalProps {
  abierto: boolean
  credencial: CredencialRespuesta
  clase?: 'documental' | 'cuenta'
  familia?: FamiliaSistemaOperativo
  onCerrar: () => void
  onGuardar: (datos: SecretoPeticion) => Promise<void>
}

/**
 * Rota el secreto y la elevación: reemplaza al vigente sin conservarlo, y un campo vacío copia el vigente si sigue
 * aplicando (RF04, R2.4, DEC-57)
 */
export function SecretoModal({ abierto, credencial, clase = 'cuenta', familia, onCerrar, onGuardar }: SecretoModalProps) {
  const [tipo, setTipo] = useState<TipoAutenticacion>(credencial.tipoAutenticacion ?? 'PASSWORD')
  const [metodo, setMetodo] = useState<MetodoElevacion>(credencial.metodoElevacion ?? 'NINGUNO')
  const [sudoPideContrasena, setSudoPideContrasena] = useState(
    credencial.metodoElevacion === 'SUDO' && !!credencial.conContrasenaElevacion,
  )
  const [secreto, setSecreto] = useState('')
  const [elevacion, setElevacion] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  const familiaFija = clase === 'documental' ? familia : undefined
  const windows = familiaFija === 'WINDOWS'
  const conContrasena = llevaContrasenaElevacion(metodo, sudoPideContrasena)
  const conservaSecreto = tipo === credencial.tipoAutenticacion
  // Solo con el mismo método: la contraseña de sudo no sirve como la de root
  const conservaElevacion = conContrasena && metodo === credencial.metodoElevacion && !!credencial.conContrasenaElevacion

  function cambiarTipo(nuevo: TipoAutenticacion) {
    setTipo(nuevo)
    if (!metodosAdmitidos(familiaFija, nuevo).includes(metodo)) setMetodo('NINGUNO')
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      await onGuardar({
        tipoAutenticacion: windows ? 'PASSWORD' : tipo,
        metodoElevacion: metodo,
        secreto: secreto || undefined,
        elevacionConContrasena: metodo === 'SUDO' ? sudoPideContrasena : undefined,
        secretoElevacion: conContrasena ? elevacion || undefined : undefined,
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
      descripcion="Reemplaza al secreto vigente, que no se conserva. El cambio queda en la auditoría, sin el secreto."
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-secreto" variante="primario" icono={KeyRound} cargando={guardando}>
            Reemplazar secreto
          </Boton>
        </>
      }
    >
      <form id="form-secreto" onSubmit={guardar} className="flex flex-col gap-4" autoComplete="off">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <p className="rounded-lg bg-panel-muted px-3 py-2 text-sm text-ink-soft">
          Usuario de acceso: <span className="font-mono text-ink">{credencial.usuarioAcceso}</span>
        </p>
        {!windows && <SelectorMecanismo valor={tipo} onCambiar={cambiarTipo} familia={familiaFija} />}
        <CampoSecreto
          tipo={windows ? 'PASSWORD' : tipo}
          valor={secreto}
          onCambiar={setSecreto}
          obligatorio={!conservaSecreto}
          ayuda={conservaSecreto ? 'Déjela vacía para conservar la actual.' : undefined}
          error={errorDeCampo(error, 'secreto')}
        />
        <SelectorElevacion valor={metodo} onCambiar={setMetodo} familia={familiaFija} tipo={windows ? 'PASSWORD' : tipo} />
        <CamposElevacion
          metodo={metodo}
          conContrasena={sudoPideContrasena}
          onConContrasena={setSudoPideContrasena}
          valor={elevacion}
          onCambiar={setElevacion}
          conserva={conservaElevacion}
          error={errorDeCampo(error, 'secretoElevacion')}
        />
        {clase === 'cuenta' && (
          <VistaAnsible
            usuario={credencial.usuarioAcceso ?? ''}
            tipo={tipo}
            metodo={metodo}
            conContrasena={conContrasena}
          />
        )}
      </form>
    </Modal>
  )
}
