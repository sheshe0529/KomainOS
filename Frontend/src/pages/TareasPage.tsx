import { useState } from 'react'
import { Plus } from 'lucide-react'
import { automatizacionApi } from '@/api/automatizacion'
import { umbralesApi } from '@/api/umbrales'
import type { TareaRespuesta, TareaPeticion } from '@/api/types'
import { PageHeader } from '@/components/common/PageHeader'
import { TablaAutomatizacion, celda } from '@/components/automatizacion/TablaAutomatizacion'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada, Selector, AreaTexto } from '@/components/ui/Campo'
import { Modal } from '@/components/ui/Modal'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { useSesion } from '@/auth/sesion-context'
const vacio: TareaPeticion = {
  idScript: 0,
  nombre: '',
  descripcion: '',
  nivelAfectacion: 'SIN_AFECTACION',
  prioridad: 10,
  duracionEstimadaMinutos: 5,
  activo: true,
  idsUmbrales: [],
}
export function TareasPage() {
  const { tieneRol } = useSesion()
  const admin = tieneRol('ADMINISTRADOR')
  const [pagina, setPagina] = useState(0)
  const lista = useConsulta(() => automatizacionApi.tareas(pagina), [pagina])
  const scripts = useConsulta(automatizacionApi.todosScripts, [])
  const umbrales = useConsulta(() => umbralesApi.umbrales(), [])
  const [editar, setEditar] = useState<TareaRespuesta | 'nueva'>()
  const [f, setF] = useState(vacio)
  const [error, setError] = useState<unknown>()
  const [guardando, setGuardando] = useState(false)
  function abrir(t: TareaRespuesta | 'nueva') {
    setEditar(t)
    setError(undefined)
    setF(
      t === 'nueva'
        ? { ...vacio }
        : {
            idScript: t.idScript!,
            nombre: t.nombre ?? '',
            descripcion: t.descripcion,
            nivelAfectacion: t.nivelAfectacion ?? 'SIN_AFECTACION',
            prioridad: t.prioridad ?? 10,
            duracionEstimadaMinutos: t.duracionEstimadaMinutos ?? 5,
            activo: !!t.activo,
            idsUmbrales: t.idsUmbrales ?? [],
          },
    )
  }
  async function guardar() {
    setGuardando(true)
    setError(undefined)
    try {
      await automatizacionApi.guardarTarea(editar === 'nueva' ? undefined : editar?.id, f)
      setEditar(undefined)
      lista.recargar()
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }
  return (
    <>
      <PageHeader
        title="Tareas de mantenimiento"
        description="Relacione umbrales con controles de mantenimiento. Una tarea se incluye una sola vez en cada MOP."
        actions={
          admin && (
            <Boton variante="primario" icono={Plus} onClick={() => abrir('nueva')}>
              Nueva tarea
            </Boton>
          )
        }
      />
      <MensajeError error={lista.error} />
      <TablaAutomatizacion
        pagina={lista.datos}
        onPagina={setPagina}
        cabeceras={['Tarea', 'Script', 'Afectación', 'Prioridad', 'Duración', 'Estado', 'Acciones']}
        fila={(t) => (
          <tr key={t.id}>
            <td className={celda}>
              <strong>{t.nombre}</strong>
              <p className="text-xs text-ink-soft">{t.descripcion}</p>
            </td>
            <td className={celda}>{t.script}</td>
            <td className={celda}>{t.nivelAfectacion?.replaceAll('_', ' ')}</td>
            <td className={celda}>{t.prioridad}</td>
            <td className={celda}>{t.duracionEstimadaMinutos} min</td>
            <td className={celda}>{t.activo ? 'Activa' : 'Inactiva'}</td>
            <td className={celda}>{admin && <Boton onClick={() => abrir(t)}>Editar</Boton>}</td>
          </tr>
        )}
      />
      {editar && (
        <Modal
          abierto
          ancho="lg"
          titulo={editar === 'nueva' ? 'Nueva tarea' : 'Editar tarea'}
          onCerrar={() => setEditar(undefined)}
          pie={
            <>
              <Boton onClick={() => setEditar(undefined)}>Cancelar</Boton>
              <Boton
                variante="primario"
                cargando={guardando}
                disabled={!f.nombre.trim() || !f.idScript}
                onClick={guardar}
              >
                Guardar tarea
              </Boton>
            </>
          }
        >
          <div className="flex flex-col gap-4">
            <MensajeError error={error} />
            <Campo etiqueta="Nombre">
              <Entrada value={f.nombre} maxLength={255} onChange={(e) => setF({ ...f, nombre: e.target.value })} />
            </Campo>
            <Campo etiqueta="Descripción">
              <AreaTexto
                value={f.descripcion ?? ''}
                maxLength={500}
                onChange={(e) => setF({ ...f, descripcion: e.target.value })}
              />
            </Campo>
            <Campo etiqueta="Script de mantenimiento">
              <Selector value={f.idScript || ''} onChange={(e) => setF({ ...f, idScript: Number(e.target.value) })}>
                <option value="">Seleccione…</option>
                {scripts.datos
                  ?.filter((s) => s.tipo === 'MANTENIMIENTO')
                  .map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.nombre}
                    </option>
                  ))}
              </Selector>
            </Campo>
            <div className="grid grid-cols-3 gap-3">
              <Campo etiqueta="Afectación">
                <Selector
                  value={f.nivelAfectacion}
                  onChange={(e) => setF({ ...f, nivelAfectacion: e.target.value as TareaPeticion['nivelAfectacion'] })}
                >
                  {['SIN_AFECTACION', 'PARCIAL', 'TOTAL'].map((n) => (
                    <option key={n} value={n}>
                      {n.replaceAll('_', ' ')}
                    </option>
                  ))}
                </Selector>
              </Campo>
              <Campo etiqueta="Prioridad" ayuda="Menor valor, primero">
                <Entrada
                  type="number"
                  min={0}
                  value={f.prioridad}
                  onChange={(e) => setF({ ...f, prioridad: Number(e.target.value) })}
                />
              </Campo>
              <Campo etiqueta="Duración (min)">
                <Entrada
                  type="number"
                  min={1}
                  value={f.duracionEstimadaMinutos}
                  onChange={(e) => setF({ ...f, duracionEstimadaMinutos: Number(e.target.value) })}
                />
              </Campo>
            </div>
            <fieldset>
              <legend className="mb-2 text-sm font-medium">Umbrales que activan la tarea</legend>
              <div className="max-h-48 overflow-auto rounded-lg border border-line p-3">
                {umbrales.datos?.map((u) => (
                  <label key={u.id} className="mb-2 flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={f.idsUmbrales.includes(u.id!)}
                      onChange={(e) =>
                        setF({
                          ...f,
                          idsUmbrales: e.target.checked
                            ? [...f.idsUmbrales, u.id!]
                            : f.idsUmbrales.filter((id) => id !== u.id),
                        })
                      }
                    />
                    {u.nombre}
                  </label>
                ))}
              </div>
            </fieldset>
            <label className="flex gap-2 text-sm">
              <input type="checkbox" checked={f.activo} onChange={(e) => setF({ ...f, activo: e.target.checked })} />
              Activa
            </label>
          </div>
        </Modal>
      )}
    </>
  )
}
