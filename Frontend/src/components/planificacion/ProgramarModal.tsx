import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { CalendarPlus, CalendarClock } from 'lucide-react'
import { planificacionApi } from '@/api/planificacion'
import type { OrdenDetalleRespuesta, PropuestaRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { aValorFechaHoraLocal, formatearFechaHora, formatearHora } from '@/utils/formato'

export interface ObjetivoProgramacion {
  idServidor?: number
  idGrupo?: number
  nombre: string
}

type Modo = 'primer-intervalo' | 'fecha'

interface ProgramarModalProps {
  abierto: boolean
  objetivo: ObjetivoProgramacion
  /** Si se indica, reprograma esa orden en vez de crear una nueva (RF30) */
  idOrden?: number
  onCerrar: () => void
  onGuardado: (orden: OrdenDetalleRespuesta) => void
}

export function ProgramarModal({ abierto, objetivo, idOrden, onCerrar, onGuardado }: ProgramarModalProps) {
  const reprogramando = idOrden !== undefined
  const [modo, setModo] = useState<Modo>('primer-intervalo')
  const [fecha, setFecha] = useState(() => {
    const manana = new Date()
    manana.setDate(manana.getDate() + 1)
    manana.setHours(1, 0, 0, 0)
    return aValorFechaHoraLocal(manana)
  })
  const [motivo, setMotivo] = useState('')
  const [propuesta, setPropuesta] = useState<PropuestaRespuesta>()
  const [errorPropuesta, setErrorPropuesta] = useState<unknown>()
  const [cargandoPropuesta, setCargandoPropuesta] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  // Al programar se muestra de antemano el intervalo del algoritmo, al reprogramar lo calcula el backend al confirmar
  useEffect(() => {
    if (reprogramando || modo !== 'primer-intervalo') return
    let vigente = true
    setCargandoPropuesta(true)
    setErrorPropuesta(undefined)
    planificacionApi
      .propuesta({ idServidor: objetivo.idServidor, idGrupo: objetivo.idGrupo })
      .then((p) => vigente && setPropuesta(p))
      .catch((e: unknown) => vigente && setErrorPropuesta(e))
      .finally(() => vigente && setCargandoPropuesta(false))
    return () => {
      vigente = false
    }
  }, [modo, reprogramando, objetivo.idServidor, objetivo.idGrupo])

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    const inicio = modo === 'fecha' ? new Date(fecha).toISOString() : undefined
    try {
      const orden = reprogramando
        ? await planificacionApi.reprogramar(idOrden, { inicio, motivo: motivo.trim() })
        : await planificacionApi.programar({
            idServidor: objetivo.idServidor,
            idGrupo: objetivo.idGrupo,
            inicio,
            motivo: motivo.trim() || undefined,
          })
      onGuardado(orden)
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={reprogramando ? `Reprogramar mantenimiento de ${objetivo.nombre}` : `Programar mantenimiento de ${objetivo.nombre}`}
      descripcion="Se verifica la ventana permisiva, la concurrencia máxima y las órdenes existentes sobre los mismos servidores."
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton
            type="submit"
            form="form-programar"
            variante="primario"
            icono={reprogramando ? CalendarClock : CalendarPlus}
            cargando={guardando}
            disabled={(reprogramando && !motivo.trim()) || (!reprogramando && modo === 'primer-intervalo' && !propuesta)}
          >
            {reprogramando ? 'Reprogramar' : 'Programar'}
          </Boton>
        </>
      }
    >
      <form id="form-programar" onSubmit={guardar} className="flex flex-col gap-4">
        <MensajeError error={error} />
        <fieldset className="grid gap-2 sm:grid-cols-2">
          <legend className="mb-1 text-sm font-medium text-ink">Fecha programada</legend>
          {(
            [
              ['primer-intervalo', 'Primer intervalo disponible', 'El sistema elige el primer horario libre dentro de la ventana.'],
              ['fecha', 'Fecha y hora específicas', 'Se verifica que el horario indicado sea factible.'],
            ] as [Modo, string, string][]
          ).map(([valor, titulo, ayuda]) => (
            <label
              key={valor}
              className="flex cursor-pointer items-start gap-2 rounded-lg border border-line p-3 text-sm has-[:checked]:border-accent has-[:checked]:bg-accent-soft"
            >
              <input type="radio" name="modo" checked={modo === valor} onChange={() => setModo(valor)} className="mt-0.5 accent-[var(--color-accent)]" />
              <span>
                <span className="font-medium text-ink">{titulo}</span>
                <span className="block text-xs text-ink-soft">{ayuda}</span>
              </span>
            </label>
          ))}
        </fieldset>

        {modo === 'primer-intervalo' && !reprogramando && (
          <div className="rounded-lg bg-panel-muted px-4 py-3 text-sm">
            {cargandoPropuesta ? (
              <Cargando texto="Buscando el primer intervalo disponible…" />
            ) : errorPropuesta ? (
              <MensajeError error={errorPropuesta} />
            ) : (
              propuesta && (
                <dl className="grid gap-1">
                  <div className="flex justify-between gap-4">
                    <dt className="text-ink-soft">Inicio propuesto</dt>
                    <dd className="font-medium text-ink">{formatearFechaHora(propuesta.inicio)}</dd>
                  </div>
                  <div className="flex justify-between gap-4">
                    <dt className="text-ink-soft">Fin previsto</dt>
                    <dd className="text-ink">{formatearFechaHora(propuesta.fin)}</dd>
                  </div>
                  <div className="flex justify-between gap-4">
                    <dt className="text-ink-soft">Evaluación previa</dt>
                    <dd className="text-ink">{formatearFechaHora(propuesta.fechaEvaluacion)}</dd>
                  </div>
                  <div className="flex justify-between gap-4">
                    <dt className="text-ink-soft">Ventana aplicada</dt>
                    <dd className="text-ink">
                      {formatearFechaHora(propuesta.inicioVentana)} – {formatearHora(propuesta.finVentana)}
                    </dd>
                  </div>
                </dl>
              )
            )}
          </div>
        )}

        {modo === 'fecha' && (
          <Campo etiqueta="Inicio" obligatorio ayuda="Hora local de este equipo.">
            <Entrada type="datetime-local" value={fecha} onChange={(e) => setFecha(e.target.value)} required />
          </Campo>
        )}

        <Campo etiqueta="Motivo" obligatorio={reprogramando} ayuda={reprogramando ? 'Queda registrado en el historial de la orden.' : undefined}>
          <AreaTexto value={motivo} maxLength={1000} onChange={(e) => setMotivo(e.target.value)} required={reprogramando} />
        </Campo>
      </form>
    </Modal>
  )
}
