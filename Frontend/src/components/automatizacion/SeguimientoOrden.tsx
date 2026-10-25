import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Download, Play, ScanSearch } from 'lucide-react'
import { automatizacionApi } from '@/api/automatizacion'
import type { EstadoOrden } from '@/api/dominio'
import { Tarjeta } from '@/components/common/Tarjeta'
import { Boton } from '@/components/ui/Boton'
import { Campo, AreaTexto, Entrada } from '@/components/ui/Campo'
import { Modal } from '@/components/ui/Modal'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'
import { useSesion } from '@/auth/sesion-context'
import { formatearFechaHora } from '@/utils/formato'
import { compararMetricas } from '@/utils/compararMetricas'
type Accion = 'aprobar' | 'rechazar' | 'proponer' | 'conforme' | 'no-conforme' | 'cerrar'
export function SeguimientoOrden({
  id,
  estado,
  onActualizar,
}: {
  id: number
  estado: EstadoOrden
  onActualizar: () => void
}) {
  const { usuario, tieneRol } = useSesion()
  const admin = tieneRol('ADMINISTRADOR')
  const informe = useConsulta(() => automatizacionApi.ficha(id), [id])
  const [pestana, setPestana] = useState('mop')
  const [accion, setAccion] = useState<Accion>()
  const [motivo, setMotivo] = useState('')
  const [fecha, setFecha] = useState('')
  const [error, setError] = useState<unknown>()
  const [trabajando, setTrabajando] = useState(false)
  useEffect(() => {
    if (estado === 'CERRADA') return
    const timer = window.setInterval(() => {
      informe.recargar()
      onActualizar()
    }, 5000)
    return () => window.clearInterval(timer)
  }, [estado, informe.recargar, onActualizar])
  async function ejecutar(fn: () => Promise<unknown>) {
    setTrabajando(true)
    setError(undefined)
    try {
      await fn()
      setAccion(undefined)
      informe.recargar()
      onActualizar()
    } catch (e) {
      setError(e)
    } finally {
      setTrabajando(false)
    }
  }
  function abrir(a: Accion) {
    setAccion(a)
    setMotivo('')
    setFecha('')
    setError(undefined)
  }
  async function confirmar() {
    await ejecutar(() =>
      accion === 'conforme' || accion === 'no-conforme'
        ? automatizacionApi.validar(id, accion === 'conforme', motivo)
        : accion === 'cerrar'
          ? automatizacionApi.cerrar(id, motivo)
          : automatizacionApi.decidir(id, {
              decision: accion === 'aprobar' ? 'APROBADA' : accion === 'rechazar' ? 'RECHAZADA' : 'FECHA_PROPUESTA',
              motivo,
              fechaPropuesta: accion === 'proponer' ? new Date(fecha).toISOString() : undefined,
            }),
    )
  }
  const f = informe.datos
  const ronda = f?.autorizaciones?.at(-1)
  const pendiente = ronda?.decisiones?.find((d) => d.estado === 'PENDIENTE')
  const propuesta = ronda?.decisiones?.find((d) => d.estado === 'FECHA_PROPUESTA')
  const puedeDecidir =
    estado === 'PENDIENTE_AUTORIZACION' &&
    ((admin && pendiente?.tipo === 'ADMINISTRADOR') || (tieneRol('RESPONSABLE') && pendiente?.tipo === 'RESPONSABLE'))
  const validacion = f?.validaciones?.find((v) => v.idResponsable === usuario?.id && v.estado === 'PENDIENTE')
  function descargar() {
    const blob = new Blob([JSON.stringify(f, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `bitacora_orden_${id}.json`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  return (
    <Tarjeta
      titulo="Automatización, MOP y bitácora"
      className="mt-6"
      acciones={
        <Boton icono={Download} onClick={descargar} disabled={!f}>
          Descargar bitácora
        </Boton>
      }
    >
      {f?.simulado && (
        <p className="mb-4 rounded-lg bg-panel-muted p-3 text-sm text-ink-soft">
          Resultados simulados para pruebas locales. Ansible real está desactivado.
        </p>
      )}
      <MensajeError error={error ?? informe.error} />
      <div className="mb-4 flex flex-wrap gap-2">
        {admin && estado === 'PROGRAMADA' && (
          <Boton icono={ScanSearch} cargando={trabajando} onClick={() => ejecutar(() => automatizacionApi.evaluar(id))}>
            Evaluar mantenimiento
          </Boton>
        )}
        {admin && ['AUTORIZADA', 'EN_COLA', 'EN_EJECUCION'].includes(estado) && (
          <Boton icono={Play} cargando={trabajando} onClick={() => ejecutar(automatizacionApi.ejecutar)}>
            Procesar cola
          </Boton>
        )}
        {puedeDecidir && !propuesta && (
          <>
            <Boton variante="primario" onClick={() => abrir('aprobar')}>
              Aprobar
            </Boton>
            <Boton onClick={() => abrir('rechazar')}>Rechazar</Boton>
            {tieneRol('RESPONSABLE') && <Boton onClick={() => abrir('proponer')}>Proponer otra fecha</Boton>}
          </>
        )}
        {admin && propuesta && estado === 'PENDIENTE_AUTORIZACION' && (
          <Boton cargando={trabajando} onClick={() => ejecutar(() => automatizacionApi.aceptarFecha(id))}>
            Aceptar fecha propuesta y renovar solicitudes
          </Boton>
        )}
        {validacion && estado === 'PENDIENTE_VALIDACION' && (
          <>
            <Boton variante="primario" onClick={() => abrir('conforme')}>
              Validar conforme
            </Boton>
            <Boton onClick={() => abrir('no-conforme')}>Reportar no conformidad</Boton>
          </>
        )}
        {admin && estado === 'SIN_IMPLEMENTACION' && (
          <Boton onClick={() => abrir('cerrar')}>Cerrar sin intervención</Boton>
        )}
      </div>
      <div className="mb-5 flex flex-wrap gap-2" role="tablist" aria-label="Seguimiento de mantenimiento">
        {[
          ['mop', 'MOP y tareas'],
          ['comparativo', 'Métricas antes y después'],
          ['aprobaciones', 'Autorizaciones y validaciones'],
          ['incidencias', 'Incidencias'],
        ].map(([id, t]) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={pestana === id}
            onClick={() => setPestana(id)}
            className={`rounded-full px-4 py-2 text-sm ${pestana === id ? 'bg-accent text-accent-ink' : 'bg-panel-muted text-ink-soft'}`}
          >
            {t}
          </button>
        ))}
      </div>
      {pestana === 'mop' && (
        <div className="flex flex-col gap-4">
          {!f?.mops?.length && (
            <p className="text-sm text-ink-soft">La MOP estará disponible después de la evaluación previa.</p>
          )}
          {f?.mops?.map((m) => (
            <section key={m.id} className="rounded-lg border border-line p-4">
              <h4 className="mb-3 font-medium">
                {m.servidor} · {m.duracionMinutos} min {m.piloto && '· Servidor piloto'}
              </h4>
              {m.superaDuracionMaxima && (
                <p className="mb-3 text-sm text-warning">
                  La duración estimada supera el máximo por MOP configurado. Revise las tareas antes de autorizar.
                </p>
              )}
              <div className="overflow-x-auto">
                <table className="w-full min-w-[660px] text-left text-sm">
                  <thead className="text-xs text-ink-soft">
                    <tr>
                      {['Tarea', 'Prioridad', 'Versión fijada', 'Cuenta', 'Estado', 'Resultado'].map((t) => (
                        <th className="p-2 font-medium" key={t}>
                          {t}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {m.tareas?.map((t) => (
                      <tr key={t.id}>
                        <td className="p-2">
                          {t.tarea}
                          <span className="block text-xs text-ink-soft">
                            {t.duracionMinutos} min · {t.afectacion?.replaceAll('_', ' ')}
                          </span>
                        </td>
                        <td className="p-2">{t.prioridad}</td>
                        <td className="p-2">
                          {t.idVersion ? `v${t.numeroVersion} · #${t.idVersion}` : 'Sin implementación compatible'}
                        </td>
                        <td className="p-2">#{t.idCuentaServicio}</td>
                        <td className="p-2">{t.estado?.replaceAll('_', ' ')}</td>
                        <td className="p-2">
                          <span className="text-danger">{t.error}</span>
                          {t.salida && (
                            <details>
                              <summary className="cursor-pointer text-accent">Ver salida</summary>
                              <pre className="mt-2 max-h-44 max-w-md overflow-auto whitespace-pre-wrap rounded bg-panel-muted p-2 text-xs">
                                {t.salida}
                              </pre>
                            </details>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
        </div>
      )}
      {pestana === 'comparativo' && (
        <>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[650px] text-left text-sm">
              <thead className="text-xs text-ink-soft">
                <tr>
                  {['Servidor', 'Métrica / instancia', 'Antes', 'Después', 'Estado posterior'].map((t) => (
                    <th key={t} className="p-2 font-medium">
                      {t}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {compararMetricas(f?.evaluaciones ?? []).map((m) => (
                  <tr key={m.clave}>
                    <td className="p-2 font-mono">{m.servidor}</td>
                    <td className="p-2">
                      {m.nombre}
                      <span className="ml-1 text-xs text-ink-soft">{m.instancia}</span>
                    </td>
                    <td className="p-2">{m.antes}</td>
                    <td className="p-2">{m.despues}</td>
                    <td className="p-2">{m.nivel}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {f?.evaluaciones?.map((e) => (
            <p key={e.id} className="mt-2 text-xs text-ink-soft">
              {e.servidor} · {e.tipo} · {e.estado} · script #{e.idVersion} · {formatearFechaHora(e.fechaInicio)}{' '}
              {e.error && <span className="text-danger">· {e.error}</span>}
            </p>
          ))}
        </>
      )}
      {pestana === 'aprobaciones' && (
        <div className="flex flex-col gap-4">
          {f?.validacionesTecnicas?.map((v) => (
            <div key={v.idDetalle} className="rounded-lg bg-panel-muted p-3 text-sm">
              <strong>Validación técnica · Detalle #{v.idDetalle}</strong>
              <p>
                {v.resultado?.replaceAll('_', ' ')} · {formatearFechaHora(v.fecha)}
              </p>
              <p className="text-ink-soft">{v.observaciones}</p>
            </div>
          ))}
          {f?.autorizaciones?.map((s) => (
            <section key={s.id} className="rounded-lg border border-line p-4">
              <h4 className="mb-2 font-medium">
                Ronda {s.ronda} · {s.entorno} · {s.afectacion?.replaceAll('_', ' ')}
              </h4>
              <p className="mb-3 text-sm text-ink-soft">Plazo compartido: {formatearFechaHora(s.fechaLimite)}</p>
              {s.decisiones?.length === 0 && <p className="text-sm">Autorización automática.</p>}
              {s.decisiones?.map((d) => (
                <div key={d.id} className="mb-2 rounded bg-panel-muted p-3 text-sm">
                  {d.secuencia}. {d.tipo} · {d.estado?.replaceAll('_', ' ')}
                  <p className="text-xs text-ink-soft">
                    {d.usuario} {d.motivo}{' '}
                    {d.fechaPropuesta && `· Fecha propuesta: ${formatearFechaHora(d.fechaPropuesta)}`}
                  </p>
                </div>
              ))}
            </section>
          ))}
          {f?.validaciones?.map((v) => (
            <div key={v.id} className="rounded-lg border border-line p-4 text-sm">
              <strong>Validación funcional · {v.responsable}</strong>
              <p>
                {v.estado} · Hasta {formatearFechaHora(v.fechaLimite)}
              </p>
              <p className="text-ink-soft">{v.observaciones}</p>
            </div>
          ))}
        </div>
      )}
      {pestana === 'incidencias' && (
        <div className="flex flex-col gap-3">
          {f?.incidencias?.length === 0 && <p className="text-sm text-ink-soft">Sin incidencias registradas.</p>}
          {f?.incidencias?.map((i) => (
            <div key={i.id} className="rounded-lg border border-line p-4 text-sm">
              <strong>
                {i.servidor} · {i.estado}
              </strong>
              <p>
                {i.origen?.replaceAll('_', ' ')} · {i.error}
              </p>
              <p className="text-ink-soft">
                {i.diagnostico} {i.resultado}
              </p>
              {admin && i.estado !== 'CERRADA' && (
                <Link className="mt-2 inline-block text-accent" to="/automatizacion/incidencias">
                  Atender incidencia
                </Link>
              )}
            </div>
          ))}
        </div>
      )}
      {accion && (
        <Modal
          abierto
          titulo={
            {
              aprobar: 'Aprobar mantenimiento',
              rechazar: 'Rechazar mantenimiento',
              proponer: 'Proponer otra fecha',
              conforme: 'Conformidad funcional',
              'no-conforme': 'Registrar no conformidad',
              cerrar: 'Cerrar sin intervención',
            }[accion]
          }
          onCerrar={() => setAccion(undefined)}
          pie={
            <>
              <Boton onClick={() => setAccion(undefined)}>Cancelar</Boton>
              <Boton
                variante="primario"
                cargando={trabajando}
                disabled={!motivo.trim() || (accion === 'proponer' && !fecha)}
                onClick={confirmar}
              >
                Confirmar
              </Boton>
            </>
          }
        >
          <div className="flex flex-col gap-4">
            <MensajeError error={error} />
            {accion === 'proponer' && (
              <Campo
                etiqueta="Fecha propuesta"
                obligatorio
                ayuda="El administrador verificará ventana, capacidad y conflictos antes de aceptarla."
              >
                <Entrada type="datetime-local" value={fecha} onChange={(e) => setFecha(e.target.value)} />
              </Campo>
            )}
            <Campo etiqueta="Motivo u observaciones" obligatorio>
              <AreaTexto value={motivo} maxLength={1000} onChange={(e) => setMotivo(e.target.value)} />
            </Campo>
          </div>
        </Modal>
      )}
    </Tarjeta>
  )
}
