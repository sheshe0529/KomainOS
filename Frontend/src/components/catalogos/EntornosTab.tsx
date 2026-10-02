import { useState } from 'react'
import type { FormEvent } from 'react'
import { Pencil, Plus, Power, PowerOff, Save } from 'lucide-react'
import { catalogosApi } from '@/api/catalogos'
import type { EntornoRespuesta } from '@/api/types'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { errorDeCampo, textoDeError, tieneErroresDeCampo } from '@/utils/errores'

export function EntornosTab() {
  const { avisar } = useAvisos()
  const entornos = useConsulta(() => catalogosApi.entornos(), [])
  const [editando, setEditando] = useState<EntornoRespuesta | 'nuevo' | null>(null)
  const [nombre, setNombre] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  function abrir(entorno: EntornoRespuesta | 'nuevo') {
    setError(undefined)
    setEditando(entorno)
    setNombre(entorno === 'nuevo' ? '' : (entorno.nombre ?? ''))
    setDescripcion(entorno === 'nuevo' ? '' : (entorno.descripcion ?? ''))
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    const datos = { nombre: nombre.trim(), descripcion: descripcion.trim() || undefined }
    try {
      if (editando === 'nuevo') await catalogosApi.crearEntorno(datos)
      else if (editando?.id) await catalogosApi.actualizarEntorno(editando.id, datos)
      setEditando(null)
      entornos.recargar()
      avisar('Entorno guardado.')
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  async function cambiarEstado(e: EntornoRespuesta) {
    try {
      await catalogosApi.cambiarEstadoEntorno(e.id!, !e.activo)
      entornos.recargar()
      avisar(e.activo ? 'Entorno desactivado.' : 'Entorno activado.')
    } catch (err) {
      avisar(textoDeError(err), 'error')
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Boton variante="primario" icono={Plus} onClick={() => abrir('nuevo')}>
          Nuevo entorno
        </Boton>
      </div>
      <MensajeError error={entornos.error} onReintentar={entornos.recargar} />
      {entornos.cargando && !entornos.datos ? (
        <Cargando />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-line bg-panel">
          <table className="w-full min-w-[560px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-4 py-3 font-medium">Entorno</th>
                <th className="px-4 py-3 font-medium">Descripción</th>
                <th className="px-4 py-3 font-medium">Estado</th>
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {entornos.datos?.map((e) => (
                <tr key={e.id} className="hover:bg-panel-muted">
                  <td className="px-4 py-3 font-medium text-ink">{e.nombre}</td>
                  <td className="px-4 py-3 text-ink-soft">{e.descripcion ?? '—'}</td>
                  <td className="px-4 py-3">
                    <StatusPill tone={e.activo ? 'success' : 'neutral'} label={e.activo ? 'Activo' : 'Inactivo'} />
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <BotonIcono icono={Pencil} etiqueta="Editar" onClick={() => abrir(e)} />
                      <BotonIcono icono={e.activo ? PowerOff : Power} etiqueta={e.activo ? 'Desactivar' : 'Activar'} onClick={() => cambiarEstado(e)} />
                    </div>
                  </td>
                </tr>
              ))}
              {entornos.datos?.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-8 text-center text-ink-soft">
                    No hay entornos registrados.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {editando && (
        <Modal
          abierto
          onCerrar={() => setEditando(null)}
          titulo={editando === 'nuevo' ? 'Nuevo entorno' : `Editar ${editando.nombre}`}
          pie={
            <>
              <Boton onClick={() => setEditando(null)}>Cancelar</Boton>
              <Boton type="submit" form="form-entorno" variante="primario" icono={Save} cargando={guardando}>
                Guardar
              </Boton>
            </>
          }
        >
          <form id="form-entorno" onSubmit={guardar} className="flex flex-col gap-4">
            {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
            <Campo etiqueta="Nombre" obligatorio error={errorDeCampo(error, 'nombre')}>
              <Entrada value={nombre} onChange={(e) => setNombre(e.target.value)} required autoFocus />
            </Campo>
            <Campo etiqueta="Descripción" error={errorDeCampo(error, 'descripcion')}>
              <AreaTexto value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
            </Campo>
          </form>
        </Modal>
      )}
    </div>
  )
}
