import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Save, X } from 'lucide-react'
import { tareasApi } from '@/api/automatizacion'
import type { NivelAfectacion } from '@/api/dominio'
import type { ScriptResumenRespuesta, TareaRespuesta, UmbralRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada, Selector } from '@/components/ui/Campo'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { AFECTACION } from '@/utils/automatizacion'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'
import { formatearDuracion } from '@/utils/formato'
import { formatearLimite, simbolo } from '@/utils/umbrales'

interface TareaFormularioProps {
  tarea?: TareaRespuesta
  scripts: ScriptResumenRespuesta[]
  umbrales: UmbralRespuesta[]
  maxDuracionMinutos?: number
  onCerrar: () => void
  onGuardado: (tarea: TareaRespuesta) => void
}

const NIVELES: NivelAfectacion[] = ['SIN_AFECTACION', 'PARCIAL', 'TOTAL']

/** Límite de un nivel con su operador, por ejemplo «≥ 90 %» */
function limite(u: UmbralRespuesta, valor?: number | null): string {
  if (valor === undefined || valor === null) return '—'
  return `${simbolo(u.operador)} ${formatearLimite(valor, u.metrica?.unidad)}`
}

/**
 * Una tarea ejecuta un script de mantenimiento cuando se activa alguno de sus umbrales (RF23, RF24). Activa exige un
 * script activo con versión habilitada y al menos un umbral
 */
export function TareaFormulario({ tarea, scripts, umbrales, maxDuracionMinutos, onCerrar, onGuardado }: TareaFormularioProps) {
  const [nombre, setNombre] = useState(tarea?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(tarea?.descripcion ?? '')
  const [idScript, setIdScript] = useState(tarea?.idScript ? String(tarea.idScript) : '')
  const [afectacion, setAfectacion] = useState<NivelAfectacion>(tarea?.nivelAfectacion ?? 'SIN_AFECTACION')
  const [prioridad, setPrioridad] = useState(String(tarea?.prioridad ?? 10))
  const [duracion, setDuracion] = useState(String(tarea?.duracionEstimadaMinutos ?? 10))
  const [activo, setActivo] = useState(tarea?.activo ?? true)
  const [elegidos, setElegidos] = useState<Set<number>>(new Set(tarea?.idsUmbrales ?? []))
  const [filtroUmbrales, setFiltroUmbrales] = useState('')
  const [soloElegidos, setSoloElegidos] = useState(false)
  const [error, setError] = useState<unknown>()
  const [guardando, setGuardando] = useState(false)

  const mantenimiento = scripts.filter((s) => s.tipo === 'MANTENIMIENTO')
  const script = mantenimiento.find((s) => String(s.id) === idScript)
  const ejecutable = !!script?.activo && !!script.implementaciones?.some((i) => i.versionVigente)

  // Por métrica y nombre: un mismo indicador suele tener varios umbrales con distinto alcance
  const visibles = useMemo(() => {
    const texto = filtroUmbrales.trim().toLowerCase()
    return umbrales
      .filter((u) => u.activo || elegidos.has(u.id!))
      .filter((u) => !soloElegidos || elegidos.has(u.id!))
      .filter((u) => !texto || `${u.nombre} ${u.metrica?.nombre} ${u.metrica?.codigo}`.toLowerCase().includes(texto))
      .sort(
        (a, b) =>
          (a.metrica?.nombre ?? '').localeCompare(b.metrica?.nombre ?? '', 'es') ||
          (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es'),
      )
  }, [umbrales, filtroUmbrales, elegidos, soloElegidos])
  const marcados = umbrales.filter((u) => elegidos.has(u.id!))

  const duracionNumero = Number(duracion)
  const excedeDuracion = !!maxDuracionMinutos && duracionNumero > maxDuracionMinutos
  const faltaUmbral = activo && elegidos.size === 0
  const faltaScript = activo && !!script && !ejecutable

  function alternar(id: number) {
    const copia = new Set(elegidos)
    if (copia.has(id)) copia.delete(id)
    else copia.add(id)
    setElegidos(copia)
  }

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      const guardada = await tareasApi.guardar(tarea?.id, {
        idScript: Number(idScript),
        nombre: nombre.trim(),
        descripcion: descripcion.trim() || undefined,
        nivelAfectacion: afectacion,
        prioridad: Number(prioridad),
        duracionEstimadaMinutos: duracionNumero,
        activo,
        idsUmbrales: [...elegidos],
      })
      onGuardado(guardada)
    } catch (err) {
      setError(err)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho="2xl"
      titulo={tarea ? 'Editar tarea' : 'Nueva tarea'}
      onCerrar={onCerrar}
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton
            variante="primario"
            icono={Save}
            type="submit"
            form="formulario-tarea"
            cargando={guardando}
            disabled={!nombre.trim() || !idScript || excedeDuracion || faltaUmbral || faltaScript}
          >
            Guardar
          </Boton>
        </>
      }
    >
      <form id="formulario-tarea" onSubmit={guardar} className="flex flex-col gap-6">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <div className="grid gap-5 lg:grid-cols-2">
          <div className="flex flex-col gap-4">
            <Campo etiqueta="Nombre" obligatorio error={errorDeCampo(error, 'nombre')}>
              <Entrada value={nombre} maxLength={255} autoFocus onChange={(e) => setNombre(e.target.value)} />
            </Campo>

            <Campo etiqueta="Descripción" error={errorDeCampo(error, 'descripcion')}>
              <AreaTexto value={descripcion} maxLength={500} onChange={(e) => setDescripcion(e.target.value)} />
            </Campo>

            <Campo
              etiqueta="Script de mantenimiento"
              obligatorio
              error={faltaScript ? 'El script está inactivo o no tiene una versión habilitada' : errorDeCampo(error, 'idScript')}
            >
              <Selector value={idScript} onChange={(e) => setIdScript(e.target.value)}>
                <option value="">Seleccione…</option>
                {mantenimiento.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.nombre}
                    {!s.activo ? ' (inactivo)' : !s.implementaciones?.some((i) => i.versionVigente) ? ' (sin versión)' : ''}
                  </option>
                ))}
              </Selector>
            </Campo>
            {script && (
              <div className="-mt-2 flex flex-wrap gap-1.5" aria-label="Sistemas operativos compatibles">
                {(script.implementaciones ?? []).length === 0 ? (
                  <StatusPill tone="warning" label="Sin implementaciones" />
                ) : (
                  script.implementaciones?.map((i) => (
                    <span
                      key={i.id}
                      className={`rounded-md border px-2 py-0.5 text-xs ${
                        i.versionVigente ? 'border-line bg-panel-muted text-ink' : 'border-warning/40 bg-warning-soft text-warning'
                      }`}
                    >
                      {i.sistemaOperativo} {i.versionVigente ? `· v${i.versionVigente}` : '· sin versión'}
                    </span>
                  ))
                )}
                <p className="w-full text-xs text-ink-faint">
                  En servidores con otro sistema operativo la tarea queda sin implementación y la orden lo advierte.
                </p>
              </div>
            )}
          </div>

          <div className="flex flex-col gap-4">
            <fieldset>
              <legend className="mb-2 text-sm font-medium text-ink">Afectación del servicio</legend>
              <div className="flex flex-col gap-2">
                {NIVELES.map((n) => (
                  <label
                    key={n}
                    className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm transition-colors ${
                      afectacion === n ? 'border-accent bg-accent-soft' : 'border-line hover:bg-panel-muted'
                    }`}
                  >
                    <input
                      type="radio"
                      name="afectacion"
                      checked={afectacion === n}
                      onChange={() => setAfectacion(n)}
                      className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
                    />
                    <span>
                      <span className="font-medium text-ink">{AFECTACION[n].etiqueta}</span>
                      <span className="block text-xs text-ink-soft">{AFECTACION[n].descripcion}</span>
                    </span>
                  </label>
                ))}
              </div>
              <p className="mt-2 text-xs text-ink-faint">
                La política de cambio del entorno decide quién autoriza según esta afectación.
              </p>
            </fieldset>

            <div className="grid grid-cols-2 gap-4">
              <Campo etiqueta="Prioridad" obligatorio ayuda="Menor número, se ejecuta antes" error={errorDeCampo(error, 'prioridad')}>
                <Entrada type="number" min={0} value={prioridad} onChange={(e) => setPrioridad(e.target.value)} />
              </Campo>
              <Campo
                etiqueta="Duración estimada (min)"
                obligatorio
                error={
                  excedeDuracion
                    ? `Supera el máximo por tarea (${formatearDuracion(maxDuracionMinutos)})`
                    : errorDeCampo(error, 'duracionEstimadaMinutos')
                }
                ayuda={maxDuracionMinutos ? `Máximo ${formatearDuracion(maxDuracionMinutos)}; al superarla se detiene` : undefined}
              >
                <Entrada type="number" min={1} value={duracion} onChange={(e) => setDuracion(e.target.value)} />
              </Campo>
            </div>

            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                checked={activo}
                onChange={(e) => setActivo(e.target.checked)}
                className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
              />
              <span>
                <span className="font-medium text-ink">Activa</span>
                <span className="block text-xs text-ink-soft">
                  Solo las tareas activas entran en las MOP. Necesita al menos un umbral.
                </span>
              </span>
            </label>
          </div>
        </div>

        <section className="flex flex-col gap-3 border-t border-line pt-5" aria-labelledby="umbrales-tarea">
          <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-0 flex-1 basis-80">
              <h4 id="umbrales-tarea" className="text-sm font-semibold text-ink">
                ¿Cuándo se ejecuta? Umbrales que la activan {activo && <span className="text-danger">*</span>}
              </h4>
              <p className="mt-0.5 text-sm text-ink-soft">
                La tarea entra en el plan de un servidor cuando su análisis previo deja alguno de los umbrales marcados en
                advertencia o crítico. Marque uno o más.
              </p>
            </div>
            <label className="flex items-center gap-2 text-sm text-ink-soft">
              <input
                type="checkbox"
                checked={soloElegidos}
                onChange={(e) => setSoloElegidos(e.target.checked)}
                className="h-4 w-4 accent-[var(--color-accent)]"
              />
              Solo los marcados
            </label>
            <CampoBusqueda
              valor={filtroUmbrales}
              onCambiar={setFiltroUmbrales}
              placeholder="Buscar umbral o métrica"
              aria-label="Buscar umbrales"
            />
          </div>

          <div className="flex flex-wrap items-center gap-1.5 text-xs" aria-live="polite">
            <span className="text-ink-soft">
              {marcados.length === 0 ? 'Ningún umbral marcado.' : `${marcados.length} marcado${marcados.length === 1 ? '' : 's'}:`}
            </span>
            {marcados.map((u) => (
              <button
                key={u.id}
                type="button"
                onClick={() => alternar(u.id!)}
                title="Quitar"
                className="inline-flex items-center gap-1 rounded-full bg-accent-soft px-2.5 py-0.5 text-ink hover:bg-panel-muted"
              >
                {u.nombre}
                <X className="h-3 w-3" aria-hidden="true" />
              </button>
            ))}
          </div>

          <div className="max-h-[22rem] overflow-auto rounded-lg border border-line">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead className="sticky top-0 z-10 bg-panel">
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="w-10 px-3 py-2">
                    <span className="sr-only">Activa la tarea</span>
                  </th>
                  <th className="px-3 py-2 font-medium">Umbral</th>
                  <th className="px-3 py-2 font-medium">Métrica</th>
                  <th className="px-3 py-2 font-medium">Advertencia</th>
                  <th className="px-3 py-2 font-medium">Crítico</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {visibles.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-sm text-ink-soft">
                      Ningún umbral coincide.
                    </td>
                  </tr>
                ) : (
                  visibles.map((u) => {
                    const marcado = elegidos.has(u.id!)
                    return (
                      <tr
                        key={u.id}
                        onClick={() => alternar(u.id!)}
                        className={`cursor-pointer transition-colors ${marcado ? 'bg-accent-soft' : 'hover:bg-panel-muted'}`}
                      >
                        <td className="px-3 py-2">
                          <input
                            type="checkbox"
                            checked={marcado}
                            onChange={() => alternar(u.id!)}
                            onClick={(e) => e.stopPropagation()}
                            aria-label={`Activar con ${u.nombre}`}
                            className="h-4 w-4 accent-[var(--color-accent)]"
                          />
                        </td>
                        <td className="px-3 py-2 text-ink">
                          {u.nombre}
                          {!u.activo && <span className="ml-2 text-xs text-warning">inactivo</span>}
                        </td>
                        <td className="px-3 py-2 text-ink-soft">{u.metrica?.nombre ?? '—'}</td>
                        {u.lista ? (
                          <td colSpan={2} className="px-3 py-2 text-ink-soft">
                            Compara con la lista «{u.lista.nombre}»
                          </td>
                        ) : (
                          <>
                            <td className="px-3 py-2 font-mono text-xs text-warning">{limite(u, u.valorAdvertencia)}</td>
                            <td className="px-3 py-2 font-mono text-xs text-danger">{limite(u, u.valorCritico)}</td>
                          </>
                        )}
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>
          {faltaUmbral && <p className="text-xs text-danger">Marque al menos un umbral para una tarea activa.</p>}
        </section>
      </form>
    </Modal>
  )
}
