import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Check, Download, FileJson, FlaskConical, Play, ScanSearch, X } from 'lucide-react'
import { cicloApi, operacionApi } from '@/api/automatizacion'
import type { EstadoOrden, EtapaOrden } from '@/api/dominio'
import { useSesion } from '@/auth/sesion-context'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { descargarTexto, ESTADO_INCIDENCIA, ORIGEN_INCIDENCIA } from '@/utils/automatizacion'
import { bitacoraCsv } from '@/utils/bitacora'
import { formatearFechaHora } from '@/utils/formato'
import { AccionOrdenModal } from './ciclo/AccionOrdenModal'
import type { AccionOrden } from './ciclo/AccionOrdenModal'
import { Autorizaciones } from './ciclo/Autorizaciones'
import { EtapasOrden } from './ciclo/EtapasOrden'
import { PlanesMop } from './ciclo/PlanesMop'
import { ReporteComparativo } from './ciclo/ReporteComparativo'
import { Validaciones } from './ciclo/Validaciones'

interface SeguimientoOrdenProps {
  idOrden: number
  /** Se llama cuando la orden cambia de estado, para refrescar los datos de planificación de la página */
  onCambioEstado: () => void
}

type Pestana = 'mop' | 'analisis' | 'autorizacion' | 'validacion' | 'incidencias'

/** Mientras la orden avanza sola, la ficha se consulta seguido; en los demás estados espera una acción */
const EN_CURSO: EstadoOrden[] = ['EN_EVALUACION', 'AUTORIZADA', 'EN_COLA', 'EN_EJECUCION']
const REFRESCO_MS = 4000

const PESTANA_DE_ETAPA: Record<EtapaOrden, Pestana> = {
  PLANIFICACION: 'analisis',
  EVALUACION: 'analisis',
  AUTORIZACION: 'mop',
  EJECUCION: 'mop',
  VALIDACION: 'validacion',
  INCIDENCIA: 'incidencias',
  CIERRE: 'analisis',
}

/**
 * Seguimiento del ciclo de una orden (HU25-HU28): en qué etapa está, qué espera para avanzar, lo que el usuario
 * puede decidir ahora y la bitácora completa (RF53) con el reporte comparativo (RF54)
 */
export function SeguimientoOrden({ idOrden, onCambioEstado }: SeguimientoOrdenProps) {
  const { avisar } = useAvisos()
  const { tieneRol } = useSesion()
  // El responsable ve sus incidencias aquí; la gestión de incidencias es del administrador y el operador
  const gestionaIncidencias = tieneRol('ADMINISTRADOR') || tieneRol('OPERADOR')
  const ficha = useConsulta(() => cicloApi.ficha(idOrden), [idOrden])
  const [pestana, setPestana] = useState<Pestana>()
  const [accion, setAccion] = useState<AccionOrden>()
  const [trabajando, setTrabajando] = useState(false)
  const [error, setError] = useState<unknown>()

  const f = ficha.datos
  const estado = f?.orden?.estado
  const { recargar } = ficha

  useEffect(() => {
    if (!estado || !EN_CURSO.includes(estado)) return
    const t = window.setInterval(() => {
      if (document.visibilityState === 'visible') recargar()
    }, REFRESCO_MS)
    return () => window.clearInterval(t)
  }, [estado, recargar])

  // La página de la orden muestra datos de planificación que también cambian con el estado
  const estadoAnterior = useRef(estado)
  useEffect(() => {
    if (estadoAnterior.current && estado && estadoAnterior.current !== estado) onCambioEstado()
    estadoAnterior.current = estado
  }, [estado, onCambioEstado])

  if (ficha.cargando && !f) return <Cargando texto="Cargando el ciclo de mantenimiento…" />
  if (!f || !f.orden) return <MensajeError error={ficha.error} onReintentar={ficha.recargar} />

  const a = f.acciones ?? {}
  const actual: Pestana = pestana ?? PESTANA_DE_ETAPA[f.orden.etapa ?? 'PLANIFICACION']
  const propuesta = f.autorizaciones
    ?.at(-1)
    ?.decisiones?.find((d) => d.estado === 'FECHA_PROPUESTA')?.fechaPropuesta
  const incidencias = f.incidencias ?? []
  const abiertas = incidencias.filter((i) => i.estado !== 'CERRADA').length

  async function ejecutar(fn: () => Promise<string>) {
    setTrabajando(true)
    setError(undefined)
    try {
      avisar(await fn())
      recargar()
      onCambioEstado()
    } catch (e) {
      setError(e)
    } finally {
      setTrabajando(false)
    }
  }

  const evaluar = () =>
    ejecutar(async () => {
      const r = await cicloApi.evaluar(idOrden)
      return r.mensaje
    })

  const despachar = () =>
    ejecutar(async () => {
      const r = await operacionApi.ejecutarCola()
      return r.mensaje ?? `${r.despachados} servidores despachados.`
    })

  const exportar = (formato: 'json' | 'csv') => {
    const nombre = `bitacora_${f.orden?.codigo ?? idOrden}`
    if (formato === 'json') descargarTexto(`${nombre}.json`, JSON.stringify(f, null, 2), 'application/json')
    else descargarTexto(`${nombre}.csv`, bitacoraCsv(f), 'text/csv')
  }

  const pestanas: [Pestana, string][] = [
    ['mop', `MOP y ejecución${f.mops?.length ? ` (${f.mops.length})` : ''}`],
    ['analisis', 'Análisis y reporte comparativo'],
    ['autorizacion', `Autorizaciones${f.autorizaciones?.length ? ` (${f.autorizaciones.length})` : ''}`],
    ['validacion', 'Validaciones'],
    ['incidencias', `Incidencias${incidencias.length ? ` (${abiertas} abiertas de ${incidencias.length})` : ''}`],
  ]

  const hayAcciones =
    a.evaluar || a.decidir || a.proponerFecha || a.decidirPropuesta || a.validar || a.cerrarSinImplementacion || a.procesarCola

  return (
    <section className="flex flex-col gap-5 rounded-xl border border-line bg-panel p-5" aria-label="Ciclo de mantenimiento">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold text-ink">Ciclo de mantenimiento</h3>
          {f.simulado && (
            <p className="mt-1 inline-flex items-center gap-1.5 text-xs text-warning">
              <FlaskConical className="h-3.5 w-3.5" aria-hidden="true" />
              Modo simulado: los resultados no provienen de los servidores
            </p>
          )}
        </div>
        <div className="flex gap-2">
          <Boton variante="fantasma" icono={Download} onClick={() => exportar('csv')}>
            Bitácora CSV
          </Boton>
          <Boton variante="fantasma" icono={FileJson} onClick={() => exportar('json')}>
            JSON
          </Boton>
        </div>
      </div>

      <EtapasOrden
        etapa={f.orden.etapa ?? 'PLANIFICACION'}
        estado={f.orden.estado ?? 'PROGRAMADA'}
        recorrido={(f.historial ?? []).flatMap((h) => (h.estadoNuevo ? [h.estadoNuevo] : []))}
      />

      <div className="flex flex-col gap-3 rounded-lg bg-accent-soft px-4 py-3 sm:flex-row sm:items-center">
        <p className="flex-1 text-sm text-ink">
          <span className="font-medium">Siguiente paso: </span>
          {f.siguientePaso}
        </p>
        {hayAcciones && (
          <div className="flex flex-wrap gap-2">
            {a.evaluar && (
              <Boton variante="primario" icono={ScanSearch} cargando={trabajando} onClick={evaluar}>
                Evaluar ahora
              </Boton>
            )}
            {a.decidir && (
              <>
                <Boton variante="primario" icono={Check} onClick={() => setAccion('aprobar')}>
                  Aprobar
                </Boton>
                <Boton icono={X} onClick={() => setAccion('rechazar')}>
                  Rechazar
                </Boton>
              </>
            )}
            {a.proponerFecha && <Boton onClick={() => setAccion('proponer')}>Proponer otra fecha</Boton>}
            {a.decidirPropuesta && (
              <>
                <Boton variante="primario" onClick={() => setAccion('aceptar-fecha')}>
                  Aceptar fecha propuesta
                </Boton>
                <Boton onClick={() => setAccion('rechazar-propuesta')}>No aceptar</Boton>
              </>
            )}
            {a.validar && (
              <>
                <Boton variante="primario" icono={Check} onClick={() => setAccion('conforme')}>
                  Validar conforme
                </Boton>
                <Boton icono={X} onClick={() => setAccion('no-conforme')}>
                  No conforme
                </Boton>
              </>
            )}
            {a.cerrarSinImplementacion && (
              <Boton variante="peligro" onClick={() => setAccion('cerrar-sin-implementacion')}>
                Cerrar sin intervenir
              </Boton>
            )}
            {a.procesarCola && (
              <Boton icono={Play} cargando={trabajando} onClick={despachar}>
                Despachar cola
              </Boton>
            )}
          </div>
        )}
      </div>

      <MensajeError error={error} />

      <div role="tablist" aria-label="Detalle del ciclo" className="flex flex-wrap gap-2">
        {pestanas.map(([clave, etiqueta]) => (
          <button
            key={clave}
            role="tab"
            type="button"
            aria-selected={actual === clave}
            onClick={() => setPestana(clave)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              actual === clave ? 'bg-accent text-accent-ink' : 'bg-panel-muted text-ink-soft hover:text-ink'
            }`}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      {actual === 'mop' && <PlanesMop mops={f.mops ?? []} />}
      {actual === 'analisis' && <ReporteComparativo reporte={f.reporte ?? []} evaluaciones={f.evaluaciones ?? []} />}
      {actual === 'autorizacion' && <Autorizaciones rondas={f.autorizaciones ?? []} />}
      {actual === 'validacion' && (
        <Validaciones tecnicas={f.validacionesTecnicas ?? []} funcional={f.validacionFuncional} />
      )}
      {actual === 'incidencias' &&
        (incidencias.length === 0 ? (
          <p className="text-sm text-ink-soft">Sin incidencias.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {incidencias.map((i) => (
              <li key={i.id} className="flex flex-wrap items-start gap-3 rounded-lg border border-line px-4 py-3 text-sm">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-ink">{i.origen ? ORIGEN_INCIDENCIA[i.origen] : 'Incidencia'}</span>
                    <span className="font-mono text-ink-soft">{i.servidor}</span>
                    {i.estado && <StatusPill tone={ESTADO_INCIDENCIA[i.estado].tono} label={ESTADO_INCIDENCIA[i.estado].etiqueta} />}
                  </div>
                  {i.error && <p className="mt-1 line-clamp-2 text-ink-soft">{i.error}</p>}
                  <p className="mt-1 text-xs text-ink-faint">Abierta {formatearFechaHora(i.fechaApertura)}</p>
                </div>
                {gestionaIncidencias && (
                  <Link
                    to={`/automatizacion/incidencias?incidencia=${i.id}`}
                    className="text-sm font-medium text-accent hover:underline"
                  >
                    {i.estado === 'CERRADA' || !tieneRol('ADMINISTRADOR') ? 'Ver' : 'Atender'}
                  </Link>
                )}
              </li>
            ))}
          </ul>
        ))}

      {accion && (
        <AccionOrdenModal
          idOrden={idOrden}
          accion={accion}
          fechaPropuesta={propuesta}
          onCerrar={() => setAccion(undefined)}
          onHecho={(mensaje) => {
            setAccion(undefined)
            avisar(mensaje)
            recargar()
            onCambioEstado()
          }}
        />
      )}
    </section>
  )
}
