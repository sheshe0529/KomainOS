import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ArrowLeft, FileCode2, Send, Sparkles } from 'lucide-react'
import { scriptsApi } from '@/api/automatizacion'
import type { FamiliaSistemaOperativo } from '@/api/dominio'
import type { RevisionSalidaRespuesta, ScriptPreparadoRespuesta } from '@/api/types'
import { PageHeader } from '@/components/common/PageHeader'
import { Tarjeta } from '@/components/common/Tarjeta'
import { AyudaContrato } from '@/components/automatizacion/AyudaContrato'
import { DiferenciasCodigo } from '@/components/automatizacion/DiferenciasCodigo'
import { EditorCodigo } from '@/components/automatizacion/EditorCodigo'
import { EditorParametros } from '@/components/automatizacion/EditorParametros'
import { RevisionSalida } from '@/components/automatizacion/RevisionSalida'
import { VistaPreviaScript } from '@/components/automatizacion/VistaPreviaScript'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada, Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { ETIQUETA_FAMILIA } from '@/utils/etiquetas'
import { ETIQUETA_TIPO_SCRIPT } from '@/utils/automatizacion'
import { aPeticion, editables, problemaLocal } from '@/utils/parametrosScript'
import type { Lenguaje, ParametroEditable } from '@/utils/parametrosScript'
import { EJEMPLO_SALIDA, plantilla } from '@/utils/plantillasScript'

type Pestana = 'vista' | 'cambios' | 'prueba'

interface Base {
  id: number
  etiqueta: string
  contenido: string
  parametros: ParametroEditable[]
}

/** Reemplazo del código pendiente de confirmar: una plantilla u otra versión de partida */
type Reemplazo = { titulo: string; contenido: string; parametros: ParametroEditable[]; idBase?: number }

/**
 * Publica una versión nueva de una implementación o una implementación nueva con su primera versión (RF25, RF26).
 * Las versiones son inmutables: lo publicado no se edita, se reemplaza por otra versión
 */
export function PublicarVersionPage() {
  const { id } = useParams()
  const idScript = Number(id)
  const [busqueda] = useSearchParams()
  const idImplementacion = Number(busqueda.get('implementacion')) || undefined
  const navigate = useNavigate()
  const { avisar } = useAvisos()

  const script = useConsulta(() => scriptsApi.obtener(idScript), [idScript])
  const catalogos = useCatalogos()

  const [idVersionSo, setIdVersionSo] = useState('')
  const [idBase, setIdBase] = useState<number>()
  const [contenido, setContenido] = useState('')
  const [parametros, setParametros] = useState<ParametroEditable[]>([])
  const [notas, setNotas] = useState('')
  const [inicial, setInicial] = useState<{ contenido: string; parametros: string }>()
  const [pestana, setPestana] = useState<Pestana>('vista')
  const [preparado, setPreparado] = useState<ScriptPreparadoRespuesta>()
  const [errorVista, setErrorVista] = useState<unknown>()
  const [salida, setSalida] = useState('')
  const [revision, setRevision] = useState<RevisionSalidaRespuesta>()
  const [revisando, setRevisando] = useState(false)
  const [error, setError] = useState<unknown>()
  const [publicando, setPublicando] = useState(false)
  const [reemplazo, setReemplazo] = useState<Reemplazo>()
  const [saliendo, setSaliendo] = useState(false)

  const s = script.datos
  const implementacion = s?.implementaciones?.find((i) => i.id === idImplementacion)
  const sistemas = catalogos.datos?.sistemasOperativos ?? []
  const versionSo = sistemas
    .flatMap((so) => (so.versiones ?? []).map((v) => ({ so, v })))
    .find(({ v }) => String(v.id) === idVersionSo)
  const familia: FamiliaSistemaOperativo | undefined = implementacion?.familia ?? versionSo?.so.familia
  const lenguaje: Lenguaje = familia === 'WINDOWS' ? 'powershell' : 'bash'
  const tipo = s?.tipo ?? 'MANTENIMIENTO'

  // Puede partir de cualquier versión de la misma familia: otra distribución de Linux suele servir de base
  const bases = useMemo<Base[]>(
    () =>
      (s?.implementaciones ?? [])
        .filter((i) => familia && i.familia === familia)
        .flatMap((i) =>
          (i.versiones ?? []).map((v) => ({
            id: v.id!,
            etiqueta: `${i.sistemaOperativo} · v${v.numero}${v.vigente ? ' (vigente)' : ''}`,
            contenido: v.contenido ?? '',
            parametros: editables(v.parametros?.map((p) => ({ ...p, nombre: p.nombre ?? '', tipo: p.tipo ?? 'STRING' }))),
          })),
        ),
    [s, familia],
  )
  const base = bases.find((b) => b.id === idBase)

  // Al cargar, la versión nueva parte de la indicada en la URL, de la vigente o de la última de la implementación
  useEffect(() => {
    if (!s || inicial) return
    const pedida = Number(busqueda.get('base')) || undefined
    const propia = implementacion?.versiones ?? []
    const desde =
      bases.find((b) => b.id === pedida) ??
      bases.find((b) => b.id === propia.find((v) => v.vigente)?.id) ??
      bases.find((b) => b.id === propia[0]?.id)
    if (implementacion && !desde) return
    setIdBase(desde?.id)
    setContenido(desde?.contenido ?? '')
    setParametros(desde?.parametros ?? [])
    setInicial({ contenido: desde?.contenido ?? '', parametros: JSON.stringify(aPeticion(desde?.parametros ?? [])) })
  }, [s, inicial, bases, busqueda, implementacion])

  const cambiado =
    !!inicial && (contenido !== inicial.contenido || JSON.stringify(aPeticion(parametros)) !== inicial.parametros)

  useEffect(() => {
    if (!cambiado) return
    const avisarSalida = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', avisarSalida)
    return () => window.removeEventListener('beforeunload', avisarSalida)
  }, [cambiado])

  // Vista previa en vivo, medio segundo después de dejar de escribir
  const firmaVista = JSON.stringify({ familia, contenido, parametros: aPeticion(parametros) })
  useEffect(() => {
    if (!familia) return
    let vigente = true
    const espera = window.setTimeout(() => {
      scriptsApi
        .vistaPrevia({ familia, contenido, parametros: aPeticion(parametros) })
        .then((r) => {
          if (!vigente) return
          setPreparado(r)
          setErrorVista(undefined)
        })
        .catch((e: unknown) => {
          if (vigente) setErrorVista(e)
        })
    }, 500)
    return () => {
      vigente = false
      window.clearTimeout(espera)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- la firma resume familia, contenido y parámetros
  }, [firmaVista])

  const problemasLocales = parametros.filter((p) => problemaLocal(p, parametros)).length
  const problemasServidor = preparado?.problemas?.length ?? 0
  const faltaSo = !implementacion && !idVersionSo
  const puedePublicar = !!contenido.trim() && !faltaSo && problemasLocales === 0 && problemasServidor === 0

  function pedirReemplazo(r: Reemplazo) {
    if (contenido.trim() && contenido !== r.contenido) setReemplazo(r)
    else aplicar(r)
  }

  function aplicar(r: Reemplazo) {
    setContenido(r.contenido)
    setParametros(r.parametros)
    if (r.idBase !== undefined) setIdBase(r.idBase)
    setReemplazo(undefined)
  }

  function usarPlantilla() {
    if (!familia) return
    const p = plantilla(tipo, familia)
    pedirReemplazo({ titulo: `Plantilla: ${p.titulo}`, contenido: p.contenido, parametros: editables(p.parametros) })
  }

  async function revisarSalida() {
    setRevisando(true)
    try {
      setRevision(await scriptsApi.revisarSalida(salida))
    } catch (e) {
      setError(e)
    } finally {
      setRevisando(false)
    }
  }

  async function publicar() {
    setPublicando(true)
    setError(undefined)
    try {
      const datos = { contenido, notasCambio: notas.trim() || undefined, parametros: aPeticion(parametros) }
      let destino = `/automatizacion/scripts/${idScript}`
      if (implementacion) {
        const version = await scriptsApi.publicar(implementacion.id!, datos)
        destino += `?implementacion=${implementacion.id}&version=${version.id}`
        avisar(`Versión ${version.numero} publicada. Las MOP ya generadas conservan la versión que fijaron.`)
      } else {
        const actualizado = await scriptsApi.implementar(idScript, { ...datos, idVersionSistemaOperativo: Number(idVersionSo) })
        const nueva = actualizado.implementaciones?.find((i) => i.idVersionSistemaOperativo === Number(idVersionSo))
        if (nueva) destino += `?implementacion=${nueva.id}`
        avisar('Implementación registrada con su primera versión habilitada.')
      }
      setInicial({ contenido, parametros: JSON.stringify(aPeticion(parametros)) })
      navigate(destino)
    } catch (e) {
      setError(e)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } finally {
      setPublicando(false)
    }
  }

  const volver = () => navigate(`/automatizacion/scripts/${idScript}${idImplementacion ? `?implementacion=${idImplementacion}` : ''}`)

  if (script.cargando && !s) return <Cargando />
  if (!s) return <MensajeError error={script.error} onReintentar={script.recargar} />

  if (idImplementacion && !implementacion) {
    return <MensajeError error={new Error('La implementación no pertenece a este script')} />
  }

  // Sistemas operativos sin implementación de este script: uno por versión de sistema operativo (RF22)
  const implementados = new Set(s.implementaciones?.map((i) => i.idVersionSistemaOperativo))
  const disponibles = sistemas
    .filter((so) => so.activo)
    .flatMap((so) => (so.versiones ?? []).filter((v) => v.activo && !implementados.has(v.id)).map((v) => ({ so, v })))

  const siguienteNumero = Math.max(0, ...(implementacion?.versiones ?? []).map((v) => v.numero ?? 0)) + 1

  return (
    <>
      <PageHeader
        title={implementacion ? `Nueva versión ${siguienteNumero}` : 'Nueva implementación'}
        description={`${s.nombre} · ${ETIQUETA_TIPO_SCRIPT[tipo]}${
          implementacion ? ` · ${implementacion.sistemaOperativo}` : ''
        }`}
        actions={
          <Boton icono={ArrowLeft} onClick={() => (cambiado ? setSaliendo(true) : volver())}>
            Volver al script
          </Boton>
        }
      />

      <div className="flex flex-col gap-6">
        <MensajeError error={error ?? catalogos.error} />

        {!implementacion && (
          <Tarjeta titulo="Sistema operativo">
            <div className="grid gap-4 md:grid-cols-[minmax(0,22rem)_1fr] md:items-end">
              <Campo etiqueta="Versión del sistema operativo" obligatorio>
                <Selector value={idVersionSo} onChange={(e) => setIdVersionSo(e.target.value)}>
                  <option value="">Seleccione…</option>
                  {disponibles.map(({ so, v }) => (
                    <option key={v.id} value={v.id}>
                      {so.nombre} {v.version} · {ETIQUETA_FAMILIA[so.familia ?? 'LINUX']}
                    </option>
                  ))}
                </Selector>
              </Campo>
              <p className="text-sm text-ink-soft">
                {disponibles.length === 0
                  ? 'El script ya está implementado en todos los sistemas operativos activos.'
                  : familia
                    ? `Se escribe en ${lenguaje === 'bash' ? 'Bash' : 'PowerShell'}. La implementación nace con esta primera versión habilitada.`
                    : 'El sistema operativo define el lenguaje: Bash para Linux y PowerShell para Windows.'}
              </p>
            </div>
          </Tarjeta>
        )}

        {familia && (
          <>
            <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
              <Tarjeta
                titulo={`Código ${lenguaje === 'bash' ? 'Bash' : 'PowerShell'}`}
                acciones={
                  <>
                    {bases.length > 0 && (
                      <div className="w-56">
                        <Selector
                          value={idBase ?? ''}
                          variante="pastilla"
                          aria-label="Partir de otra versión"
                          onChange={(e) => {
                            const elegida = bases.find((b) => String(b.id) === e.target.value)
                            if (elegida) {
                              pedirReemplazo({
                                titulo: `Partir de ${elegida.etiqueta}`,
                                contenido: elegida.contenido,
                                parametros: editables(aPeticion(elegida.parametros)),
                                idBase: elegida.id,
                              })
                            }
                          }}
                        >
                          <option value="">Partir de…</option>
                          {bases.map((b) => (
                            <option key={b.id} value={b.id}>
                              {b.etiqueta}
                            </option>
                          ))}
                        </Selector>
                      </div>
                    )}
                    <Boton variante="fantasma" icono={Sparkles} onClick={usarPlantilla}>
                      Plantilla
                    </Boton>
                  </>
                }
              >
                {!contenido.trim() && (
                  <div className="mb-3 flex flex-wrap items-center justify-between gap-3 rounded-lg bg-accent-soft px-4 py-3 text-sm text-ink">
                    <span>Empiece con una plantilla que ya cumple el contrato de KomainOS y adáptela.</span>
                    <Boton variante="primario" icono={Sparkles} onClick={usarPlantilla}>
                      Usar plantilla
                    </Boton>
                  </div>
                )}
                <EditorCodigo
                  valor={contenido}
                  onChange={setContenido}
                  lenguaje={lenguaje}
                  altoMinimo="22rem"
                  altoMaximo="40rem"
                  textoVacio={
                    lenguaje === 'bash'
                      ? '#!/usr/bin/env bash\n# Escriba el script o use una plantilla'
                      : '# Escriba el script o use una plantilla'
                  }
                />
                <p className="mt-2 text-xs text-ink-faint">
                  {contenido.split('\n').length} líneas · Ctrl+F busca · Tab sangra la selección
                </p>
              </Tarjeta>
              <AyudaContrato tipo={tipo} lenguaje={lenguaje} />
            </div>

            <Tarjeta titulo={`Parámetros (${parametros.length})`}>
              <EditorParametros parametros={parametros} onChange={setParametros} lenguaje={lenguaje} />
            </Tarjeta>

            <section className="rounded-xl border border-line bg-panel p-5">
              <div role="tablist" aria-label="Revisión antes de publicar" className="mb-4 flex flex-wrap gap-2">
                {(
                  [
                    ['vista', `Archivo que se ejecuta${problemasServidor ? ` (${problemasServidor} problemas)` : ''}`],
                    ...(base ? [['cambios', 'Cambios respecto de la base']] : []),
                    ...(tipo === 'ANALISIS' ? [['prueba', 'Probar una salida']] : []),
                  ] as [Pestana, string][]
                ).map(([clave, etiqueta]) => (
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

              {pestana === 'vista' && (
                <>
                  <MensajeError error={errorVista} />
                  {preparado ? (
                    <VistaPreviaScript preparado={preparado} nombreDescarga={s.nombre?.replace(/\W+/g, '_')} />
                  ) : (
                    <Cargando texto="Preparando la vista previa…" />
                  )}
                </>
              )}

              {pestana === 'cambios' && base && (
                <DiferenciasCodigo
                  anterior={base.contenido}
                  nuevo={contenido}
                  etiquetaAnterior={base.etiqueta}
                  etiquetaNueva="la versión nueva"
                />
              )}

              {pestana === 'prueba' && (
                <div className="flex flex-col gap-4">
                  <p className="text-sm text-ink-soft">
                    Ejecute el script a mano en un servidor de prueba (puede descargarlo desde la vista previa) y pegue
                    aquí lo que imprimió. KomainOS le indica qué métricas reconoce y cuáles faltan.
                  </p>
                  <AreaTexto
                    rows={6}
                    value={salida}
                    spellCheck={false}
                    placeholder={EJEMPLO_SALIDA}
                    onChange={(e) => setSalida(e.target.value)}
                    className="font-mono text-xs"
                    aria-label="Salida del script"
                  />
                  <div>
                    <Boton icono={FileCode2} cargando={revisando} disabled={!salida.trim()} onClick={revisarSalida}>
                      Revisar salida
                    </Boton>
                  </div>
                  {revision && <RevisionSalida revision={revision} />}
                </div>
              )}
            </section>
          </>
        )}
      </div>

      {familia && (
        <div className="sticky bottom-4 z-20 mt-6 rounded-xl border border-line bg-panel/95 p-4 shadow-lg backdrop-blur">
          <div className="flex flex-wrap items-end gap-3">
            <Campo etiqueta="Notas del cambio" className="min-w-[16rem] flex-1">
              <Entrada
                value={notas}
                maxLength={1000}
                placeholder="Qué cambia respecto de la versión anterior"
                onChange={(e) => setNotas(e.target.value)}
              />
            </Campo>
            <p className="pb-2 text-sm text-ink-soft">
              {!contenido.trim()
                ? 'Escriba el código'
                : faltaSo
                  ? 'Elija el sistema operativo'
                  : problemasLocales + problemasServidor > 0
                    ? `${problemasLocales + problemasServidor} problema(s) por corregir`
                    : 'Lista para publicar'}
            </p>
            <Boton variante="primario" icono={Send} cargando={publicando} disabled={!puedePublicar} onClick={publicar}>
              {implementacion ? `Publicar versión ${siguienteNumero}` : 'Registrar implementación'}
            </Boton>
          </div>
        </div>
      )}

      <Modal
        abierto={!!reemplazo}
        titulo="¿Reemplazar el código actual?"
        descripcion={reemplazo?.titulo}
        onCerrar={() => setReemplazo(undefined)}
        pie={
          <>
            <Boton onClick={() => setReemplazo(undefined)}>Conservar lo escrito</Boton>
            <Boton variante="primario" onClick={() => reemplazo && aplicar(reemplazo)}>
              Reemplazar
            </Boton>
          </>
        }
      >
        <p className="text-sm text-ink-soft">Se reemplazan el código y los parámetros que escribió hasta ahora.</p>
      </Modal>

      <Modal
        abierto={saliendo}
        titulo="¿Salir sin publicar?"
        onCerrar={() => setSaliendo(false)}
        pie={
          <>
            <Boton onClick={() => setSaliendo(false)}>Seguir editando</Boton>
            <Boton variante="peligro" onClick={volver}>
              Descartar cambios
            </Boton>
          </>
        }
      >
        <p className="text-sm text-ink-soft">Los cambios del código y los parámetros no se guardan hasta publicar.</p>
      </Modal>
    </>
  )
}
