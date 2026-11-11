import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import type { FamiliaSistemaOperativo, MetodoElevacion, TipoAutenticacion } from '@/api/dominio'
import type { CredencialPeticion, CredencialRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { llevaContrasenaElevacion, metodosAdmitidos } from '@/utils/elevacion'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { CamposElevacion, CampoSecreto, SelectorElevacion, SelectorMecanismo } from './CampoSecreto'
import { VistaAnsible } from './VistaAnsible'

interface CredencialFormularioProps {
  abierto: boolean
  titulo: string
  descripcion?: string
  /** documental: los métodos de elevación de la familia del servidor. cuenta: además muestra la configuración de Ansible */
  clase?: 'documental' | 'cuenta'
  /** Si se indica, edita sus datos identificativos: el secreto se cambia con «Nuevo secreto» */
  credencial?: CredencialRespuesta
  familia?: FamiliaSistemaOperativo
  ayudaNombre?: string
  onCerrar: () => void
  onGuardar: (datos: CredencialPeticion) => Promise<void>
}

/** Conexión (usuario y contraseña o llave) y, por separado, cómo obtiene privilegios Ansible (DEC-57) */
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
  const [metodo, setMetodo] = useState<MetodoElevacion>('NINGUNO')
  const [sudoPideContrasena, setSudoPideContrasena] = useState(false)
  const [secreto, setSecreto] = useState('')
  const [elevacion, setElevacion] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  const familiaFija = clase === 'documental' ? familia : undefined
  const windows = familiaFija === 'WINDOWS'
  const conContrasena = llevaContrasenaElevacion(metodo, sudoPideContrasena)

  function cambiarTipo(nuevo: TipoAutenticacion) {
    setTipo(nuevo)
    // runas es de Windows, que solo admite contraseña
    if (!metodosAdmitidos(familiaFija, nuevo).includes(metodo)) setMetodo('NINGUNO')
  }

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
        metodoElevacion: metodo,
        secreto,
        elevacionConContrasena: metodo === 'SUDO' ? sudoPideContrasena : undefined,
        secretoElevacion: conContrasena ? elevacion : undefined,
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
              <SelectorMecanismo valor={tipo} onCambiar={cambiarTipo} familia={familiaFija} />
            )}
            <CampoSecreto tipo={windows ? 'PASSWORD' : tipo} valor={secreto} onCambiar={setSecreto} error={errorDeCampo(error, 'secreto')} />
            <SelectorElevacion valor={metodo} onCambiar={setMetodo} familia={familiaFija} tipo={windows ? 'PASSWORD' : tipo} />
            <CamposElevacion
              metodo={metodo}
              conContrasena={sudoPideContrasena}
              onConContrasena={setSudoPideContrasena}
              valor={elevacion}
              onCambiar={setElevacion}
              error={errorDeCampo(error, 'secretoElevacion')}
            />
            {clase === 'cuenta' && (
              <VistaAnsible usuario={usuario} tipo={tipo} metodo={metodo} conContrasena={conContrasena} />
            )}
          </>
        )}
        {credencial && clase === 'cuenta' && credencial.tipoAutenticacion && (
          <VistaAnsible
            usuario={usuario}
            tipo={credencial.tipoAutenticacion}
            metodo={credencial.metodoElevacion ?? 'NINGUNO'}
            conContrasena={!!credencial.conContrasenaElevacion}
          />
        )}
      </form>
    </Modal>
  )
}
