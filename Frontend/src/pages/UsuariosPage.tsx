import { useState } from 'react'
import type { FormEvent } from 'react'
import { Pencil, Plus, Power, PowerOff, Save } from 'lucide-react'
import type { Rol } from '@/api/dominio'
import type { UsuarioRespuesta } from '@/api/types'
import { usuariosApi } from '@/api/usuarios'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Campo, Entrada, Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { ETIQUETA_ROL } from '@/utils/etiquetas'
import { formatearFecha } from '@/utils/formato'
import { errorDeCampo, textoDeError, tieneErroresDeCampo } from '@/utils/errores'

const ROLES: Rol[] = ['ADMINISTRADOR', 'OPERADOR', 'RESPONSABLE']

/** Usuarios y roles (RF03, HU02). */
export function UsuariosPage() {
  const { usuario: sesion } = useSesion()
  const { avisar } = useAvisos()
  const usuarios = useConsulta(() => usuariosApi.listar(), [])
  const [editando, setEditando] = useState<UsuarioRespuesta | 'nuevo' | null>(null)
  const [codigo, setCodigo] = useState('')
  const [nombre, setNombre] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [rol, setRol] = useState<Rol>('RESPONSABLE')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  function abrir(u: UsuarioRespuesta | 'nuevo') {
    setError(undefined)
    setEditando(u)
    setCodigo(u === 'nuevo' ? '' : (u.codigo ?? ''))
    setNombre(u === 'nuevo' ? '' : (u.nombreCompleto ?? ''))
    setContrasena('')
    setRol(u === 'nuevo' ? 'RESPONSABLE' : (u.rol ?? 'RESPONSABLE'))
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      if (editando === 'nuevo') {
        await usuariosApi.crear({ codigo: codigo.trim(), nombreCompleto: nombre.trim(), contrasena, rol })
      } else if (editando?.id) {
        await usuariosApi.actualizar(editando.id, { nombreCompleto: nombre.trim(), rol })
      }
      setEditando(null)
      usuarios.recargar()
      avisar('Usuario guardado.')
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  async function cambiarEstado(u: UsuarioRespuesta) {
    try {
      await usuariosApi.cambiarEstado(u.id!, !u.activo)
      usuarios.recargar()
      avisar(u.activo ? 'Cuenta desactivada; su historial se conserva.' : 'Cuenta activada.')
    } catch (e) {
      avisar(textoDeError(e), 'error')
    }
  }

  return (
    <>
      <PageHeader
        title="Usuarios y roles"
        description="Las cuentas desactivadas conservan su historial y no pueden iniciar sesión."
        actions={
          <Boton variante="primario" icono={Plus} onClick={() => abrir('nuevo')}>
            Nuevo usuario
          </Boton>
        }
      />
      <MensajeError error={usuarios.error} onReintentar={usuarios.recargar} />
      {usuarios.cargando && !usuarios.datos ? (
        <Cargando />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-line bg-panel">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-4 py-3 font-medium">Usuario</th>
                <th className="px-4 py-3 font-medium">Rol</th>
                <th className="px-4 py-3 font-medium">Alta</th>
                <th className="px-4 py-3 font-medium">Estado</th>
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {usuarios.datos?.contenido?.map((u) => (
                <tr key={u.id} className="hover:bg-panel-muted">
                  <td className="px-4 py-3">
                    <p className="font-medium text-ink">{u.nombreCompleto}</p>
                    <p className="font-mono text-xs text-ink-faint">{u.codigo}</p>
                  </td>
                  <td className="px-4 py-3 text-ink-soft">{u.rol ? ETIQUETA_ROL[u.rol] : ''}</td>
                  <td className="px-4 py-3 text-ink-soft">{formatearFecha(u.fechaCreacion)}</td>
                  <td className="px-4 py-3">
                    <StatusPill tone={u.activo ? 'success' : 'neutral'} label={u.activo ? 'Activa' : 'Desactivada'} />
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <BotonIcono icono={Pencil} etiqueta="Editar" onClick={() => abrir(u)} />
                      {u.id !== sesion?.id && (
                        <BotonIcono icono={u.activo ? PowerOff : Power} etiqueta={u.activo ? 'Desactivar' : 'Activar'} onClick={() => cambiarEstado(u)} />
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editando && (
        <Modal
          abierto
          onCerrar={() => setEditando(null)}
          titulo={editando === 'nuevo' ? 'Nuevo usuario' : `Editar ${editando.codigo}`}
          pie={
            <>
              <Boton onClick={() => setEditando(null)}>Cancelar</Boton>
              <Boton type="submit" form="form-usuario" variante="primario" icono={Save} cargando={guardando}>
                Guardar
              </Boton>
            </>
          }
        >
          <form id="form-usuario" onSubmit={guardar} className="flex flex-col gap-4">
            {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
            {editando === 'nuevo' && (
              <Campo etiqueta="Código de usuario" obligatorio error={errorDeCampo(error, 'codigo')}>
                <Entrada value={codigo} onChange={(e) => setCodigo(e.target.value)} required autoFocus autoComplete="off" />
              </Campo>
            )}
            <Campo etiqueta="Nombre completo" obligatorio error={errorDeCampo(error, 'nombreCompleto')}>
              <Entrada value={nombre} onChange={(e) => setNombre(e.target.value)} required />
            </Campo>
            {editando === 'nuevo' && (
              <Campo etiqueta="Contraseña" obligatorio error={errorDeCampo(error, 'contrasena')} ayuda="Entre 8 y 72 caracteres.">
                <Entrada type="password" value={contrasena} onChange={(e) => setContrasena(e.target.value)} required autoComplete="new-password" />
              </Campo>
            )}
            <Campo etiqueta="Rol" obligatorio error={errorDeCampo(error, 'rol')}>
              <Selector value={rol} onChange={(e) => setRol(e.target.value as Rol)}>
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {ETIQUETA_ROL[r]}
                  </option>
                ))}
              </Selector>
            </Campo>
          </form>
        </Modal>
      )}
    </>
  )
}
