import { useState } from 'react'
import type { FormEvent } from 'react'
import { Pencil, Plus, Power, PowerOff, Save, Trash2 } from 'lucide-react'
import { catalogosApi } from '@/api/catalogos'
import type { NivelCriticidadPeticion, NivelCriticidadRespuesta } from '@/api/types'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Campo, Entrada } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { invalidarCriticidades, tonoSegunCatalogo } from '@/hooks/useTonoCriticidad'
import { errorDeCampo, textoDeError, tieneErroresDeCampo } from '@/utils/errores'

type Formulario = Record<keyof NivelCriticidadPeticion, string>

const VACIO: Formulario = {
  nombre: '',
  prioridad: '',
  frecuenciaRevisionDias: '',
  frecuenciaMantenimientoDias: '',
  plazoAutorizacionHoras: '',
  plazoValidacionHoras: '',
}

export function CriticidadesTab() {
  const { avisar } = useAvisos()
  const niveles = useConsulta(() => catalogosApi.criticidades(), [])
  const prioridadesActivas = (niveles.datos ?? [])
    .filter((n) => n.activo && n.prioridad !== undefined)
    .map((n) => n.prioridad!)
  const [editando, setEditando] = useState<NivelCriticidadRespuesta | 'nuevo' | null>(null)
  const [f, setF] = useState<Formulario>(VACIO)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  function abrir(nivel: NivelCriticidadRespuesta | 'nuevo') {
    setError(undefined)
    setEditando(nivel)
    setF(
      nivel === 'nuevo'
        ? VACIO
        : {
            nombre: nivel.nombre ?? '',
            prioridad: String(nivel.prioridad ?? ''),
            frecuenciaRevisionDias: String(nivel.frecuenciaRevisionDias ?? ''),
            frecuenciaMantenimientoDias: String(nivel.frecuenciaMantenimientoDias ?? ''),
            plazoAutorizacionHoras: String(nivel.plazoAutorizacionHoras ?? ''),
            plazoValidacionHoras: String(nivel.plazoValidacionHoras ?? ''),
          },
    )
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    const datos: NivelCriticidadPeticion = {
      nombre: f.nombre.trim(),
      prioridad: Number(f.prioridad),
      frecuenciaRevisionDias: Number(f.frecuenciaRevisionDias),
      frecuenciaMantenimientoDias: Number(f.frecuenciaMantenimientoDias),
      plazoAutorizacionHoras: Number(f.plazoAutorizacionHoras),
      plazoValidacionHoras: Number(f.plazoValidacionHoras),
    }
    try {
      if (editando === 'nuevo') await catalogosApi.crearCriticidad(datos)
      else if (editando?.id) await catalogosApi.actualizarCriticidad(editando.id, datos)
      setEditando(null)
      invalidarCriticidades()
      niveles.recargar()
      avisar('Nivel de criticidad guardado.')
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  async function ejecutar(accion: () => Promise<unknown>, mensaje: string) {
    try {
      await accion()
      invalidarCriticidades()
      niveles.recargar()
      avisar(mensaje)
    } catch (e) {
      avisar(textoDeError(e), 'error')
    }
  }

  const numero = (campo: keyof Formulario, etiqueta: string, minimo: number, ayuda?: string) => (
    <Campo etiqueta={etiqueta} obligatorio error={errorDeCampo(error, campo)} ayuda={ayuda}>
      <Entrada type="number" min={minimo} value={f[campo]} onChange={(e) => setF((p) => ({ ...p, [campo]: e.target.value }))} required />
    </Campo>
  )

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-ink-soft">
          Menor prioridad = mayor criticidad. Las frecuencias se copian al crear una configuración de mantenimiento; cambiarlas no
          altera configuraciones existentes.
        </p>
        <Boton variante="primario" icono={Plus} onClick={() => abrir('nuevo')}>
          Nuevo nivel
        </Boton>
      </div>
      <MensajeError error={niveles.error} onReintentar={niveles.recargar} />
      {niveles.cargando && !niveles.datos ? (
        <Cargando />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-line bg-panel">
          <table className="w-full min-w-[820px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-4 py-3 font-medium">Nivel</th>
                <th className="px-4 py-3 font-medium">Prioridad</th>
                <th className="px-4 py-3 font-medium">Revisión</th>
                <th className="px-4 py-3 font-medium">Mantenimiento</th>
                <th className="px-4 py-3 font-medium">Plazo autorización</th>
                <th className="px-4 py-3 font-medium">Plazo validación</th>
                <th className="px-4 py-3 font-medium">Estado</th>
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {niveles.datos?.map((n) => (
                <tr key={n.id} className="hover:bg-panel-muted">
                  <td className="px-4 py-3">
                    <StatusPill tone={n.activo ? tonoSegunCatalogo(n.prioridad, prioridadesActivas) : 'neutral'} label={n.nombre ?? ''} />
                  </td>
                  <td className="px-4 py-3 font-mono tabular-nums">{n.prioridad}</td>
                  <td className="px-4 py-3 text-ink-soft">Cada {n.frecuenciaRevisionDias} días</td>
                  <td className="px-4 py-3 text-ink-soft">Cada {n.frecuenciaMantenimientoDias} días</td>
                  <td className="px-4 py-3 text-ink-soft">{n.plazoAutorizacionHoras} h</td>
                  <td className="px-4 py-3 text-ink-soft">{n.plazoValidacionHoras} h</td>
                  <td className="px-4 py-3">
                    <StatusPill tone={n.activo ? 'success' : 'neutral'} label={n.activo ? 'Activo' : 'Inactivo'} />
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <BotonIcono icono={Pencil} etiqueta="Editar" onClick={() => abrir(n)} />
                      <BotonIcono
                        icono={n.activo ? PowerOff : Power}
                        etiqueta={n.activo ? 'Desactivar' : 'Activar'}
                        onClick={() => ejecutar(() => catalogosApi.cambiarEstadoCriticidad(n.id!, !n.activo), 'Estado actualizado.')}
                      />
                      <BotonIcono
                        icono={Trash2}
                        etiqueta="Eliminar"
                        onClick={() => ejecutar(() => catalogosApi.eliminarCriticidad(n.id!), 'Nivel de criticidad eliminado.')}
                      />
                    </div>
                  </td>
                </tr>
              ))}
              {niveles.datos?.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-ink-soft">
                    No hay niveles de criticidad registrados.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {editando && (
        <Modal
          abierto
          onCerrar={() => setEditando(null)}
          titulo={editando === 'nuevo' ? 'Nuevo nivel de criticidad' : `Editar ${editando.nombre}`}
          pie={
            <>
              <Boton onClick={() => setEditando(null)}>Cancelar</Boton>
              <Boton type="submit" form="form-criticidad" variante="primario" icono={Save} cargando={guardando}>
                Guardar
              </Boton>
            </>
          }
        >
          <form id="form-criticidad" onSubmit={guardar} className="grid gap-4 sm:grid-cols-2">
            {!tieneErroresDeCampo(error) && (
              <div className="sm:col-span-2">
                <MensajeError error={error} />
              </div>
            )}
            <Campo etiqueta="Nombre" obligatorio error={errorDeCampo(error, 'nombre')}>
              <Entrada value={f.nombre} onChange={(e) => setF((p) => ({ ...p, nombre: e.target.value }))} required />
            </Campo>
            {numero('prioridad', 'Prioridad', 0, 'Única; 1 es la más crítica')}
            {numero('frecuenciaRevisionDias', 'Frecuencia de revisión (días)', 1)}
            {numero('frecuenciaMantenimientoDias', 'Frecuencia de mantenimiento (días)', 1)}
            {numero('plazoAutorizacionHoras', 'Plazo de autorización (horas)', 1, 'Anticipación de la evaluación previa')}
            {numero('plazoValidacionHoras', 'Plazo de validación (horas)', 1)}
          </form>
        </Modal>
      )}
    </div>
  )
}
