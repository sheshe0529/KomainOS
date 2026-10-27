import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  Activity,
  Archive,
  ClipboardCheck,
  ClipboardList,
  Database,
  History,
  Play,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Zap,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { operacionApi } from '@/api/automatizacion'
import type { EtapaOrden } from '@/api/dominio'
import type { ResumenProcesoRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { DiagnosticoAutomatizacion } from '@/components/automatizacion/DiagnosticoAutomatizacion'
import { IndicadoresMantenimiento } from '@/components/automatizacion/IndicadoresMantenimiento'
import type { ObjetivoProgramacion } from '@/components/planificacion/ProgramarModal'
import { SeleccionObjetivoModal } from '@/components/planificacion/SeleccionObjetivoModal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Selector } from '@/components/ui/Campo'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { useTextoDiferido } from '@/hooks/useTextoDiferido'
import { TONO_ESTADO_DETALLE, TONO_ESTADO_ORDEN } from '@/utils/automatizacion'
import { ETIQUETA_ESTADO_DETALLE, ETIQUETA_ESTADO_ORDEN, ETIQUETA_ETAPA, ETIQUETA_ORIGEN } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'

const ETAPAS: { etapa: EtapaOrden; icono: LucideIcon; detalle: string }[] = [
  { etapa: 'PLANIFICACION', icono: ClipboardList, detalle: 'Programadas, a la espera de su evaluación' },
  { etapa: 'EVALUACION', icono: Activity, detalle: 'Analizando servidores y generando MOP' },
  { etapa: 'AUTORIZACION', icono: ShieldCheck, detalle: 'Esperando aprobaciones' },
  { etapa: 'EJECUCION', icono: Play, detalle: 'En cola o ejecutando tareas' },
  { etapa: 'VALIDACION', icono: ClipboardCheck, detalle: 'Esperando al responsable' },
  { etapa: 'INCIDENCIA', icono: ShieldAlert, detalle: 'Requieren atención' },
  { etapa: 'CIERRE', icono: Archive, detalle: 'Ciclos terminados' },
]

type Vista = 'ordenes' | 'cola' | 'indicadores'

/** La sala se actualiza sola mientras está a la vista */
const REFRESCO_MS = 10_000

/** Sala de operaciones (HU22): el ciclo de mantenimiento de un vistazo, la cola por servidor y los indicadores */
export function AutomatizacionPage() {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()
  const catalogos = useCatalogos()

  const [vista, setVista] = useState<Vista>('ordenes')
  const [busqueda, setBusqueda] = useState('')
  const texto = useTextoDiferido(busqueda)
  const [etapa, setEtapa] = useState<EtapaOrden | ''>('')
  const [idEntorno, setIdEntorno] = useState('')
  const [idCriticidad, setIdCriticidad] = useState('')
  const [pagina, setPagina] = useState(0)
  const [paginaCola, setPaginaCola] = useState(0)

  const diagnostico = useConsulta(() => operacionApi.diagnostico(), [])
  const indicadores = useConsulta(() => operacionApi.indicadores(), [])
  const sala = useConsulta(
    () => operacionApi.sala({ etapa, idEntorno, idCriticidad, texto }, pagina),
    [etapa, idEntorno, idCriticidad, texto, pagina],
  )
  const cola = useConsulta(() => operacionApi.cola(paginaCola), [paginaCola])

  const [trabajando, setTrabajando] = useState<'procesar' | 'cola' | 'demo' | 'lanzar'>()
  const [error, setError] = useState<unknown>()
  const [resumen, setResumen] = useState<ResumenProcesoRespuesta>()
  const [eligiendo, setEligiendo] = useState(false)
  const [objetivo, setObjetivo] = useState<ObjetivoProgramacion>()
  const [motivo, setMotivo] = useState('')
  const [fueraVentana, setFueraVentana] = useState(false)

  const { recargar: recargarIndicadores } = indicadores
  const { recargar: recargarSala } = sala
  const { recargar: recargarCola } = cola

  useEffect(() => {
    const t = window.setInterval(() => {
      if (document.visibilityState !== 'visible') return
      recargarIndicadores()
      recargarSala()
      recargarCola()
    }, REFRESCO_MS)
    return () => window.clearInterval(t)
  }, [recargarIndicadores, recargarSala, recargarCola])

  function recargarTodo() {
    recargarIndicadores()
    recargarSala()
    recargarCola()
  }

  async function ejecutar(clave: NonNullable<typeof trabajando>, accion: () => Promise<void>) {
    setTrabajando(clave)
    setError(undefined)
    try {
      await accion()
      recargarTodo()
    } catch (e) {
      setError(e)
    } finally {
      setTrabajando(undefined)
    }
  }

  const procesar = () =>
    ejecutar('procesar', async () => {
      setResumen(await operacionApi.procesar())
    })

  const despacharCola = () =>
    ejecutar('cola', async () => {
      const r = await operacionApi.ejecutarCola()
      avisar(r.mensaje ?? `${r.despachados} servidores despachados.`)
    })

  const cargarDemostracion = () =>
    ejecutar('demo', async () => {
      const agregados = await operacionApi.demostracion()
      const total = Object.values(agregados).reduce((a, b) => a + b, 0)
      avisar(
        total === 0
          ? 'Los datos de demostración ya estaban cargados.'
          : `Demostración cargada: ${Object.entries(agregados)
              .filter(([, n]) => n > 0)
              .map(([clave, n]) => `${n} ${clave}`)
              .join(', ')}.`,
      )
    })

  const lanzar = () =>
    ejecutar('lanzar', async () => {
      if (!objetivo) return
      const orden = await operacionApi.inmediata({
        idServidor: objetivo.idServidor,
        idGrupo: objetivo.idGrupo,
        motivo: motivo.trim(),
        confirmarFueraVentana: fueraVentana,
      })
      setObjetivo(undefined)
      avisar(`Orden ${orden.nombre} creada: su evaluación previa está en curso.`)
      navigate(`/ordenes/${orden.id}`)
    })

  const filasSala = sala.datos?.contenido ?? []
  const filasCola = cola.datos?.contenido ?? []

  return (
    <>
      <PageHeader
        title="Sala de operaciones"
        description="El ciclo de mantenimiento de un vistazo: en qué etapa está cada orden y qué espera para avanzar."
        actions={
          <>
            <Link
              to="/automatizacion/ejecuciones"
              className="inline-flex items-center gap-2 rounded-lg border border-line bg-panel px-3.5 py-2 text-sm font-medium text-ink transition hover:bg-panel-muted"
            >
              <History className="h-4 w-4" aria-hidden="true" />
              Historial de ejecución
            </Link>
            {esAdmin && (
              <>
                <Boton icono={RefreshCw} cargando={trabajando === 'procesar'} onClick={procesar}>
                  Procesar ciclo ahora
                </Boton>
                <Boton variante="primario" icono={Zap} onClick={() => setEligiendo(true)}>
                  Lanzamiento inmediato
                </Boton>
              </>
            )}
          </>
        }
      />

      {diagnostico.datos && <DiagnosticoAutomatizacion diagnostico={diagnostico.datos} />}

      <MensajeError error={error ?? diagnostico.error} />

      {resumen && (
        <section className="mb-6 rounded-xl border border-line bg-panel px-5 py-4" aria-live="polite">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h3 className="text-sm font-semibold text-ink">
                {resumen.ejecutado ? 'Ciclo procesado' : 'El ciclo ya se estaba procesando'}
              </h3>
              <p className="mt-0.5 text-sm text-ink-soft">
                {resumen.evaluadas} evaluaciones iniciadas · {resumen.revisadas} servidores revisados ·{' '}
                {resumen.despachadas} despachados a ejecución · {resumen.vencidas} plazos vencidos
              </p>
            </div>
            <Boton variante="fantasma" onClick={() => setResumen(undefined)}>
              Cerrar
            </Boton>
          </div>
          {(resumen.mensajes ?? []).length > 0 && (
            <ul className="mt-3 flex list-disc flex-col gap-1 pl-5 text-sm text-ink-soft">
              {resumen.mensajes?.map((m, i) => (
                <li key={i}>{m}</li>
              ))}
            </ul>
          )}
        </section>
      )}

      <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">
        {ETAPAS.map(({ etapa: e, icono: Icono, detalle }) => {
          const elegida = etapa === e
          return (
            <button
              key={e}
              type="button"
              aria-pressed={elegida}
              title={detalle}
              onClick={() => {
                setEtapa(elegida ? '' : e)
                setVista('ordenes')
                setPagina(0)
              }}
              className={`rounded-xl border p-4 text-left transition-colors ${
                elegida ? 'border-accent bg-accent-soft' : 'border-line bg-panel hover:border-ink-faint'
              }`}
            >
              <span className="flex items-center gap-2 text-xs text-ink-soft">
                <Icono className="h-4 w-4" aria-hidden="true" />
                {ETIQUETA_ETAPA[e]}
              </span>
              <strong className="mt-2 block font-mono text-2xl font-semibold tabular-nums text-ink">
                {indicadores.datos?.[e] ?? 0}
              </strong>
            </button>
          )
        })}
      </div>

      <div role="tablist" aria-label="Vistas de la sala" className="mb-4 flex flex-wrap items-center gap-2">
        {(
          [
            ['ordenes', 'Órdenes'],
            ['cola', `Cola por servidor${cola.datos?.totalElementos ? ` (${cola.datos.totalElementos})` : ''}`],
            ['indicadores', 'Indicadores de gestión'],
          ] as [Vista, string][]
        ).map(([clave, etiqueta]) => (
          <button
            key={clave}
            role="tab"
            type="button"
            aria-selected={vista === clave}
            onClick={() => setVista(clave)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              vista === clave ? 'bg-accent text-accent-ink' : 'bg-panel-muted text-ink-soft hover:text-ink'
            }`}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      {vista === 'ordenes' && (
        <>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <CampoBusqueda
              valor={busqueda}
              onCambiar={(t) => {
                setBusqueda(t)
                setPagina(0)
              }}
              placeholder="Buscar por código, servidor o grupo"
              aria-label="Buscar órdenes"
            />
            <Selector
              aria-label="Filtrar por etapa"
              className="!w-auto"
              value={etapa}
              onChange={(e) => {
                setEtapa(e.target.value as EtapaOrden | '')
                setPagina(0)
              }}
            >
              <option value="">Todas las etapas</option>
              {ETAPAS.map(({ etapa: e }) => (
                <option key={e} value={e}>
                  {ETIQUETA_ETAPA[e]}
                </option>
              ))}
            </Selector>
            <Selector
              aria-label="Filtrar por entorno"
              className="!w-auto"
              value={idEntorno}
              onChange={(e) => {
                setIdEntorno(e.target.value)
                setPagina(0)
              }}
            >
              <option value="">Todos los entornos</option>
              {catalogos.datos?.entornos.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.nombre}
                </option>
              ))}
            </Selector>
            <Selector
              aria-label="Filtrar por criticidad"
              className="!w-auto"
              value={idCriticidad}
              onChange={(e) => {
                setIdCriticidad(e.target.value)
                setPagina(0)
              }}
            >
              <option value="">Todas las criticidades</option>
              {catalogos.datos?.criticidades.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nombre}
                </option>
              ))}
            </Selector>
          </div>

          <MensajeError error={sala.error} onReintentar={sala.recargar} />
          {sala.cargando && !sala.datos ? (
            <Cargando />
          ) : filasSala.length === 0 ? (
            <p className="rounded-xl border border-line bg-panel px-5 py-10 text-center text-sm text-ink-soft">
              Ninguna orden coincide con los filtros.
            </p>
          ) : (
            <div className="overflow-hidden rounded-xl border border-line bg-panel">
              <div className="overflow-x-auto">
                <table className="w-full min-w-[820px] text-left text-sm">
                  <thead>
                    <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                      <th className="px-5 py-3 font-medium">Orden</th>
                      <th className="px-3 py-3 font-medium">Objetivo</th>
                      <th className="px-3 py-3 font-medium">Estado</th>
                      <th className="px-3 py-3 font-medium">Origen</th>
                      <th className="px-3 py-3 font-medium">Inicio programado</th>
                      <th className="px-3 py-3 font-medium">Criticidad</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {filasSala.map((o) => (
                      <tr key={o.id} onClick={() => navigate(`/ordenes/${o.id}`)} className="cursor-pointer hover:bg-panel-muted">
                        <td className="px-5 py-3">
                          <Link
                            to={`/ordenes/${o.id}`}
                            onClick={(e) => e.stopPropagation()}
                            className="font-mono font-medium text-ink hover:text-accent hover:underline"
                          >
                            {o.codigo}
                          </Link>
                          {o.prioridad === 'ALTA' && (
                            <span className="ml-2 rounded bg-danger-soft px-1.5 py-0.5 text-xs font-medium text-danger">
                              Alta
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-3">
                          {o.objetivo?.nombre}
                          {o.tipoObjetivo === 'GRUPO' && (
                            <span className="block text-xs text-ink-faint">{o.cantidadServidores} servidores</span>
                          )}
                        </td>
                        <td className="px-3 py-3">
                          {o.estado && <StatusPill tone={TONO_ESTADO_ORDEN[o.estado]} label={ETIQUETA_ESTADO_ORDEN[o.estado]} />}
                        </td>
                        <td className="px-3 py-3 text-ink-soft">{o.origen ? ETIQUETA_ORIGEN[o.origen] : '—'}</td>
                        <td className="px-3 py-3 whitespace-nowrap text-ink-soft">{formatearFechaHora(o.inicioProgramado)}</td>
                        <td className="px-3 py-3 text-ink-soft">{o.criticidad?.nombre}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Paginacion
                pagina={pagina}
                totalPaginas={sala.datos?.totalPaginas ?? 0}
                totalElementos={sala.datos?.totalElementos ?? 0}
                onCambiar={setPagina}
              />
            </div>
          )}
        </>
      )}

      {vista === 'cola' && (
        <>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-ink-soft">
              Un servidor ejecuta una sola MOP a la vez; el piloto de un grupo va primero y, si falla, el resto no
              inicia.
            </p>
            {esAdmin && (
              <Boton icono={Play} cargando={trabajando === 'cola'} onClick={despacharCola}>
                Despachar cola ahora
              </Boton>
            )}
          </div>
          <MensajeError error={cola.error} onReintentar={cola.recargar} />
          {cola.cargando && !cola.datos ? (
            <Cargando />
          ) : filasCola.length === 0 ? (
            <p className="rounded-xl border border-line bg-panel px-5 py-10 text-center text-sm text-ink-soft">
              La cola está vacía.
            </p>
          ) : (
            <div className="overflow-hidden rounded-xl border border-line bg-panel">
              <div className="overflow-x-auto">
                <table className="w-full min-w-[760px] text-left text-sm">
                  <thead>
                    <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                      <th className="px-5 py-3 text-right font-medium">#</th>
                      <th className="px-3 py-3 font-medium">Servidor</th>
                      <th className="px-3 py-3 font-medium">Orden</th>
                      <th className="px-3 py-3 font-medium">Estado</th>
                      <th className="px-3 py-3 font-medium">Inicio previsto</th>
                      <th className="px-3 py-3 font-medium">Inicio real</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {filasCola.map((d) => (
                      <tr key={d.idDetalle}>
                        <td className="px-5 py-3 text-right font-mono tabular-nums text-ink-soft">{d.posicion}</td>
                        <td className="px-3 py-3">
                          <Link to={`/servidores/${d.idServidor}`} className="font-mono text-ink hover:text-accent hover:underline">
                            {d.servidor}
                          </Link>
                          {d.piloto && (
                            <span className="ml-2 rounded bg-accent-soft px-1.5 py-0.5 text-xs text-ink">Piloto</span>
                          )}
                        </td>
                        <td className="px-3 py-3">
                          <Link to={`/ordenes/${d.idOrden}`} className="font-mono text-accent hover:underline">
                            {d.orden}
                          </Link>
                          {d.prioridad === 'ALTA' && <span className="ml-2 text-xs font-medium text-danger">Alta</span>}
                        </td>
                        <td className="px-3 py-3">
                          {d.estado && (
                            <StatusPill tone={TONO_ESTADO_DETALLE[d.estado]} label={ETIQUETA_ESTADO_DETALLE[d.estado]} />
                          )}
                        </td>
                        <td className="px-3 py-3 whitespace-nowrap text-ink-soft">{formatearFechaHora(d.inicioPrevisto)}</td>
                        <td className="px-3 py-3 whitespace-nowrap text-ink-soft">{formatearFechaHora(d.inicioReal)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Paginacion
                pagina={paginaCola}
                totalPaginas={cola.datos?.totalPaginas ?? 0}
                totalElementos={cola.datos?.totalElementos ?? 0}
                onCambiar={setPaginaCola}
              />
            </div>
          )}
        </>
      )}

      {vista === 'indicadores' && <IndicadoresMantenimiento />}

      {esAdmin && diagnostico.datos?.simulado && (
        <div className="mt-8 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-dashed border-line px-5 py-4 text-sm text-ink-soft">
          <span>
            ¿Primera vez? Cargue scripts, tareas, umbrales y políticas genéricas para recorrer el ciclo completo en modo
            simulado.
          </span>
          <Boton icono={Database} cargando={trabajando === 'demo'} onClick={cargarDemostracion}>
            Cargar demostración
          </Boton>
        </div>
      )}

      {eligiendo && (
        <SeleccionObjetivoModal
          abierto
          onCerrar={() => setEligiendo(false)}
          onElegir={(o) => {
            setEligiendo(false)
            setObjetivo(o)
            setMotivo('')
            setFueraVentana(false)
          }}
        />
      )}

      <Modal
        abierto={!!objetivo}
        ancho="lg"
        titulo="Lanzamiento inmediato"
        descripcion={objetivo?.nombre}
        onCerrar={() => trabajando !== 'lanzar' && setObjetivo(undefined)}
        pie={
          <>
            <Boton disabled={trabajando === 'lanzar'} onClick={() => setObjetivo(undefined)}>
              Cancelar
            </Boton>
            <Boton variante="primario" icono={Zap} cargando={trabajando === 'lanzar'} disabled={!motivo.trim()} onClick={lanzar}>
              Lanzar ahora
            </Boton>
          </>
        }
      >
        <div className="flex flex-col gap-4">
          <MensajeError error={error} />
          <ol className="flex list-decimal flex-col gap-1 pl-5 text-sm text-ink-soft">
            <li>Se crea una orden de prioridad alta que inicia ya su evaluación previa.</li>
            <li>La MOP pasa por las aprobaciones que exige la política del entorno.</li>
            <li>Se respeta la exclusión por servidor: si otro mantenimiento está en curso, espera su turno.</li>
          </ol>
          <Campo etiqueta="Motivo" obligatorio>
            <AreaTexto value={motivo} maxLength={1000} onChange={(e) => setMotivo(e.target.value)} />
          </Campo>
          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              checked={fueraVentana}
              onChange={(e) => setFueraVentana(e.target.checked)}
              className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
            />
            <span>
              <span className="font-medium text-ink">Autorizo iniciar fuera de la ventana permisiva</span>
              <span className="block text-xs text-ink-soft">
                Solo se usa si ahora está fuera de la ventana: sin ella el lanzamiento se rechaza. Queda registrada.
              </span>
            </span>
          </label>
        </div>
      </Modal>
    </>
  )
}
