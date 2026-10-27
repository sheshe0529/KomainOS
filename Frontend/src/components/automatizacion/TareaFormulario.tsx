import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
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

function descripcionUmbral(u: UmbralRespuesta): string {
  if (u.lista) return `Lista ${u.lista.nombre}`
  const partes = [
    u.valorAdvertencia !== undefined && u.valorAdvertencia !== null
      ? `advertencia ${simbolo(u.operador)} ${formatearLimite(u.valorAdvertencia, u.metrica?.unidad)}`
      : '',
    u.valorCritico !== undefined && u.valorCritico !== null
      ? `crítico ${simbolo(u.operador)} ${formatearLimite(u.valorCritico, u.metrica?.unidad)}`
      : '',
  ]
  return partes.filter(Boolean).join(' · ')
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
  const [error, setError] = useState<unknown>()
  const [guardando, setGuardando] = useState(false)

  const mantenimiento = scripts.filter((s) => s.tipo === 'MANTENIMIENTO')
  const script = mantenimiento.find((s) => String(s.id) === idScript)
  const ejecutable = !!script?.activo && !!script.implementaciones?.some((i) => i.versionVigente)

  // Umbrales agrupados por métrica: un mismo indicador suele tener varios umbrales con distinto alcance
  const grupos = useMemo(() => {
    const texto = filtroUmbrales.trim().toLowerCase()
    const mapa = new Map<string, UmbralRespuesta[]>()
    for (const u of umbrales) {
      const coincide =
        !texto || `${u.nombre} ${u.metrica?.nombre} ${u.metrica?.codigo}`.toLowerCase().includes(texto) || elegidos.has(u.id!)
      if (!coincide || (!u.activo && !elegidos.has(u.id!))) continue
      const clave = u.metrica?.nombre ?? 'Sin métrica'
      mapa.set(clave, [...(mapa.get(clave) ?? []), u])
    }
    return [...mapa.entries()].sort(([a], [b]) => a.localeCompare(b, 'es'))
  }, [umbrales, filtroUmbrales, elegidos])

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
      ancho="xl"
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
      <form id="formulario-tarea" onSubmit={guardar} className="grid gap-5 lg:grid-cols-2">
        <div className="flex flex-col gap-4">
          {!tieneErroresDeCampo(error) && <MensajeError error={error} />}

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

        <div className="flex min-h-0 flex-col gap-2">
          <div className="flex items-baseline justify-between gap-2">
            <span className="text-sm font-medium text-ink">
              Umbrales que la activan <span className="text-danger">{activo ? '*' : ''}</span>
            </span>
            <span className="text-xs text-ink-soft">{elegidos.size} seleccionados</span>
          </div>
          <CampoBusqueda
            valor={filtroUmbrales}
            onCambiar={setFiltroUmbrales}
            placeholder="Buscar umbral o métrica"
            aria-label="Buscar umbrales"
            className="sm:max-w-none"
          />
          <div className="max-h-[26rem] overflow-y-auto rounded-lg border border-line">
            {grupos.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-ink-soft">Ningún umbral activo coincide.</p>
            ) : (
              grupos.map(([metrica, lista]) => (
                <div key={metrica}>
                  <p className="sticky top-0 border-b border-line bg-panel-muted px-3 py-1.5 text-xs font-medium uppercase tracking-wide text-ink-faint">
                    {metrica}
                  </p>
                  {lista.map((u) => (
                    <label
                      key={u.id}
                      className={`flex cursor-pointer items-start gap-3 border-b border-line px-3 py-2 text-sm last:border-b-0 ${
                        elegidos.has(u.id!) ? 'bg-accent-soft' : 'hover:bg-panel-muted'
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={elegidos.has(u.id!)}
                        onChange={() => alternar(u.id!)}
                        className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
                      />
                      <span className="min-w-0">
                        <span className="block text-ink">
                          {u.nombre}
                          {!u.activo && <span className="ml-2 text-xs text-warning">inactivo</span>}
                        </span>
                        <span className="block text-xs text-ink-soft">{descripcionUmbral(u)}</span>
                      </span>
                    </label>
                  ))}
                </div>
              ))
            )}
          </div>
          {faltaUmbral && <p className="text-xs text-danger">Seleccione al menos un umbral para una tarea activa.</p>}
        </div>
      </form>
    </Modal>
  )
}
