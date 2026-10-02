import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import type { FamiliaSistemaOperativo, TipoAutenticacion, TipoUsuario } from '@/api/dominio'
import type { CredencialPeticion, CredencialRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { CampoContrasena, CampoSecreto, SelectorMecanismo, SelectorTipoUsuario } from './CampoSecreto'

interface CredencialFormularioProps {
  abierto: boolean
  titulo: string
  descripcion?: string
  /** documental: Linux pide tipo de usuario y, si es Genérico, la contraseña su. Windows solo usuario y contraseña (DEC-39) */
  clase?: 'documental' | 'cuenta'
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
  clase = 'cuenta',
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
  const [tipoUsuario, setTipoUsuario] = useState<TipoUsuario>('ADMINISTRADOR')
  const [secreto, setSecreto] = useState('')
  const [su, setSu] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  const windows = clase === 'documental' && familia === 'WINDOWS'
  const conTipoUsuario = clase === 'documental' && !windows
  const conSu = conTipoUsuario && tipoUsuario === 'GENERICO'

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      await onGuardar({
        nombre: nombre.trim(),
        usuarioAcceso: usuario.trim(),
        descripcion: detalle.trim() || undefined,
        tipoAutenticacion: windows ? 'PASSWORD' : tipo,
        tipoUsuario: conTipoUsuario ? tipoUsuario : undefined,
        secreto,
        secretoSu: conSu ? su : undefined,
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
            {windows ? (
              <p className="rounded-lg bg-panel-muted px-3 py-2 text-xs text-ink-soft">
                Servidor Windows: se registra solo usuario y contraseña (WinRM).
              </p>
            ) : (
              <SelectorMecanismo valor={tipo} onCambiar={setTipo} familia={clase === 'documental' ? familia : undefined} />
            )}
            {conTipoUsuario && <SelectorTipoUsuario valor={tipoUsuario} onCambiar={setTipoUsuario} />}
            <CampoSecreto tipo={windows ? 'PASSWORD' : tipo} valor={secreto} onCambiar={setSecreto} error={errorDeCampo(error, 'secreto')} />
            {conSu && (
              <CampoContrasena
                etiqueta="Contraseña su (root)"
                valor={su}
                onCambiar={setSu}
                error={errorDeCampo(error, 'secretoSu')}
                ayuda="La que pide su para subir a root."
              />
            )}
          </>
        )}
      </form>
    </Modal>
  )
}
