import { useState } from 'react'
import type { FormEvent } from 'react'
import { Activity, Save, Wrench } from 'lucide-react'
import { scriptsApi } from '@/api/automatizacion'
import type { TipoScript } from '@/api/dominio'
import type { ScriptRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { DESCRIPCION_TIPO_SCRIPT, ETIQUETA_TIPO_SCRIPT } from '@/utils/automatizacion'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'

interface ScriptFormularioProps {
  script?: ScriptRespuesta
  onCerrar: () => void
  onGuardado: (script: ScriptRespuesta) => void
}

const ICONOS: Record<TipoScript, typeof Activity> = { ANALISIS: Activity, MANTENIMIENTO: Wrench }

/** Datos generales del script. El código se escribe por sistema operativo, en sus implementaciones */
export function ScriptFormulario({ script, onCerrar, onGuardado }: ScriptFormularioProps) {
  const [nombre, setNombre] = useState(script?.nombre ?? '')
  const [tipo, setTipo] = useState<TipoScript>(script?.tipo ?? 'MANTENIMIENTO')
  const [descripcion, setDescripcion] = useState(script?.descripcion ?? '')
  const [activo, setActivo] = useState(script?.activo ?? true)
  const [error, setError] = useState<unknown>()
  const [guardando, setGuardando] = useState(false)

  async function guardar(e: FormEvent) {
    e.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      const datos = { nombre: nombre.trim(), tipo, descripcion: descripcion.trim() || undefined, activo }
      onGuardado(script?.id ? await scriptsApi.actualizar(script.id, datos) : await scriptsApi.registrar(datos))
    } catch (err) {
      setError(err)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      titulo={script ? 'Editar script' : 'Nuevo script'}
      descripcion={script ? undefined : 'Después agregará el código para cada sistema operativo.'}
      onCerrar={onCerrar}
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" icono={Save} type="submit" form="formulario-script" cargando={guardando} disabled={!nombre.trim()}>
            {script ? 'Guardar' : 'Crear script'}
          </Boton>
        </>
      }
    >
      <form id="formulario-script" onSubmit={guardar} className="flex flex-col gap-4">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-medium text-ink">Tipo</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {(['ANALISIS', 'MANTENIMIENTO'] as TipoScript[]).map((t) => {
              const Icono = ICONOS[t]
              const elegido = tipo === t
              return (
                <label
                  key={t}
                  className={`flex cursor-pointer gap-3 rounded-lg border p-3 text-sm transition-colors ${
                    elegido ? 'border-accent bg-accent-soft' : 'border-line hover:bg-panel-muted'
                  } ${script ? 'cursor-not-allowed opacity-60' : ''}`}
                >
                  <input
                    type="radio"
                    name="tipo-script"
                    value={t}
                    checked={elegido}
                    disabled={!!script}
                    onChange={() => setTipo(t)}
                    className="sr-only"
                  />
                  <Icono className="mt-0.5 h-4 w-4 shrink-0 text-ink-soft" aria-hidden="true" />
                  <span>
                    <span className="block font-medium text-ink">{ETIQUETA_TIPO_SCRIPT[t]}</span>
                    <span className="text-xs text-ink-soft">{DESCRIPCION_TIPO_SCRIPT[t]}</span>
                  </span>
                </label>
              )
            })}
          </div>
          {script && <p className="text-xs text-ink-faint">El tipo no cambia después de registrar el script.</p>}
        </fieldset>

        <Campo etiqueta="Nombre" obligatorio error={errorDeCampo(error, 'nombre')}>
          <Entrada value={nombre} maxLength={255} autoFocus onChange={(e) => setNombre(e.target.value)} />
        </Campo>

        <Campo
          etiqueta="Descripción"
          error={errorDeCampo(error, 'descripcion')}
          ayuda="Qué revisa o corrige y en qué condiciones conviene usarlo."
        >
          <AreaTexto value={descripcion} maxLength={500} onChange={(e) => setDescripcion(e.target.value)} />
        </Campo>

        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            checked={activo}
            onChange={(e) => setActivo(e.target.checked)}
            className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
          />
          <span>
            <span className="font-medium text-ink">Activo</span>
            <span className="block text-xs text-ink-soft">
              {tipo === 'ANALISIS'
                ? 'Solo un análisis activo por versión de sistema operativo: es el que evalúa los umbrales.'
                : 'Un script inactivo no se puede asignar a tareas nuevas.'}
            </span>
          </span>
        </label>
      </form>
    </Modal>
  )
}
