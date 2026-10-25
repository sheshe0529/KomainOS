import { Link } from 'react-router-dom'
import { useState } from 'react'
import { Plus, Save } from 'lucide-react'
import { automatizacionApi } from '@/api/automatizacion'
import type { ScriptRespuesta, VersionScriptPeticion } from '@/api/types'
import { PageHeader } from '@/components/common/PageHeader'
import { ModoAutomatizacion } from '@/components/automatizacion/ModoAutomatizacion'
import { TablaAutomatizacion, celda } from '@/components/automatizacion/TablaAutomatizacion'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada, AreaTexto, Selector } from '@/components/ui/Campo'
import { Modal } from '@/components/ui/Modal'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useSesion } from '@/auth/sesion-context'
import { useAvisos } from '@/components/ui/avisos-context'
export function ScriptsPage() {
  const { tieneRol } = useSesion()
  const admin = tieneRol('ADMINISTRADOR')
  const { avisar } = useAvisos()
  const [pagina, setPagina] = useState(0)
  const scripts = useConsulta(() => automatizacionApi.scripts(pagina), [pagina])
  const catalogos = useCatalogos()
  const [editando, setEditando] = useState<ScriptRespuesta | 'nuevo'>()
  const [nombre, setNombre] = useState('')
  const [tipo, setTipo] = useState<'ANALISIS' | 'MANTENIMIENTO'>('ANALISIS')
  const [descripcion, setDescripcion] = useState('')
  const [activo, setActivo] = useState(true)
  const [historial, setHistorial] = useState<number>()
  const [paginaUsos, setPaginaUsos] = useState(0)
  const usos = useConsulta(
    () => (historial ? automatizacionApi.usosVersion(historial, paginaUsos) : Promise.resolve({ contenido: [] })),
    [historial, paginaUsos],
  )
  const [detalle, setDetalle] = useState<ScriptRespuesta>()
  const [so, setSo] = useState('')
  const [impl, setImpl] = useState<number>()
  const [contenido, setContenido] = useState('')
  const [notas, setNotas] = useState('')
  const [parametros, setParametros] = useState('[]')
  const [error, setError] = useState<unknown>()
  const [guardando, setGuardando] = useState(false)
  function editar(s: ScriptRespuesta | 'nuevo') {
    setEditando(s)
    setNombre(s === 'nuevo' ? '' : (s.nombre ?? ''))
    setTipo(s === 'nuevo' ? 'ANALISIS' : (s.tipo ?? 'ANALISIS'))
    setDescripcion(s === 'nuevo' ? '' : (s.descripcion ?? ''))
    setActivo(s === 'nuevo' || !!s.activo)
    setError(undefined)
  }
  async function accion(fn: () => Promise<unknown>, mensaje: string) {
    setGuardando(true)
    setError(undefined)
    try {
      await fn()
      scripts.recargar()
      avisar(mensaje)
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }
  function version(id: number, codigo = '', parametros = '[]') {
    setImpl(id)
    setContenido(codigo)
    setNotas('')
    setParametros(parametros)
    setError(undefined)
  }
  return (
    <>
      <PageHeader
        title="Scripts de análisis y mantenimiento"
        description="Implemente cada script por sistema operativo y publique versiones inmutables."
        actions={
          admin && (
            <Boton variante="primario" icono={Plus} onClick={() => editar('nuevo')}>
              Nuevo script
            </Boton>
          )
        }
      />
      <ModoAutomatizacion />
      <MensajeError error={scripts.error} />
      <TablaAutomatizacion
        pagina={scripts.datos}
        onPagina={setPagina}
        cabeceras={['Script', 'Tipo', 'Compatibilidad', 'Estado', 'Acciones']}
        fila={(s) => (
          <tr key={s.id}>
            <td className={celda}>
              <strong>{s.nombre}</strong>
              <p className="text-xs text-ink-soft">{s.descripcion}</p>
            </td>
            <td className={celda}>{s.tipo === 'ANALISIS' ? 'Análisis' : 'Mantenimiento'}</td>
            <td className={celda}>
              {s.implementaciones?.map((i) => (
                <span key={i.id} className="block text-xs">
                  {i.sistemaOperativo} · {i.versiones?.filter((v) => v.habilitada).length ?? 0} versiones habilitadas
                </span>
              ))}
            </td>
            <td className={celda}>{s.activo ? 'Activo' : 'Inactivo'}</td>
            <td className={celda}>
              <div className="flex gap-2">
                <Boton
                  onClick={() => {
                    setDetalle(s)
                    setSo('')
                    setError(undefined)
                  }}
                >
                  Implementaciones y versiones
                </Boton>
                {admin && <Boton onClick={() => editar(s)}>Editar</Boton>}
              </div>
            </td>
          </tr>
        )}
      />
      {editando && (
        <Modal
          abierto
          titulo={editando === 'nuevo' ? 'Nuevo script' : 'Editar script'}
          onCerrar={() => setEditando(undefined)}
          pie={
            <>
              <Boton onClick={() => setEditando(undefined)}>Cancelar</Boton>
              <Boton
                variante="primario"
                icono={Save}
                cargando={guardando}
                disabled={!nombre.trim()}
                onClick={() =>
                  accion(async () => {
                    await automatizacionApi.guardarScript(editando === 'nuevo' ? undefined : editando.id, {
                      nombre,
                      tipo,
                      descripcion,
                      activo,
                    })
                    setEditando(undefined)
                  }, 'Script guardado.')
                }
              >
                Guardar
              </Boton>
            </>
          }
        >
          <div className="flex flex-col gap-4">
            <MensajeError error={error} />
            <Campo etiqueta="Nombre" obligatorio>
              <Entrada value={nombre} maxLength={255} onChange={(e) => setNombre(e.target.value)} />
            </Campo>
            <Campo etiqueta="Tipo">
              <Selector
                value={tipo}
                disabled={editando !== 'nuevo'}
                onChange={(e) => setTipo(e.target.value as typeof tipo)}
              >
                <option value="ANALISIS">Análisis</option>
                <option value="MANTENIMIENTO">Mantenimiento</option>
              </Selector>
            </Campo>
            <Campo etiqueta="Descripción">
              <AreaTexto value={descripcion} maxLength={500} onChange={(e) => setDescripcion(e.target.value)} />
            </Campo>
            <label className="flex gap-2 text-sm">
              <input type="checkbox" checked={activo} onChange={(e) => setActivo(e.target.checked)} />
              Activo
            </label>
          </div>
        </Modal>
      )}
      {detalle && (
        <Modal abierto ancho="xl" titulo={detalle.nombre ?? 'Implementaciones'} onCerrar={() => setDetalle(undefined)}>
          <div className="flex flex-col gap-5">
            <MensajeError error={error} />
            <p className="text-sm text-ink-soft">
              La última versión habilitada se fija al generar la MOP. Las nuevas publicaciones conservan las versiones
              de los mantenimientos anteriores.
            </p>
            {admin && (
              <div className="flex items-end gap-2">
                <Campo etiqueta="Sistema operativo y versión" className="flex-1">
                  <Selector value={so} onChange={(e) => setSo(e.target.value)}>
                    <option value="">Seleccione…</option>
                    {catalogos.datos?.sistemasOperativos
                      .filter((s) => s.activo)
                      .flatMap((s) =>
                        s.versiones
                          ?.filter((v) => v.activo)
                          .map((v) => (
                            <option key={v.id} value={v.id}>
                              {s.nombre} {v.version}
                            </option>
                          )),
                      )}
                  </Selector>
                </Campo>
                <Boton
                  disabled={!so}
                  cargando={guardando}
                  onClick={() =>
                    accion(async () => {
                      const id = await automatizacionApi.implementar(detalle.id!, Number(so))
                      setDetalle({
                        ...detalle,
                        implementaciones: [
                          ...(detalle.implementaciones ?? []),
                          {
                            id,
                            idVersionSistemaOperativo: Number(so),
                            sistemaOperativo: 'Nueva implementación',
                            versiones: [],
                          },
                        ],
                      })
                      setSo('')
                    }, 'Implementación registrada.')
                  }
                >
                  Agregar implementación
                </Boton>
              </div>
            )}
            {detalle.implementaciones?.map((i) => (
              <section key={i.id} className="rounded-xl border border-line p-4">
                <div className="mb-3 flex justify-between gap-3">
                  <h4 className="font-semibold">{i.sistemaOperativo}</h4>
                  {admin && (
                    <Boton
                      icono={Plus}
                      onClick={() =>
                        version(
                          i.id!,
                          i.versiones?.[0]?.contenido,
                          JSON.stringify(i.versiones?.[0]?.parametros ?? [], null, 2),
                        )
                      }
                    >
                      Publicar versión
                    </Boton>
                  )}
                </div>
                {i.versiones?.map((v) => (
                  <details key={v.id} className="border-t border-line py-3">
                    <summary className="cursor-pointer text-sm">
                      Versión {v.numero} · {v.habilitada ? 'Habilitada' : 'Deshabilitada'} · {v.notasCambio}
                    </summary>
                    <pre className="my-3 max-h-72 overflow-auto rounded-lg bg-panel-muted p-3 text-xs">
                      {v.contenido}
                    </pre>
                    {v.parametros?.length ? (
                      <pre className="mb-3 overflow-auto text-xs text-ink-soft">
                        {JSON.stringify(v.parametros, null, 2)}
                      </pre>
                    ) : null}
                    <Boton
                      onClick={() => {
                        setHistorial(v.id)
                        setPaginaUsos(0)
                      }}
                    >
                      Ver tareas con esta versión
                    </Boton>
                    {admin && (
                      <Boton
                        onClick={() =>
                          accion(async () => {
                            await automatizacionApi.habilitarVersion(v.id!, !v.habilitada)
                            setDetalle({
                              ...detalle,
                              implementaciones: detalle.implementaciones?.map((x) =>
                                x.id === i.id
                                  ? {
                                      ...x,
                                      versiones: x.versiones?.map((y) =>
                                        y.id === v.id ? { ...y, habilitada: !v.habilitada } : y,
                                      ),
                                    }
                                  : x,
                              ),
                            })
                          }, 'Estado actualizado.')
                        }
                      >
                        {v.habilitada ? 'Deshabilitar' : 'Habilitar'}
                      </Boton>
                    )}
                  </details>
                ))}
              </section>
            ))}
          </div>
        </Modal>
      )}
      {impl && (
        <Modal
          abierto
          ancho="xl"
          titulo="Publicar nueva versión"
          onCerrar={() => setImpl(undefined)}
          pie={
            <>
              <Boton onClick={() => setImpl(undefined)}>Cancelar</Boton>
              <Boton
                variante="primario"
                cargando={guardando}
                disabled={!contenido.trim()}
                onClick={() =>
                  accion(async () => {
                    const datos: VersionScriptPeticion = {
                      contenido,
                      notasCambio: notas,
                      parametros: JSON.parse(parametros),
                    }
                    await automatizacionApi.publicar(impl, datos)
                    setImpl(undefined)
                    setDetalle(undefined)
                  }, 'Versión publicada sin alterar las anteriores.')
                }
              >
                Publicar versión
              </Boton>
            </>
          }
        >
          <div className="flex flex-col gap-4">
            <MensajeError error={error} />
            <Campo
              etiqueta="Código ejecutable"
              obligatorio
              ayuda="Bash para Linux o PowerShell para Windows. Los análisis deben devolver el contrato JSON de métricas."
            >
              <AreaTexto
                className="min-h-72 font-mono text-xs"
                value={contenido}
                maxLength={131072}
                onChange={(e) => setContenido(e.target.value)}
              />
            </Campo>
            <Campo etiqueta="Notas del cambio">
              <Entrada value={notas} maxLength={1000} onChange={(e) => setNotas(e.target.value)} />
            </Campo>
            <Campo
              etiqueta="Parámetros (JSON)"
              ayuda={'Ejemplo: [{"nombre":"dias","tipo":"INTEGER","obligatorio":true,"valorPorDefecto":"7"}]'}
            >
              <AreaTexto
                className="font-mono text-xs"
                value={parametros}
                onChange={(e) => setParametros(e.target.value)}
              />
            </Campo>
          </div>
        </Modal>
      )}
      {historial && (
        <Modal abierto ancho="xl" titulo={`Uso de la versión #${historial}`} onCerrar={() => setHistorial(undefined)}>
          <MensajeError error={usos.error} />
          <TablaAutomatizacion
            pagina={usos.datos}
            onPagina={setPaginaUsos}
            cabeceras={['Orden', 'Servidor', 'Tarea', 'Estado']}
            fila={(t) => (
              <tr key={`${t.idOrden}/${t.servidor}/${t.tarea}`}>
                <td className={celda}>
                  <Link className="text-accent" to={`/ordenes/${t.idOrden}`}>
                    {t.orden}
                  </Link>
                </td>
                <td className={celda}>{t.servidor}</td>
                <td className={celda}>{t.tarea}</td>
                <td className={celda}>{t.estado?.replaceAll('_', ' ')}</td>
              </tr>
            )}
          />
        </Modal>
      )}
    </>
  )
}
