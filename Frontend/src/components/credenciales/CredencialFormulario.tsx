import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import type { FamiliaSistemaOperativo, TipoAutenticacion } from '@/api/dominio'
import type { CredencialPeticion, CredencialRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { CampoSecreto, SelectorMecanismo } from './CampoSecreto'

interface CredencialFormularioProps {
  abierto: boolean
  titulo: string
  descripcion?: string
  /** Si se indica, edita sus datos identificativos: el secreto se cambia con una versión nueva */
  credencial?: CredencialRespuesta
  familia?: FamiliaSistemaOperativo
  ayudaNombre?: string
  onCerrar: () => void
  onGuardar: (datos: CredencialPeticion) => Promise<void>
}

export function CredencialFormulario({
  abierto,
  titulo,
  descripcion,
  credencial,
  familia,
  ayudaNombre,
  onCerrar,
  onGuardar,
}: CredencialFormularioProps) {
  const [nombre, setNombre] = useState(credencial?.nombre ?? '')
  const [usuario, setUsuario] = useState(credencial?.usuarioAcceso ?? '')
  const [detalle, setDetalle] = useState(credencial?.descripcion ?? '')
  const [tipo, setTipo] = useState<TipoAutenticacion>('PASSWORD')
  const [secreto, setSecreto] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      await onGuardar({
        nombre: nombre.trim(),
        usuarioAcceso: usuario.trim(),
        descripcion: detalle.trim() || undefined,
        tipoAutenticacion: tipo,
        secreto,
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
      titulo={titulo}
      descripcion={descripcion}
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-credencial" variante="primario" icono={Save} cargando={guardando}>
            Guardar
          </Boton>
        </>
      }
    >
      <form id="form-credencial" onSubmit={guardar} className="flex flex-col gap-4" autoComplete="off">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="Nombre" obligatorio error={errorDeCampo(error, 'nombre')} ayuda={ayudaNombre}>
            <Entrada value={nombre} onChange={(e) => setNombre(e.target.value)} maxLength={255} required autoFocus />
          </Campo>
          <Campo etiqueta="Usuario de acceso" obligatorio error={errorDeCampo(error, 'usuarioAcceso')}>
            <Entrada
              value={usuario}
              onChange={(e) => setUsuario(e.target.value)}
              maxLength={255}
              required
              className="font-mono"
              spellCheck={false}
            />
          </Campo>
        </div>
        <Campo etiqueta="Descripción" error={errorDeCampo(error, 'descripcion')}>
          <AreaTexto value={detalle} onChange={(e) => setDetalle(e.target.value)} maxLength={500} rows={2} />
        </Campo>
        {!credencial && (
          <>
            <SelectorMecanismo valor={tipo} onCambiar={setTipo} familia={familia} />
            <CampoSecreto tipo={tipo} valor={secreto} onCambiar={setSecreto} error={errorDeCampo(error, 'secreto')} />
          </>
        )}
      </form>
    </Modal>
  )
}
