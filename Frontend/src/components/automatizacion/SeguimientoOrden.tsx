import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Check, Download, FlaskConical, Play, ScanSearch, X } from 'lucide-react'
import { bitacoraApi, cicloApi, operacionApi } from '@/api/automatizacion'
import type { EstadoOrden, EtapaOrden } from '@/api/dominio'
import type { FichaAutomatizacionRespuesta, IncidenciaRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { Info, TituloConInfo } from '@/components/ui/Info'
import { useConsulta } from '@/hooks/useConsulta'
import { AFECTACION, ESTADO_INCIDENCIA, ORIGEN_INCIDENCIA } from '@/utils/automatizacion'
import { etapasAlcanzadas, pasoDe } from '@/utils/etapasOrden'
import { ETIQUETA_ESTADO_ORDEN } from '@/utils/etiquetas'
import { formatearFechaHora, formatearHora } from '@/utils/formato'
import { AccionOrdenModal } from './ciclo/AccionOrdenModal'
import type { AccionOrden } from './ciclo/AccionOrdenModal'
import { Autorizaciones } from './ciclo/Autorizaciones'
import { CELDA, ENCABEZADO } from './ciclo/estilosTabla'
import { EtapasOrden } from './ciclo/EtapasOrden'
import { EjecucionMop, PlanTrabajo } from './ciclo/PlanesMop'
import { ValidacionFuncional, ValidacionTecnica } from './ciclo/ResultadoValidacion'
import { ExportarBitacoraModal } from './ExportarBitacoraModal'
import { TablaAnalisis } from './TablaAnalisis'

interface SeguimientoOrdenProps {
  idOrden: number
  /** Se llama cuando la orden cambia de estado, para refrescar los datos de planificación de la página */
  onCambioEstado: () => void
}

/** Mientras la orden avanza sola, la ficha se consulta seguido; en los demás estados espera una acción */
const EN_CURSO: EstadoOrden[] = ['EN_EVALUACION', 'AUTORIZADA', 'EN_COLA', 'EN_EJECUCION']
const REFRESCO_MS = 4000

/** Cada etapa responde una pregunta, en lenguaje del usuario y no del modelo */
const ETAPA: Record<Exclude<EtapaOrden, 'INCIDENCIA'>, { titulo: string; explicacion: string }> = {
  PLANIFICACION: {
    titulo: 'Cuándo se hará',
    explicacion:
      'La orden espera su fecha dentro de la ventana permisiva. Poco antes, KomainOS analiza el servidor para decidir qué hacer.',
  },
  EVALUACION: {
    titulo: 'Qué se encontró en el servidor',
    explicacion:
      'El script de análisis mide el servidor y cada métrica se compara con sus umbrales. Las que están en advertencia o crítico activan las tareas del plan.',
  },
  AUTORIZACION: {
    titulo: 'Qué se va a hacer y quién lo aprueba',
    explicacion:
      'El plan de trabajo (MOP) lista las tareas de cada servidor en el orden en que se ejecutarán. Lo aprueban el responsable, el administrador o ambos, según la política de cambio del entorno.',
  },
  EJECUCION: {
    titulo: 'Qué se hizo',
    explicacion:
      'Cada tarea se ejecuta en el servidor con la cuenta de servicio. Si una falla, las siguientes no se inician y se abre una incidencia.',
  },
  VALIDACION: {
    titulo: '¿Quedó bien?',
    explicacion:
      'KomainOS vuelve a analizar el servidor y compara las métricas antes y después (validación técnica). Luego el responsable confirma que el servicio funciona.',
  },
  CIERRE: {
    titulo: 'Cómo terminó',
    explicacion: 'El ciclo terminó. La bitácora completa se puede exportar con el botón de arriba.',
  },
}

/** Con una incidencia abierta, la etapa de validación cuenta qué falló */
const INCIDENCIA = {
  titulo: 'Qué falló y cómo se atiende',
  explicacion:
    'Una tarea falló, se excedió el tiempo o la validación no fue conforme. El administrador registra el diagnóstico y las acciones; al cerrar la incidencia, la orden pasa a su cierre sin reanudar tareas.',
}

/** Sección de una etapa: su explicación queda detrás del ⓘ para no cargar la pantalla */
function Seccion({ titulo, info, children }: { titulo: string; info?: ReactNode; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      {info ? <TituloConInfo titulo={titulo}>{info}</TituloConInfo> : <h5 className="text-sm font-semibold text-ink">{titulo}</h5>}
      {children}
    </section>
  )
}

/** Datos en el formato de la planificación: etiqueta pequeña y valor debajo */
function Dato({ etiqueta, children, ancho }: { etiqueta: string; children: ReactNode; ancho?: boolean }) {
  return (
    <div className={ancho ? 'sm:col-span-full' : ''}>
      <dt className="text-xs text-ink-faint">{etiqueta}</dt>
      <dd className="mt-0.5 text-sm text-ink">{children}</dd>
    </div>
  )
}

/** Cómo terminó el ciclo según el estado previo al cierre */
const RESULTADO_CIERRE: Partial<Record<EstadoOrden, string>> = {
  PENDIENTE_VALIDACION: 'Mantenimiento completado y validado',
  EN_EVALUACION: 'Cerrado sin intervenir: ninguna métrica activó tareas',
  INCIDENCIA: 'Cerrado tras atender la incidencia',
  SIN_IMPLEMENTACION: 'Cerrado sin intervenir: faltaba una implementación compatible',
  RECHAZADA: 'Rechazado en la autorización',
  AUTORIZACION_VENCIDA: 'No se ejecutó: venció el plazo de autorización',
  VALIDACION_VENCIDA: 'Cerrado: venció el plazo de la validación funcional',
  CANCELADA: 'Cancelado antes de ejecutarse',
}

function Incidencias({ incidencias, gestiona, esAdmin }: { incidencias: IncidenciaRespuesta[]; gestiona: boolean; esAdmin: boolean }) {
  return (
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
          {/* El responsable ve sus incidencias aquí; la gestión de incidencias es del administrador y el operador */}
          {gestiona && (
            <Link to={`/automatizacion/incidencias?incidencia=${i.id}`} className="text-sm font-medium text-accent hover:underline">
              {i.estado === 'CERRADA' || !esAdmin ? 'Ver' : 'Atender'}
            </Link>
          )}
        </li>
      ))}
    </ul>
  )
}

/** Lo que muestra cada etapa del ciclo */
function DetalleEtapa({
  etapa,
  f,
  gestiona,
  esAdmin,
}: {
  etapa: Exclude<EtapaOrden, 'INCIDENCIA'>
  f: FichaAutomatizacionRespuesta
  gestiona: boolean
  esAdmin: boolean
}) {
  const o = f.orden!
  const analisis = f.evaluaciones ?? []
  const incidencias = f.incidencias ?? []

  switch (etapa) {
    case 'PLANIFICACION':
      return (
        <dl className="grid gap-4 sm:grid-cols-3">
          <Dato etiqueta="Inicio programado">
            <span className="font-medium">{formatearFechaHora(o.inicioProgramado)}</span>
          </Dato>
          <Dato etiqueta="Fin previsto">
            <span className="font-medium">{formatearFechaHora(o.finProgramado)}</span>
          </Dato>
          <Dato etiqueta="Análisis previo">{formatearFechaHora(o.fechaEvaluacion)}</Dato>
        </dl>
      )
    case 'EVALUACION': {
      // Cada servidor analizado es una fila: al pulsarla se abre el detalle de sus métricas
      const previos = analisis.filter((a) => a.tipo === 'PREVIA')
      return previos.length === 0 ? (
        <p className="text-sm text-ink-soft">El análisis previo todavía no se ejecuta.</p>
      ) : (
        <TablaAnalisis analisis={previos} />
      )
    }
    case 'AUTORIZACION': {
      const vigente = f.autorizaciones?.at(-1)
      return (
        <div className="flex flex-col gap-5">
          {/* Lo que el aprobador necesita saber antes de decidir */}
          <dl className="grid gap-4 rounded-lg bg-panel-muted px-4 py-3 sm:grid-cols-4">
            <Dato etiqueta="Ejecución programada">
              <span className="font-medium">
                {formatearFechaHora(o.inicioProgramado)} – {formatearHora(o.finProgramado)}
              </span>
            </Dato>
            <Dato etiqueta="Política de cambio">{vigente?.entorno ?? '—'}</Dato>
            <Dato etiqueta="Afectación del plan">{vigente?.afectacion ? AFECTACION[vigente.afectacion].etiqueta : '—'}</Dato>
            <Dato etiqueta="Plazo para decidir">
              {!vigente ? '—' : vigente.automatica ? 'No requiere aprobaciones' : formatearFechaHora(vigente.fechaLimite)}
            </Dato>
          </dl>
          <Seccion
            titulo="Plan de trabajo (MOP)"
            info="Cuántos servidores y tareas abarca el plan y lo que dura completo: en un grupo secuencial se suman las MOP; en uno paralelo va primero el piloto y luego los demás a la vez. Pulse la fila para ver las tareas de cada servidor."
          >
            <PlanTrabajo mops={f.mops ?? []} resumen={f.resumenPlan} />
          </Seccion>
          <Seccion
            titulo="Aprobaciones"
            info="Se piden en secuencia, primero el responsable y después el administrador, con un único plazo. El responsable puede proponer otra fecha; si el administrador la acepta, se piden de nuevo las aprobaciones."
          >
            <Autorizaciones rondas={f.autorizaciones ?? []} />
          </Seccion>
        </div>
      )
    }
    case 'EJECUCION':
      return (
        <div className="flex flex-col gap-5">
          <EjecucionMop mops={f.mops ?? []} />
          {incidencias.length > 0 && (
            <Seccion titulo={`Incidencias (${incidencias.length})`}>
              <Incidencias incidencias={incidencias} gestiona={gestiona} esAdmin={esAdmin} />
            </Seccion>
          )}
        </div>
      )
    case 'VALIDACION':
      return (
        <div className="flex flex-col gap-5">
          {incidencias.length > 0 && (
            <Seccion titulo={`Incidencias (${incidencias.length})`}>
              <Incidencias incidencias={incidencias} gestiona={gestiona} esAdmin={esAdmin} />
            </Seccion>
          )}
          <Seccion
            titulo="Validación técnica"
            info="Al terminar las tareas, KomainOS vuelve a analizar cada servidor y compara cada métrica con su valor antes de intervenir. Pulse un servidor para ver la comparación de cada métrica."
          >
            <ValidacionTecnica reporte={f.reporte ?? []} tecnicas={f.validacionesTecnicas ?? []} />
          </Seccion>
          <Seccion
            titulo="Conformidad del responsable"
            info="Si la validación técnica es conforme, se pide al responsable que confirme que el servicio funciona. Con su conformidad el ciclo se cierra."
          >
            <ValidacionFuncional funcional={f.validacionFuncional} />
          </Seccion>
        </div>
      )
    case 'CIERRE': {
      const historial = f.historial ?? []
      const ultimo = historial.at(-1)
      const previo = ultimo?.estadoAnterior
      const reporte = f.reporte ?? []
      return (
        <div className="flex flex-col gap-5">
          <dl className="grid gap-4 sm:grid-cols-3">
            <Dato etiqueta="Resultado">
              <span className="font-medium">{(previo && RESULTADO_CIERRE[previo]) ?? (o.estado ? ETIQUETA_ESTADO_ORDEN[o.estado] : '—')}</span>
            </Dato>
            <Dato etiqueta="Cerrada">{formatearFechaHora(ultimo?.fechaHora)}</Dato>
            <Dato etiqueta="Por">{ultimo?.usuario?.nombre ?? 'Sistema'}</Dato>
            {ultimo?.motivo && (
              <Dato etiqueta="Motivo" ancho>
                {ultimo.motivo}
              </Dato>
            )}
          </dl>
          {reporte.length === 1 && (
            <dl className="grid gap-4 border-t border-line pt-4 sm:grid-cols-3">
              <Dato etiqueta="Tareas finalizadas">
                <span className="font-mono tabular-nums">{reporte[0].tareasFinalizadas ?? 0}</span>
              </Dato>
              <Dato etiqueta="Tareas fallidas">
                <span className={`font-mono tabular-nums ${reporte[0].tareasFallidas ? 'text-danger' : ''}`}>
                  {reporte[0].tareasFallidas ?? 0}
                </span>
              </Dato>
              <Dato etiqueta="Tareas no iniciadas">
                <span className="font-mono tabular-nums">{reporte[0].tareasNoIniciadas ?? 0}</span>
              </Dato>
            </dl>
          )}
          {reporte.length > 1 && (
            <div className="overflow-x-auto rounded-lg border border-line">
              <table className="w-full min-w-[480px] text-sm">
                <thead>
                  <tr className={ENCABEZADO}>
                    <th className="px-3 py-2 font-medium">Servidor</th>
                    <th className="px-3 py-2 font-medium">Tareas finalizadas</th>
                    <th className="px-3 py-2 font-medium">Fallidas</th>
                    <th className="px-3 py-2 font-medium">No iniciadas</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {reporte.map((r) => (
                    <tr key={r.idServidor}>
                      <td className={`${CELDA} font-mono text-ink`}>{r.servidor}</td>
                      <td className={`${CELDA} tabular-nums`}>{r.tareasFinalizadas ?? 0}</td>
                      <td className={`${CELDA} tabular-nums ${r.tareasFallidas ? 'text-danger' : ''}`}>{r.tareasFallidas ?? 0}</td>
                      <td className={`${CELDA} tabular-nums`}>{r.tareasNoIniciadas ?? 0}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )
    }
  }
}

/**
 * Seguimiento del ciclo de una orden (HU25-HU28): en qué etapa está, qué espera para avanzar y lo que el usuario
 * puede decidir ahora. Cada etapa del recorrido se abre para ver lo que ocurrió en ella; la bitácora completa (RF53)
 * se exporta en el formato que se elija
 */
export function SeguimientoOrden({ idOrden, onCambioEstado }: SeguimientoOrdenProps) {
  const { avisar } = useAvisos()
  const { tieneRol } = useSesion()
  const gestionaIncidencias = tieneRol('ADMINISTRADOR') || tieneRol('OPERADOR')
  const esAdmin = tieneRol('ADMINISTRADOR')
  const ficha = useConsulta(() => cicloApi.ficha(idOrden), [idOrden])
  const [elegida, setElegida] = useState<EtapaOrden>()
  const [accion, setAccion] = useState<AccionOrden>()
  const [exportando, setExportando] = useState(false)
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
    if (estadoAnterior.current && estado && estadoAnterior.current !== estado) {
      onCambioEstado()
      // Al avanzar, se muestra la etapa nueva
      setElegida(undefined)
    }
    estadoAnterior.current = estado
  }, [estado, onCambioEstado])

  if (ficha.cargando && !f) return <Cargando texto="Cargando el ciclo de mantenimiento…" />
  if (!f || !f.orden) return <MensajeError error={ficha.error} onReintentar={ficha.recargar} />

  const a = f.acciones ?? {}
  const etapaOrden = f.orden.etapa ?? 'PLANIFICACION'
  const recorrido = (f.historial ?? []).flatMap((h) => (h.estadoNuevo ? [h.estadoNuevo] : []))
  const alcanzadas = etapasAlcanzadas(etapaOrden, recorrido)
  const actual = pasoDe(elegida && alcanzadas.has(elegida) ? elegida : etapaOrden) as Exclude<EtapaOrden, 'INCIDENCIA'>
  const presentacion = etapaOrden === 'INCIDENCIA' && actual === 'VALIDACION' ? INCIDENCIA : ETAPA[actual]
  const propuesta = f.autorizaciones
    ?.at(-1)
    ?.decisiones?.find((d) => d.estado === 'FECHA_PROPUESTA')?.fechaPropuesta

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

  const hayAcciones =
    a.evaluar || a.decidir || a.proponerFecha || a.decidirPropuesta || a.validar || a.cerrarSinImplementacion || a.procesarCola

  return (
    <section className="flex flex-col gap-5 rounded-xl border border-line bg-panel p-5" aria-label="Ciclo de mantenimiento">
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1 basis-60">
          <h3 className="text-base font-semibold text-ink">Ciclo de mantenimiento</h3>
          {f.simulado && (
            <p className="mt-1 inline-flex items-center gap-1.5 text-xs text-warning">
              <FlaskConical className="h-3.5 w-3.5" aria-hidden="true" />
              Modo simulado: los resultados no provienen de los servidores
            </p>
          )}
        </div>
        <div className="ml-auto flex flex-wrap justify-end gap-2">
          <Boton icono={Download} onClick={() => setExportando(true)}>
            Exportar bitácora
          </Boton>
        </div>
      </div>

      <EtapasOrden etapa={etapaOrden} estado={f.orden.estado ?? 'PROGRAMADA'} recorrido={recorrido} seleccionada={actual} onElegir={setElegida} />

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

      <div className="flex flex-col gap-4 border-t border-line pt-4">
        <div className="flex items-center gap-1.5">
          <h4 className="text-sm font-semibold text-ink">{presentacion.titulo}</h4>
          <Info etiqueta={`Qué ocurre en esta etapa: ${presentacion.titulo}`}>{presentacion.explicacion}</Info>
        </div>
        <DetalleEtapa etapa={actual} f={f} gestiona={gestionaIncidencias} esAdmin={esAdmin} />
      </div>

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
      {exportando && (
        <ExportarBitacoraModal
          titulo="Exportar bitácora de la orden"
          descripcion={`Todo lo ocurrido en ${f.orden.codigo}, en orden cronológico.`}
          onExportar={(formato) => bitacoraApi.exportarOrden(idOrden, formato)}
          onCerrar={() => setExportando(false)}
        />
      )}
    </section>
  )
}
