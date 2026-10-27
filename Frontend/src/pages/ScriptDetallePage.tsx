import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ArrowLeft, CopyPlus, FlaskConical, Pencil, Plus, Power, PowerOff } from 'lucide-react'
import { scriptsApi } from '@/api/automatizacion'
import type { ImplementacionRespuesta, ScriptRespuesta, VersionRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { DiferenciasCodigo } from '@/components/automatizacion/DiferenciasCodigo'
import { EditorCodigo } from '@/components/automatizacion/EditorCodigo'
import { EditorParametros } from '@/components/automatizacion/EditorParametros'
import { ProbarAnalisisModal } from '@/components/automatizacion/ProbarAnalisisModal'
import { ScriptFormulario } from '@/components/automatizacion/ScriptFormulario'
import { VistaPreviaScript } from '@/components/automatizacion/VistaPreviaScript'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { ESTADO_TAREA_MOP, ETIQUETA_TIPO_SCRIPT } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'
import { editables } from '@/utils/parametrosScript'
import type { Lenguaje } from '@/utils/parametrosScript'

type Pestana = 'codigo' | 'archivo' | 'parametros' | 'cambios' | 'uso'

function lenguajeDe(i?: ImplementacionRespuesta): Lenguaje {
  return i?.familia === 'WINDOWS' ? 'powershell' : 'bash'
}

/** RF26: tareas de MOP que fijaron o ejecutaron la versión */
function UsoVersion({ idVersion }: { idVersion: number }) {
  const [pagina, setPagina] = useState(0)
  const usos = useConsulta(() => scriptsApi.usosVersion(idVersion, pagina), [idVersion, pagina])
  const filas = usos.datos?.contenido ?? []

  if (usos.cargando && !usos.datos) return <Cargando />
  if (usos.error) return <MensajeError error={usos.error} onReintentar={usos.recargar} />
  if (filas.length === 0) {
    return <p className="text-sm text-ink-soft">Ninguna MOP ha fijado esta versión todavía.</p>
  }

  return (
    <div className="overflow-hidden rounded-lg border border-line">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[620px] text-left text-sm">
          <thead>
            <tr className="border-b border-line bg-panel-muted text-xs uppercase tracking-wide text-ink-faint">
              <th className="px-3 py-2 font-medium">Orden</th>
              <th className="px-3 py-2 font-medium">Servidor</th>
              <th className="px-3 py-2 font-medium">Tarea</th>
              <th className="px-3 py-2 font-medium">Estado</th>
              <th className="px-3 py-2 font-medium">Inicio</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {filas.map((u) => (
              <tr key={u.idTareaMop}>
                <td className="px-3 py-2">
                  <Link to={`/ordenes/${u.idOrden}`} className="font-mono text-accent hover:underline">
                    {u.orden}
                  </Link>
                </td>
                <td className="px-3 py-2 font-mono">{u.servidor}</td>
                <td className="px-3 py-2">{u.tarea}</td>
                <td className="px-3 py-2">
                  {u.estado && <StatusPill tone={ESTADO_TAREA_MOP[u.estado].tono} label={ESTADO_TAREA_MOP[u.estado].etiqueta} />}
                </td>
                <td className="px-3 py-2 text-ink-soft">{formatearFechaHora(u.inicio)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Paginacion
        pagina={pagina}
        totalPaginas={usos.datos?.totalPaginas ?? 0}
        totalElementos={usos.datos?.totalElementos ?? 0}
        onCambiar={setPagina}
      />
    </div>
  )
}

function ArchivoVersion({ idVersion, nombre }: { idVersion: number; nombre?: string }) {
  const preparado = useConsulta(() => scriptsApi.vistaPreviaVersion(idVersion), [idVersion])
  if (preparado.cargando && !preparado.datos) return <Cargando texto="Preparando el archivo…" />
  if (!preparado.datos) return <MensajeError error={preparado.error} onReintentar={preparado.recargar} />
  return <VistaPreviaScript preparado={preparado.datos} nombreDescarga={nombre} />
}

interface DetalleVersionProps {
  script: ScriptRespuesta
  implementacion: ImplementacionRespuesta
  version: VersionRespuesta
  anterior?: VersionRespuesta
  esAdmin: boolean
  onCambiarEstado: () => void
  onProbar: () => void
}

function DetalleVersion({ script, implementacion, version, anterior, esAdmin, onCambiarEstado, onProbar }: DetalleVersionProps) {
  const [pestana, setPestana] = useState<Pestana>('codigo')
  const lenguaje = lenguajeDe(implementacion)
  const parametros = version.parametros ?? []
  const nombreArchivo = `${script.nombre}_${implementacion.sistemaOperativo}_v${version.numero}`.replace(/\W+/g, '_')

  const pestanas: [Pestana, string][] = [
    ['codigo', 'Código'],
    ['archivo', 'Archivo que se ejecuta'],
    ['parametros', `Parámetros (${parametros.length})`],
    ...(anterior ? ([['cambios', `Cambios desde v${anterior.numero}`]] as [Pestana, string][]) : []),
    ['uso', 'Uso en MOP'],
  ]

  return (
    <section className="flex min-w-0 flex-col gap-4 rounded-xl border border-line bg-panel p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="flex flex-wrap items-center gap-2 text-base font-semibold text-ink">
            Versión {version.numero}
            {version.vigente && <StatusPill tone="success" label="Vigente" />}
            {!version.habilitada && <StatusPill tone="neutral" label="Deshabilitada" />}
          </h3>
          <p className="mt-0.5 text-sm text-ink-soft">
            Publicada el {formatearFechaHora(version.fechaCreacion)} · {lenguaje === 'bash' ? 'Bash' : 'PowerShell'}
          </p>
        </div>
        {esAdmin && (
          <div className="flex flex-wrap gap-2">
            {script.tipo === 'ANALISIS' && version.habilitada && (
              <Boton icono={FlaskConical} onClick={onProbar}>
                Probar en un servidor
              </Boton>
            )}
            <Link
              to={`/automatizacion/scripts/${script.id}/publicar?implementacion=${implementacion.id}&base=${version.id}`}
              className="inline-flex items-center justify-center gap-2 rounded-lg border border-line bg-panel px-3.5 py-2 text-sm font-medium text-ink transition hover:bg-panel-muted"
            >
              <CopyPlus className="h-4 w-4" aria-hidden="true" />
              Nueva versión a partir de esta
            </Link>
            <Boton icono={version.habilitada ? PowerOff : Power} onClick={onCambiarEstado}>
              {version.habilitada ? 'Deshabilitar' : 'Habilitar'}
            </Boton>
          </div>
        )}
      </div>

      {version.notasCambio && (
        <p className="rounded-lg bg-panel-muted px-3 py-2 text-sm text-ink">
          <span className="text-ink-soft">Notas del cambio: </span>
          {version.notasCambio}
        </p>
      )}

      <div role="tablist" aria-label={`Versión ${version.numero}`} className="flex flex-wrap gap-2">
        {pestanas.map(([clave, etiqueta]) => (
          <button
            key={clave}
            role="tab"
            type="button"
            aria-selected={pestana === clave}
            onClick={() => setPestana(clave)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              pestana === clave ? 'bg-accent text-accent-ink' : 'bg-panel-muted text-ink-soft hover:text-ink'
            }`}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      {pestana === 'codigo' && (
        <EditorCodigo valor={version.contenido ?? ''} lenguaje={lenguaje} soloLectura altoMaximo="34rem" />
      )}
      {pestana === 'archivo' && <ArchivoVersion idVersion={version.id!} nombre={nombreArchivo} />}
      {pestana === 'parametros' && (
        <EditorParametros
          parametros={editables(parametros.map((p) => ({ ...p, nombre: p.nombre ?? '', tipo: p.tipo ?? 'STRING' })))}
          onChange={() => undefined}
          lenguaje={lenguaje}
          soloLectura
        />
      )}
      {pestana === 'cambios' && anterior && (
        <DiferenciasCodigo
          anterior={anterior.contenido ?? ''}
          nuevo={version.contenido ?? ''}
          etiquetaAnterior={`v${anterior.numero}`}
          etiquetaNueva={`v${version.numero}`}
        />
      )}
      {pestana === 'uso' && <UsoVersion idVersion={version.id!} />}
    </section>
  )
}

/** Ficha del script: implementaciones por sistema operativo y versiones inmutables (RF22, RF25, RF26, HU17) */
export function ScriptDetallePage() {
  const { id } = useParams()
  const idScript = Number(id)
  const [busqueda, setBusqueda] = useSearchParams()
  const navigate = useNavigate()
  const { avisar } = useAvisos()
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')

  const script = useConsulta(() => scriptsApi.obtener(idScript), [idScript])
  const [editando, setEditando] = useState(false)
  const [cambioEstado, setCambioEstado] = useState<VersionRespuesta>()
  const [probando, setProbando] = useState(false)
  const [error, setError] = useState<unknown>()
  const [guardando, setGuardando] = useState(false)

  const s = script.datos
  if (script.cargando && !s) return <Cargando />
  if (!s) return <MensajeError error={script.error} onReintentar={script.recargar} />

  const implementaciones = s.implementaciones ?? []
  const implementacion =
    implementaciones.find((i) => String(i.id) === busqueda.get('implementacion')) ?? implementaciones[0]
  const versiones = implementacion?.versiones ?? []
  const version =
    versiones.find((v) => String(v.id) === busqueda.get('version')) ?? versiones.find((v) => v.vigente) ?? versiones[0]
  const anterior = version ? versiones.find((v) => (v.numero ?? 0) < (version.numero ?? 0)) : undefined

  const elegir = (idImplementacion?: number, idVersion?: number) => {
    const parametros = new URLSearchParams()
    if (idImplementacion) parametros.set('implementacion', String(idImplementacion))
    if (idVersion) parametros.set('version', String(idVersion))
    setBusqueda(parametros, { replace: true })
  }

  async function cambiarEstado() {
    if (!cambioEstado) return
    setGuardando(true)
    setError(undefined)
    try {
      const v = await scriptsApi.cambiarEstadoVersion(cambioEstado.id!, !cambioEstado.habilitada)
      avisar(v.habilitada ? `Versión ${v.numero} habilitada.` : `Versión ${v.numero} deshabilitada.`)
      setCambioEstado(undefined)
      script.recargar()
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <>
      <PageHeader
        title={s.nombre ?? 'Script'}
        description={s.descripcion || DESCRIPCION_VACIA}
        actions={
          <>
            <Boton icono={ArrowLeft} onClick={() => navigate('/automatizacion/scripts')}>
              Scripts
            </Boton>
            {esAdmin && (
              <>
                <Boton icono={Pencil} onClick={() => setEditando(true)}>
                  Editar datos
                </Boton>
                <Boton variante="primario" icono={Plus} onClick={() => navigate(`/automatizacion/scripts/${idScript}/publicar`)}>
                  Nueva implementación
                </Boton>
              </>
            )}
          </>
        }
      />

      <div className="mb-6 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-ink-soft">
        <span>
          Tipo: <span className="font-medium text-ink">{ETIQUETA_TIPO_SCRIPT[s.tipo ?? 'MANTENIMIENTO']}</span>
        </span>
        <StatusPill tone={s.activo ? 'success' : 'neutral'} label={s.activo ? 'Activo' : 'Inactivo'} />
        {s.tipo === 'MANTENIMIENTO' && (
          <span>
            <Link to="/automatizacion/tareas" className="text-accent hover:underline">
              {s.tareasActivas ?? 0} {s.tareasActivas === 1 ? 'tarea activa' : 'tareas activas'}
            </Link>{' '}
            lo usan
          </span>
        )}
      </div>

      <MensajeError error={error} />

      {implementaciones.length === 0 ? (
        <div className="rounded-xl border border-dashed border-line bg-panel px-5 py-10 text-center">
          <p className="text-sm text-ink-soft">
            Este script todavía no tiene código. Agregue una implementación para cada sistema operativo donde deba
            ejecutarse.
          </p>
          {esAdmin && (
            <Boton
              variante="primario"
              icono={Plus}
              className="mt-4"
              onClick={() => navigate(`/automatizacion/scripts/${idScript}/publicar`)}
            >
              Agregar la primera implementación
            </Boton>
          )}
        </div>
      ) : (
        <>
          <div role="tablist" aria-label="Implementaciones por sistema operativo" className="mb-4 flex flex-wrap gap-2">
            {implementaciones.map((i) => (
              <button
                key={i.id}
                role="tab"
                type="button"
                aria-selected={i.id === implementacion?.id}
                onClick={() => elegir(i.id)}
                className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors ${
                  i.id === implementacion?.id
                    ? 'border-accent bg-accent-soft font-medium text-ink'
                    : 'border-line bg-panel text-ink-soft hover:text-ink'
                }`}
              >
                {i.sistemaOperativo}
                <span
                  className={`rounded px-1.5 font-mono text-xs ${i.versionVigente ? 'bg-panel-muted' : 'bg-warning-soft text-warning'}`}
                >
                  {i.versionVigente ? `v${i.versionVigente}` : 'sin versión'}
                </span>
              </button>
            ))}
          </div>

          {implementacion && (
            <div className="grid gap-6 lg:grid-cols-[16rem_minmax(0,1fr)]">
              <aside className="flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-ink">Versiones</h3>
                  {esAdmin && (
                    <Link
                      to={`/automatizacion/scripts/${idScript}/publicar?implementacion=${implementacion.id}`}
                      className="inline-flex items-center gap-1 text-sm font-medium text-accent hover:underline"
                    >
                      <Plus className="h-4 w-4" aria-hidden="true" />
                      Nueva
                    </Link>
                  )}
                </div>
                {!implementacion.versionVigente && (
                  <p className="rounded-lg bg-warning-soft px-3 py-2 text-xs text-warning">
                    Ninguna versión habilitada: las MOP nuevas no pueden incluir este script en{' '}
                    {implementacion.sistemaOperativo}.
                  </p>
                )}
                <ol className="flex flex-col gap-2">
                  {versiones.map((v) => (
                    <li key={v.id}>
                      <button
                        type="button"
                        onClick={() => elegir(implementacion.id, v.id)}
                        aria-current={v.id === version?.id}
                        className={`w-full rounded-lg border px-3 py-2 text-left transition-colors ${
                          v.id === version?.id ? 'border-accent bg-accent-soft' : 'border-line bg-panel hover:bg-panel-muted'
                        }`}
                      >
                        <span className="flex items-center justify-between gap-2">
                          <span className={`font-mono text-sm font-medium ${v.habilitada ? 'text-ink' : 'text-ink-faint line-through'}`}>
                            v{v.numero}
                          </span>
                          {v.vigente ? (
                            <span className="text-xs font-medium text-success">Vigente</span>
                          ) : (
                            !v.habilitada && <span className="text-xs text-ink-faint">Deshabilitada</span>
                          )}
                        </span>
                        <span className="block text-xs text-ink-faint">{formatearFechaHora(v.fechaCreacion)}</span>
                        {v.notasCambio && <span className="mt-1 line-clamp-2 block text-xs text-ink-soft">{v.notasCambio}</span>}
                      </button>
                    </li>
                  ))}
                </ol>
                <p className="text-xs text-ink-faint">
                  La vigente es la última habilitada: es la que se fija al generar una MOP. Las MOP ya generadas conservan
                  su versión.
                </p>
              </aside>

              {version && (
                <DetalleVersion
                  key={version.id}
                  script={s}
                  implementacion={implementacion}
                  version={version}
                  anterior={anterior}
                  esAdmin={esAdmin}
                  onCambiarEstado={() => setCambioEstado(version)}
                  onProbar={() => setProbando(true)}
                />
              )}
            </div>
          )}
        </>
      )}

      {editando && (
        <ScriptFormulario
          script={s}
          onCerrar={() => setEditando(false)}
          onGuardado={(guardado) => {
            setEditando(false)
            script.reemplazar(guardado)
            avisar('Datos del script actualizados.')
          }}
        />
      )}

      <Modal
        abierto={!!cambioEstado}
        titulo={cambioEstado?.habilitada ? `¿Deshabilitar la versión ${cambioEstado?.numero}?` : `¿Habilitar la versión ${cambioEstado?.numero}?`}
        onCerrar={() => setCambioEstado(undefined)}
        pie={
          <>
            <Boton onClick={() => setCambioEstado(undefined)}>Cancelar</Boton>
            <Boton variante={cambioEstado?.habilitada ? 'peligro' : 'primario'} cargando={guardando} onClick={cambiarEstado}>
              {cambioEstado?.habilitada ? 'Deshabilitar' : 'Habilitar'}
            </Boton>
          </>
        }
      >
        <p className="text-sm text-ink-soft">
          {cambioEstado?.habilitada
            ? 'Las MOP nuevas fijarán la versión habilitada anterior. Las MOP ya generadas conservan esta versión.'
            : 'Si es la más reciente habilitada, pasa a ser la vigente para las MOP nuevas.'}
        </p>
      </Modal>

      {probando && version && implementacion && (
        <ProbarAnalisisModal
          idVersion={version.id!}
          numeroVersion={version.numero}
          idVersionSistemaOperativo={implementacion.idVersionSistemaOperativo}
          sistemaOperativo={implementacion.sistemaOperativo}
          onCerrar={() => setProbando(false)}
        />
      )}
    </>
  )
}

const DESCRIPCION_VACIA = 'Sin descripción'
