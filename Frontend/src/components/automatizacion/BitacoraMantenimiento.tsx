import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Check, Circle, Download, Minus, X } from 'lucide-react'
import { bitacoraApi } from '@/api/automatizacion'
import type { ResultadoMantenimiento } from '@/api/dominio'
import type { MantenimientoBitacora, ParticipacionServidor, TareaBitacora } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { OrdenesDelObjetivo } from '@/components/planificacion/OrdenesDelObjetivo'
import { useConsulta } from '@/hooks/useConsulta'
import { formatearSegundos, NIVEL } from '@/utils/automatizacion'
import {
  aniosDe,
  fechaDe,
  ORDEN_RESULTADOS,
  periodoDe,
  posicionEnPeriodo,
  RESULTADO,
  resumenBitacora,
  TONO_NIVEL,
} from '@/utils/bitacora'
import { ETIQUETA_ORIGEN } from '@/utils/etiquetas'
import { formatearFecha, formatearFechaHora } from '@/utils/formato'
import { ExportarBitacoraModal, type OpcionAlcance } from './ExportarBitacoraModal'

interface BitacoraMantenimientoProps {
  idServidor?: number
  idGrupo?: number
  /** Integrantes del grupo, para exportar la bitácora de cada uno */
  integrantes?: { id?: number; hostname?: string }[]
  /** Cambiarlo recarga la bitácora y las órdenes (por ejemplo, después de programar) */
  version?: number
}

type Vista = 'linea' | 'ordenes'
type Filtro = 'todos' | 'bien' | 'problemas' | 'pendientes'

const FILTROS: { valor: Filtro; etiqueta: string; resultados?: ResultadoMantenimiento[] }[] = [
  { valor: 'todos', etiqueta: 'Todos' },
  { valor: 'bien', etiqueta: 'Terminaron bien', resultados: ['EXITOSO', 'SIN_INTERVENCION'] },
  { valor: 'problemas', etiqueta: 'Con problemas', resultados: ['CON_INCIDENCIA', 'REQUIERE_ATENCION'] },
  { valor: 'pendientes', etiqueta: 'En curso o programados', resultados: ['EN_CURSO', 'PROGRAMADO'] },
]

const POR_TANDA = 8

const DIA_MES = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short' })

/** «07 nov»: sin el guion ni el punto que agrega el formato abreviado */
function diaMes(iso?: string): string {
  if (!iso) return '—'
  const partes = DIA_MES.formatToParts(new Date(iso))
  const dia = partes.find((p) => p.type === 'day')?.value ?? ''
  const mes = (partes.find((p) => p.type === 'month')?.value ?? '').replace('.', '')
  return `${dia} ${mes}`
}

function Punto({ resultado, className = 'h-3 w-3' }: { resultado?: ResultadoMantenimiento; className?: string }) {
  const r = RESULTADO[resultado ?? 'EN_CURSO']
  return (
    <span
      aria-hidden="true"
      className={`inline-block shrink-0 rounded-full border-2 ${className}`}
      style={{ borderColor: r.color, background: r.hueco ? 'var(--color-panel)' : r.color }}
    />
  )
}

function Indicador({ etiqueta, valor, detalle }: { etiqueta: string; valor: string; detalle?: string }) {
  return (
    <div className="rounded-lg bg-panel-muted px-4 py-3">
      <dt className="text-xs text-ink-soft">{etiqueta}</dt>
      <dd className="mt-1 font-mono text-xl font-semibold tabular-nums text-ink">{valor}</dd>
      {detalle && <dd className="mt-0.5 truncate text-xs text-ink-faint">{detalle}</dd>}
    </div>
  )
}

/** Salud del servidor antes y después: lo que el mantenimiento logró */
function Salud({ p }: { p: ParticipacionServidor }) {
  if (!p.nivelAntes && !p.nivelDespues) return null
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-ink-soft" title="Nivel del análisis previo y de la validación técnica">
      {p.nivelAntes ? <StatusPill tone={TONO_NIVEL[p.nivelAntes]} label={NIVEL[p.nivelAntes].etiqueta} /> : '—'}
      <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
      {p.nivelDespues ? (
        <StatusPill tone={TONO_NIVEL[p.nivelDespues]} label={NIVEL[p.nivelDespues].etiqueta} />
      ) : (
        <span className="text-ink-faint">sin validar</span>
      )}
    </span>
  )
}

function ChipTarea({ t }: { t: TareaBitacora }) {
  const icono =
    t.estado === 'FINALIZADA' ? (
      <Check className="h-3 w-3 text-success" aria-hidden="true" />
    ) : t.estado === 'FALLIDA' ? (
      <X className="h-3 w-3 text-danger" aria-hidden="true" />
    ) : t.estado === 'NO_INICIADA' ? (
      <Minus className="h-3 w-3 text-ink-faint" aria-hidden="true" />
    ) : (
      <Circle className="h-3 w-3 text-ink-faint" aria-hidden="true" />
    )
  return (
    <span className="inline-flex items-center gap-1 rounded-md border border-line bg-panel px-2 py-0.5 text-xs text-ink">
      {icono}
      {t.nombre}
      {t.duracionSegundos !== undefined && t.duracionSegundos !== null && (
        <span className="text-ink-faint">· {formatearSegundos(t.duracionSegundos)}</span>
      )}
    </span>
  )
}

/** Franja con un punto por mantenimiento en su fecha: muestra el ritmo y dónde hubo problemas */
function LineaDeTiempo({ mantenimientos }: { mantenimientos: MantenimientoBitacora[] }) {
  const periodo = periodoDe(mantenimientos)
  if (!periodo) return null
  const marcas = [0, 0.5, 1].map((f) => periodo.desde + (periodo.hasta - periodo.desde) * f)
  const ahora = Date.now()
  const posicionHoy = ahora > periodo.desde && ahora < periodo.hasta ? posicionEnPeriodo(new Date(ahora).toISOString(), periodo.desde, periodo.hasta) : undefined

  return (
    <div className="rounded-lg border border-line px-4 pb-3 pt-6">
      <div className="relative h-8">
        <span className="absolute inset-x-0 top-1/2 h-0.5 -translate-y-1/2 rounded bg-line" aria-hidden="true" />
        {posicionHoy !== undefined && (
          <span
            className="absolute -top-4 bottom-0 flex -translate-x-1/2 flex-col items-center text-[10px] text-ink-faint"
            style={{ left: `${posicionHoy}%` }}
            aria-hidden="true"
          >
            hoy
            <span className="mt-0.5 w-px flex-1 border-l border-dashed border-ink-faint" />
          </span>
        )}
        {mantenimientos.map((m) => {
          const r = RESULTADO[m.resultado ?? 'EN_CURSO']
          return (
            <Link
              key={m.idOrden}
              to={`/ordenes/${m.idOrden}`}
              title={`${m.codigo} · ${formatearFecha(fechaDe(m))} · ${r.etiqueta}`}
              aria-label={`${m.codigo}, ${formatearFecha(fechaDe(m))}, ${r.etiqueta}`}
              className="group absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full p-1 focus-visible:outline-2 focus-visible:outline-accent"
              style={{ left: `${posicionEnPeriodo(fechaDe(m), periodo.desde, periodo.hasta)}%` }}
            >
              <Punto resultado={m.resultado} className="h-3.5 w-3.5 transition-transform group-hover:scale-150" />
            </Link>
          )
        })}
      </div>
      <div className="mt-2 flex justify-between text-[11px] text-ink-faint" aria-hidden="true">
        {marcas.map((t) => (
          <span key={t}>{formatearFecha(new Date(t).toISOString())}</span>
        ))}
      </div>
    </div>
  )
}

/** En un grupo: cada integrante en una fila y cada mantenimiento en una columna, coloreados por su resultado */
function MapaDelGrupo({ mantenimientos }: { mantenimientos: MantenimientoBitacora[] }) {
  const columnas = [...mantenimientos].reverse().slice(-16)
  const servidores = new Map<number, string>()
  columnas.forEach((m) => m.servidores?.forEach((p) => p.idServidor && servidores.set(p.idServidor, p.servidor ?? '')))
  if (columnas.length === 0 || servidores.size === 0) return null

  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <table className="text-xs" aria-label="Resultado de cada integrante por mantenimiento">
        <thead>
          <tr className="border-b border-line">
            <th className="sticky left-0 bg-panel px-3 py-2 text-left font-medium text-ink-faint">Integrante</th>
            {columnas.map((m) => (
              <th key={m.idOrden} className="px-1 py-2 font-normal text-ink-faint">
                <Link to={`/ordenes/${m.idOrden}`} title={`${m.codigo} · ${formatearFecha(fechaDe(m))}`} className="block w-10 hover:text-accent">
                  {diaMes(fechaDe(m))}
                </Link>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {[...servidores.entries()].map(([id, hostname]) => (
            <tr key={id} className="border-b border-line last:border-b-0">
              <th scope="row" className="sticky left-0 bg-panel px-3 py-1.5 text-left font-mono font-normal text-ink">
                <Link to={`/servidores/${id}`} className="hover:text-accent">
                  {hostname}
                </Link>
              </th>
              {columnas.map((m) => {
                const p = m.servidores?.find((s) => s.idServidor === id)
                const r = p?.resultado ? RESULTADO[p.resultado] : undefined
                return (
                  <td key={m.idOrden} className="px-1 py-1.5 text-center">
                    {r ? (
                      <span
                        title={`${m.codigo} · ${hostname}: ${r.etiqueta}${p?.piloto ? ' (piloto)' : ''}`}
                        className={`mx-auto block h-5 w-8 rounded ${p?.piloto ? 'ring-2 ring-accent ring-offset-1 ring-offset-panel' : ''}`}
                        style={{ background: r.hueco ? 'transparent' : r.color, border: `2px solid ${r.color}` }}
                      />
                    ) : (
                      <span title="No participó" className="mx-auto block h-5 w-8 rounded border border-dashed border-line" />
                    )}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function TarjetaMantenimiento({ m, grupal }: { m: MantenimientoBitacora; grupal: boolean }) {
  const r = RESULTADO[m.resultado ?? 'EN_CURSO']
  const fecha = fechaDe(m)
  const participaciones = m.servidores ?? []
  const una = !grupal && participaciones.length === 1 ? participaciones[0] : undefined
  const duracion =
    m.inicioReal && m.finReal ? Math.round((new Date(m.finReal).getTime() - new Date(m.inicioReal).getTime()) / 1000) : undefined

  return (
    <li className="relative flex gap-4 pb-6 last:pb-0">
      {/* Fecha y nodo de la línea vertical */}
      <div className="w-16 shrink-0 pt-0.5 text-right">
        <div className="text-sm font-semibold text-ink">{diaMes(fecha)}</div>
        <div className="text-xs text-ink-faint">{fecha ? new Date(fecha).getFullYear() : ''}</div>
      </div>
      <div className="relative flex flex-col items-center">
        <Punto resultado={m.resultado} className="mt-1 h-3.5 w-3.5" />
        <span className="w-0.5 flex-1 bg-line" aria-hidden="true" />
      </div>
      <article className="min-w-0 flex-1 rounded-lg border border-line px-4 py-3">
        <header className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
          <Link to={`/ordenes/${m.idOrden}`} className="font-mono text-sm font-medium text-ink hover:text-accent hover:underline">
            {m.codigo}
          </Link>
          <StatusPill tone={r.tono} label={r.etiqueta} />
          {m.prioridad === 'ALTA' && <span className="rounded bg-danger-soft px-1.5 py-0.5 text-xs font-medium text-danger">Alta</span>}
          <span className="text-xs text-ink-soft">{m.origen ? ETIQUETA_ORIGEN[m.origen] : ''}</span>
          {m.grupal && !grupal && <span className="text-xs text-ink-soft">· con el grupo {m.grupo}</span>}
          <span className="ml-auto text-xs text-ink-faint">
            {m.inicioReal ? `Ejecutado ${formatearFechaHora(m.inicioReal)}` : `Programado ${formatearFechaHora(m.inicioProgramado)}`}
            {duracion !== undefined && ` · ${formatearSegundos(duracion)}`}
          </span>
        </header>

        {una && (
          <div className="mt-2 flex flex-col gap-2">
            <Salud p={una} />
            {(una.tareas ?? []).length > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {una.tareas?.map((t, i) => (
                  <ChipTarea key={i} t={t} />
                ))}
              </div>
            ) : (
              <p className="text-xs text-ink-faint">{r.descripcion}</p>
            )}
          </div>
        )}

        {!una && participaciones.length > 0 && (
          <ul className="mt-2 flex flex-col gap-1.5">
            {participaciones.map((p) => {
              const rp = RESULTADO[p.resultado ?? 'EN_CURSO']
              return (
                <li key={p.idServidor} className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                  <Punto resultado={p.resultado} className="h-2.5 w-2.5" />
                  <Link to={`/servidores/${p.idServidor}`} className="font-mono text-ink hover:text-accent">
                    {p.servidor}
                  </Link>
                  {p.piloto && <span className="rounded bg-accent-soft px-1.5 py-0.5 text-ink">Piloto</span>}
                  <span className="text-ink-soft">{rp.etiqueta}</span>
                  <Salud p={p} />
                  <span className="text-ink-faint">
                    {(p.tareas ?? []).filter((t) => t.estado === 'FINALIZADA').length}/{p.tareas?.length ?? 0} tareas
                  </span>
                </li>
              )
            })}
          </ul>
        )}

        {m.validacionFuncional && (
          <p className="mt-2 text-xs text-ink-soft">
            Conformidad del responsable:{' '}
            <span className="font-medium text-ink">
              {{ PENDIENTE: 'pendiente', CONFORME: 'conforme', RECHAZADA: 'no conforme', VENCIDA: 'vencida' }[m.validacionFuncional]}
            </span>
          </p>
        )}
      </article>
    </li>
  )
}

/**
 * Bitácora de mantenimiento de un servidor o de un grupo (RF53): un resumen, la línea de tiempo con el resultado de
 * cada ciclo y su historia. Se exporta en el formato que se elija; en un grupo, completa o de un integrante
 */
export function BitacoraMantenimiento({ idServidor, idGrupo, integrantes = [], version = 0 }: BitacoraMantenimientoProps) {
  const bitacora = useConsulta(
    () => (idGrupo ? bitacoraApi.grupo(idGrupo) : bitacoraApi.servidor(idServidor!)),
    [idServidor, idGrupo, version],
  )
  const [vista, setVista] = useState<Vista>('linea')
  const [anio, setAnio] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('todos')
  const [visibles, setVisibles] = useState(POR_TANDA)
  const [exportando, setExportando] = useState(false)

  const todos = useMemo(() => bitacora.datos?.mantenimientos ?? [], [bitacora.datos])
  const delAnio = useMemo(
    () => (anio ? todos.filter((m) => new Date(fechaDe(m) ?? 0).getFullYear() === Number(anio)) : todos),
    [todos, anio],
  )
  const resultadosFiltro = FILTROS.find((f) => f.valor === filtro)?.resultados
  const filtrados = resultadosFiltro ? delAnio.filter((m) => m.resultado && resultadosFiltro.includes(m.resultado)) : delAnio
  const resumen = resumenBitacora(delAnio)
  const presentes = ORDEN_RESULTADOS.filter((r) => delAnio.some((m) => m.resultado === r))
  const grupal = !!idGrupo

  const alcances: OpcionAlcance[] | undefined = grupal
    ? [
        { etiqueta: 'Todo el grupo' },
        ...integrantes.filter((s) => s.id).map((s) => ({ idServidor: s.id, etiqueta: `Solo ${s.hostname}` })),
      ]
    : undefined

  return (
    <section className="rounded-xl border border-line bg-panel p-5" aria-label="Bitácora de mantenimiento">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="min-w-0 flex-1 basis-60">
          <h3 className="text-base font-semibold text-ink">Bitácora de mantenimiento</h3>
          <p className="text-sm text-ink-soft">
            {grupal ? 'Los mantenimientos del grupo y el resultado de cada integrante.' : 'Todos los mantenimientos del servidor, individuales y de sus grupos.'}
          </p>
        </div>
        <div className="ml-auto flex flex-wrap items-center justify-end gap-2">
          <div role="tablist" aria-label="Vista de la bitácora" className="flex gap-1 rounded-lg bg-panel-muted p-1">
            {(
              [
                ['linea', 'Línea de tiempo'],
                ['ordenes', 'Órdenes'],
              ] as [Vista, string][]
            ).map(([clave, etiqueta]) => (
              <button
                key={clave}
                type="button"
                role="tab"
                aria-selected={vista === clave}
                onClick={() => setVista(clave)}
                className={`rounded-md px-3 py-1 text-sm transition-colors ${
                  vista === clave ? 'bg-panel font-medium text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
                }`}
              >
                {etiqueta}
              </button>
            ))}
          </div>
          <Boton icono={Download} onClick={() => setExportando(true)}>
            Exportar bitácora
          </Boton>
        </div>
      </div>

      {vista === 'ordenes' && <OrdenesDelObjetivo idServidor={idServidor} idGrupo={idGrupo} version={version} />}

      {vista === 'linea' && (
        <>
          <MensajeError error={bitacora.error} onReintentar={bitacora.recargar} />
          {bitacora.cargando && !bitacora.datos ? (
            <Cargando texto="Armando la bitácora…" />
          ) : todos.length === 0 ? (
            <p className="rounded-lg border border-dashed border-line px-4 py-8 text-center text-sm text-ink-soft">
              Aún no hay mantenimientos registrados.
            </p>
          ) : (
            <div className="flex flex-col gap-5">
              <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                <Indicador
                  etiqueta="Mantenimientos terminados"
                  valor={String(resumen.realizados)}
                  detalle={`${resumen.exitosos} con tareas ejecutadas · ${resumen.sinIntervencion} sin intervención`}
                />
                <Indicador
                  etiqueta="Terminaron bien"
                  valor={resumen.tasaExito === undefined ? '—' : `${resumen.tasaExito} %`}
                  detalle={resumen.conProblemas ? `${resumen.conProblemas} con problemas` : 'Sin problemas registrados'}
                />
                <Indicador
                  etiqueta="Último mantenimiento"
                  valor={resumen.ultimo ? formatearFecha(resumen.ultimo.inicioReal) : '—'}
                  detalle={resumen.ultimo?.codigo}
                />
                <Indicador
                  etiqueta="Próximo"
                  valor={resumen.proximo ? formatearFecha(resumen.proximo.inicioProgramado) : '—'}
                  detalle={resumen.proximo?.codigo ?? 'Sin mantenimientos programados'}
                />
              </dl>

              <div className="flex flex-wrap items-center gap-2">
                <Selector aria-label="Año" className="!w-auto" value={anio} onChange={(e) => { setAnio(e.target.value); setVisibles(POR_TANDA) }}>
                  <option value="">Todos los años</option>
                  {aniosDe(todos).map((a) => (
                    <option key={a} value={a}>
                      {a}
                    </option>
                  ))}
                </Selector>
                <div role="radiogroup" aria-label="Resultado" className="flex flex-wrap gap-1 rounded-lg bg-panel-muted p-1">
                  {FILTROS.map((f) => (
                    <button
                      key={f.valor}
                      type="button"
                      role="radio"
                      aria-checked={filtro === f.valor}
                      onClick={() => {
                        setFiltro(f.valor)
                        setVisibles(POR_TANDA)
                      }}
                      className={`rounded-md px-3 py-1 text-sm transition-colors ${
                        filtro === f.valor ? 'bg-panel font-medium text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
                      }`}
                    >
                      {f.etiqueta}
                    </button>
                  ))}
                </div>
              </div>

              <LineaDeTiempo mantenimientos={filtrados} />
              <ul className="-mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-soft" aria-label="Leyenda">
                {presentes.map((r) => (
                  <li key={r} className="flex items-center gap-1.5" title={RESULTADO[r].descripcion}>
                    <Punto resultado={r} className="h-2.5 w-2.5" />
                    {RESULTADO[r].etiqueta}
                  </li>
                ))}
              </ul>

              {grupal && (
                <div className="flex flex-col gap-2">
                  <h4 className="text-sm font-semibold text-ink">Resultado por integrante</h4>
                  <MapaDelGrupo mantenimientos={filtrados} />
                  <p className="text-xs text-ink-faint">Cada columna es un mantenimiento; el recuadro marcado es el servidor piloto.</p>
                </div>
              )}

              {filtrados.length === 0 ? (
                <p className="text-sm text-ink-soft">Ningún mantenimiento coincide con el filtro.</p>
              ) : (
                <ol aria-label="Historia de mantenimientos">
                  {filtrados.slice(0, visibles).map((m) => (
                    <TarjetaMantenimiento key={m.idOrden} m={m} grupal={grupal} />
                  ))}
                </ol>
              )}
              {filtrados.length > visibles && (
                <Boton className="self-center" onClick={() => setVisibles((v) => v + POR_TANDA)}>
                  Ver {Math.min(POR_TANDA, filtrados.length - visibles)} más de {filtrados.length - visibles}
                </Boton>
              )}
              {bitacora.datos?.truncada && (
                <p className="text-xs text-ink-faint">
                  Se muestran los {todos.length} mantenimientos más recientes. La pestaña Órdenes tiene el historial completo.
                </p>
              )}
            </div>
          )}
        </>
      )}

      {exportando && (
        <ExportarBitacoraModal
          titulo="Exportar bitácora de mantenimiento"
          descripcion={
            grupal
              ? `Mantenimientos del grupo ${bitacora.datos?.objetivo ?? ''}`
              : `Todos los mantenimientos de ${bitacora.datos?.objetivo ?? 'el servidor'}`
          }
          alcances={alcances}
          onExportar={(formato, idIntegrante) =>
            grupal ? bitacoraApi.exportarGrupo(idGrupo!, formato, idIntegrante) : bitacoraApi.exportarServidor(idServidor!, formato)
          }
          onCerrar={() => setExportando(false)}
        />
      )}
    </section>
  )
}
