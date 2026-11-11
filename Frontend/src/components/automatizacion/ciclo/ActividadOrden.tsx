import { useLayoutEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ArrowLeft } from 'lucide-react'
import { cicloApi } from '@/api/automatizacion'
import type { AnalisisRespuesta, FichaAutomatizacionRespuesta, TareaMopRespuesta } from '@/api/types'
import { Dato } from '@/components/common/Tarjeta'
import { Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { FilaDetalle } from '@/components/ui/FilaDetalle'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'
import { useRefrescoConsulta } from '@/hooks/useRefrescoConsulta'
import type { EventoActividad, FaseActividad, NivelActividad } from '@/utils/actividadEjecucion'
import { deRespuestas, nombreDeTarea } from '@/utils/actividadEjecucion'
import { diagnosticarFallo, formatearSegundos } from '@/utils/automatizacion'
import { DiagnosticoActividad } from '../RegistroActividad'
import { SalidaTerminal } from '../SalidaTerminal'

const FASES: { fase: FaseActividad; titulo: string }[] = [
  { fase: 'ORDEN', titulo: 'Cambios de la orden' },
  { fase: 'EVALUACION', titulo: 'Evaluación previa' },
  { fase: 'EJECUCION', titulo: 'Ejecución' },
  { fase: 'VALIDACION', titulo: 'Validación' },
]
const VISTAS = { actividad: 'Actividad', salidas: 'Salida de scripts' } as const
const FECHA_HORA = new Intl.DateTimeFormat('es-PE', {
  day: '2-digit', month: '2-digit', year: 'numeric',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
})

interface RegistroTarea {
  clave: string
  tarea: TareaMopRespuesta
  servidor?: string
}

type AbrirRegistro = (clave: string, origen: HTMLButtonElement) => void

function fechaVisible(fecha?: string) {
  const instante = Date.parse(fecha ?? '')
  return Number.isFinite(instante) ? FECHA_HORA.format(instante) : undefined
}

type AccionRegistro = Pick<EventoActividad, 'accion' | 'nivel' | 'servidor' | 'script' | 'idTarea'>

function datosDeSalida(tarea: TareaMopRespuesta, servidor?: string): AccionRegistro {
  const nombre = nombreDeTarea(tarea)
  return {
    accion: tarea.estado === 'FALLIDA' ? `${nombre} ha fallado`
      : tarea.estado === 'FINALIZADA' ? `${nombre} completada`
        : tarea.estado == null ? `${nombre} sin resultado confirmado` : `${nombre} con salida registrada`,
    nivel: tarea.estado === 'FALLIDA' ? 'ERROR' : tarea.estado == null ? 'WARNING' : 'INFO',
    servidor,
    idTarea: tarea.id,
  }
}

function mensajeDelRegistro({ accion, nivel, servidor, script, idTarea }: AccionRegistro) {
  const ubicacion = servidor ? ` en ${servidor}` : ''
  const herramienta = script ? ` con el script «${script}»` : ''
  const revision = nivel === 'ERROR' && idTarea != null ? ' y requiere revisión' : ''
  return `${accion}${ubicacion}${herramienta}${revision}`
}

const COLOR_NIVEL: Record<NivelActividad, string> = { INFO: 'bg-success', WARNING: 'bg-warning', ERROR: 'bg-danger' }

function EtiquetaNivel({ nivel }: { nivel: NivelActividad }) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap font-mono font-medium text-ink">
      <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${COLOR_NIVEL[nivel]}`} aria-hidden="true" />
      {nivel}
    </span>
  )
}

function FilaRegistro({ fecha, registro, onAbrir }: {
  fecha?: string
  registro: AccionRegistro
  onAbrir: (origen: HTMLButtonElement) => void
}) {
  const marca = fechaVisible(fecha)
  const mensaje = mensajeDelRegistro(registro)
  const boton = useRef<HTMLButtonElement>(null)
  function abrir() {
    if (boton.current) onAbrir(boton.current)
  }

  return (
    <FilaDetalle onAbrir={abrir} className="align-middle">
      <td className="px-2 py-1 text-[10px] whitespace-nowrap tabular-nums text-ink-soft">
        {marca ? <time dateTime={fecha}>{marca}</time> : <span aria-label="Fecha y hora no registradas">—</span>}
      </td>
      <td className="px-2 py-1 text-[10px]"><EtiquetaNivel nivel={registro.nivel} /></td>
      <td className="p-0">
        <button
          ref={boton}
          type="button"
          onClick={abrir}
          title={mensaje}
          className="block min-h-8 w-full truncate px-2 py-1 text-left text-[11px] leading-4 text-ink focus-visible:-outline-offset-2 focus-visible:outline-2 focus-visible:outline-ink"
        >
          {mensaje}
        </button>
      </td>
    </FilaDetalle>
  )
}

function TablaRegistros({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="overflow-hidden rounded-lg border border-line">
      <table aria-label={etiqueta} className="w-full table-fixed text-left">
        <thead className="border-b border-line bg-panel-muted text-xs text-ink-soft">
          <tr>
            <th scope="col" className="w-32 px-2 py-1.5 font-medium">Fecha y hora</th>
            <th scope="col" className="w-[4.5rem] px-2 py-1.5 font-medium">Nivel</th>
            <th scope="col" className="px-2 py-1.5 font-medium">Acción</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">{children}</tbody>
      </table>
    </div>
  )
}

function SalidasDeScripts({ registros, onAbrir }: {
  registros: RegistroTarea[]
  onAbrir: AbrirRegistro
}) {
  if (!registros.length) return <p className="text-sm text-ink-soft">Aún no hay salida de scripts registrada.</p>
  return (
    <TablaRegistros etiqueta="Salidas de las tareas">
      {registros.map(({ clave, tarea, servidor }) => (
        <FilaRegistro
          key={clave}
          fecha={tarea.fechaFin ?? tarea.fechaInicio}
          registro={datosDeSalida(tarea, servidor)}
          onAbrir={(origen) => onAbrir(clave, origen)}
        />
      ))}
    </TablaRegistros>
  )
}

function DetalleRegistro({ evento, registro, analisis }: {
  evento?: EventoActividad
  registro?: RegistroTarea
  /** El análisis del evento, si es de uno: su salida explica por qué falló */
  analisis?: AnalisisRespuesta
}) {
  const tarea = registro?.tarea
  const encabezado = evento ?? (registro && datosDeSalida(registro.tarea, registro.servidor))
  if (!encabezado) return <p className="text-sm text-ink-soft">El registro ya no está disponible.</p>

  const fecha = evento ? evento.fecha : tarea?.fechaFin ?? tarea?.fechaInicio
  // Con la salida del script, el diagnóstico reconoce también un error de sintaxis
  const salidaAnalisis = evento?.nivel === 'ERROR' ? analisis?.salida : undefined
  const diagnostico = evento?.error && evento.nivel === 'ERROR'
    ? diagnosticarFallo(evento.error, tarea?.salida ?? salidaAnalisis)
    : undefined
  const servidor = evento?.servidor ?? registro?.servidor
  const version = evento?.script ?? (tarea?.script && (tarea.numeroVersion == null ? tarea.script : `${tarea.script} v${tarea.numeroVersion}`))
  const salidaTarea = (tarea?.salida || tarea?.error) && (!evento || evento.estado === 'COMPLETADO' || evento.nivel === 'ERROR')
  const mostrarSalida = salidaTarea || Boolean(salidaAnalisis)

  return (
    <section aria-label="Detalle del registro" className="flex flex-col gap-4">
      <h4 className="text-base font-semibold text-ink [overflow-wrap:anywhere]">{mensajeDelRegistro(encabezado)}</h4>
      <dl className="grid grid-cols-2 gap-3 rounded-lg border border-line p-3 sm:grid-cols-3">
        <Dato etiqueta="Fecha y hora" valor={fechaVisible(fecha) ?? 'Sin registro'} />
        <Dato etiqueta="Nivel" valor={<span className="text-[11px]"><EtiquetaNivel nivel={encabezado.nivel} /></span>} />
        {servidor && <Dato etiqueta="Servidor" valor={<span className="break-words">{servidor}</span>} mono />}
        {tarea?.orden != null && <Dato etiqueta="Paso" valor={tarea.orden} mono />}
        {version && <Dato etiqueta="Script" valor={<span className="break-words">{version}</span>} />}
        {!evento && tarea?.cuenta && <Dato etiqueta="Cuenta de servicio" valor={tarea.cuenta} />}
        {!evento && tarea?.codigoRetorno != null && <Dato etiqueta="Código de salida" valor={tarea.codigoRetorno} mono />}
        {!evento && tarea?.duracionSegundos != null && <Dato etiqueta="Duración" valor={formatearSegundos(tarea.duracionSegundos)} />}
      </dl>
      {evento?.detalle && <p className="whitespace-pre-wrap break-words text-sm text-ink-soft">{evento.detalle}</p>}
      {diagnostico && <DiagnosticoActividad fallo={diagnostico} conCausas />}
      {mostrarSalida && (
        <div className="space-y-2">
          <h5 className="text-sm font-semibold text-ink">Salida del script</h5>
          {salidaTarea ? (
            <SalidaTerminal salida={tarea?.salida} error={tarea?.error} nombre={`tarea_${tarea?.id}_${servidor ?? 'salida'}`} />
          ) : (
            <SalidaTerminal salida={salidaAnalisis} nombre={`analisis_${analisis?.id}_${servidor ?? 'salida'}`} />
          )}
        </div>
      )}
    </section>
  )
}

interface ActividadOrdenProps {
  idOrden: number
  ficha: FichaAutomatizacionRespuesta
  /** Cada cuántos milisegundos se consulta de nuevo mientras la orden avanza; sin valor no se refresca */
  refrescoMs?: number
  onCerrar: () => void
}

export function ActividadOrden({ idOrden, ficha, refrescoMs, onCerrar }: ActividadOrdenProps) {
  const [vista, setVista] = useState<keyof typeof VISTAS>('actividad')
  const [servidor, setServidor] = useState('')
  const [fase, setFase] = useState<FaseActividad | ''>('')
  const [soloErrores, setSoloErrores] = useState(false)
  const [seleccion, setSeleccion] = useState<string>()
  const origenDetalle = useRef<HTMLButtonElement>(null)
  const volver = useRef<HTMLButtonElement>(null)
  // Cada cambio de estado de la orden trae sus últimos eventos, también el que detiene el refresco
  const actividad = useConsulta(() => cicloApi.actividad(idOrden), [idOrden, ficha.orden?.estado])
  useRefrescoConsulta(actividad, refrescoMs != null, refrescoMs)
  const eventos = deRespuestas(actividad.datos)
  const mops = ficha.mops ?? []
  const servidores = [...new Set([...eventos, ...mops].flatMap((e) => e.servidor ? [e.servidor] : []))].sort()
  const filtrados = eventos.filter((e) =>
    (!servidor || !e.servidor || e.servidor === servidor)
    && (!fase || e.fase === fase)
    && (!soloErrores || e.nivel === 'ERROR'),
  )
  const tareas = mops.flatMap((m) => (m.tareas ?? []).map((tarea) => ({ clave: `salida-${tarea.id}`, tarea, servidor: m.servidor })))
  const salidas = tareas.filter(({ tarea, servidor: host }) => (!servidor || servidor === host) && (tarea.salida || tarea.error))
  const evento = seleccion ? eventos.find((e) => e.clave === seleccion) : undefined
  const registro = seleccion ? tareas.find((r) => r.clave === seleccion || (evento?.idTarea != null && r.tarea.id === evento.idTarea)) : undefined
  const codigo = ficha.orden?.codigo
  const objetivo = ficha.orden?.objetivo?.nombre
  const grupal = ficha.orden?.tipoObjetivo === 'GRUPAL'

  function abrirDetalle(clave: string, origen: HTMLButtonElement) {
    origenDetalle.current = origen
    setSeleccion(clave)
  }

  useLayoutEffect(() => {
    if (seleccion) volver.current?.focus()
    else {
      origenDetalle.current?.focus()
      origenDetalle.current = null
    }
  }, [seleccion])

  return (
    <Modal
      abierto
      posicion="derecha"
      titulo="Actividad de la orden"
      descripcion={(codigo || objetivo) && (
        <dl className="flex flex-wrap gap-x-5 gap-y-2 [overflow-wrap:anywhere]">
          {codigo && <Dato etiqueta="Orden" valor={<span className="whitespace-nowrap">{codigo}</span>} mono />}
          {objetivo && <Dato etiqueta={grupal ? 'Grupo' : 'Servidor'} valor={objetivo} mono={!grupal} />}
        </dl>
      )}
      onCerrar={onCerrar}
    >
      <div hidden={seleccion !== undefined}>
        <div className="flex flex-col gap-4">
          <div role="group" aria-label="Vista del panel" className="flex w-fit gap-1 rounded-lg bg-panel-muted p-1">
            {(Object.keys(VISTAS) as (keyof typeof VISTAS)[]).map((id) => (
              <button
                key={id}
                type="button"
                aria-pressed={vista === id}
                onClick={() => setVista(id)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium focus-visible:outline-2 focus-visible:outline-ink ${
                  vista === id ? 'bg-panel text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
                }`}
              >
                {VISTAS[id]}
              </button>
            ))}
          </div>
          {(servidores.length > 1 || vista === 'actividad') && (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
              {servidores.length > 1 && (
                <Selector aria-label="Filtrar actividad por servidor" className="sm:min-w-40 sm:flex-1" value={servidor} onChange={(e) => setServidor(e.target.value)}>
                  <option value="">Todos los servidores</option>
                  {servidores.map((s) => <option value={s} key={s}>{s}</option>)}
                </Selector>
              )}
              {vista === 'actividad' && (
                <>
                  <Selector aria-label="Filtrar por etapa" className="min-w-36 flex-1" value={fase} onChange={(e) => setFase(e.target.value as FaseActividad | '')}>
                    <option value="">Todas las etapas</option>
                    {FASES.map(({ fase: id, titulo }) => <option value={id} key={id}>{titulo}</option>)}
                  </Selector>
                  <label className="inline-flex min-h-9 shrink-0 cursor-pointer items-center gap-2 text-sm text-ink">
                    <input
                      type="checkbox"
                      checked={soloErrores}
                      onChange={(e) => setSoloErrores(e.target.checked)}
                      className="h-4 w-4 accent-ink"
                    />
                    Solo errores
                  </label>
                </>
              )}
            </div>
          )}
          {vista === 'actividad' && <MensajeError error={actividad.error} onReintentar={actividad.recargar} />}
          {vista === 'salidas' ? (
            <SalidasDeScripts registros={salidas} onAbrir={abrirDetalle} />
          ) : actividad.cargando && !actividad.datos ? (
            <Cargando />
          ) : filtrados.length ? (
            <TablaRegistros etiqueta="Eventos de la orden">
              {filtrados.map((e) => (
                <FilaRegistro
                  key={e.clave}
                  fecha={e.fecha}
                  registro={e}
                  onAbrir={(origen) => abrirDetalle(e.clave, origen)}
                />
              ))}
            </TablaRegistros>
          ) : (
            !actividad.error && (
              <p role="status" className="text-sm text-ink-soft">
                {eventos.length ? 'No hay eventos que coincidan con los filtros.' : 'Aún no hay actividad registrada.'}
              </p>
            )
          )}
        </div>
      </div>

      {seleccion !== undefined && (
        <div className="flex flex-col gap-4">
          <button
            ref={volver}
            type="button"
            onClick={() => setSeleccion(undefined)}
            className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-lg px-2 text-sm font-medium text-ink-soft hover:bg-panel-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-ink"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Volver
          </button>
          <DetalleRegistro
            evento={evento}
            registro={registro}
            analisis={evento?.idEvaluacion == null ? undefined : ficha.evaluaciones?.find((a) => a.id === evento.idEvaluacion)}
          />
        </div>
      )}
    </Modal>
  )
}
