import { useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowLeft, Plus, Save, Trash2, TriangleAlert } from 'lucide-react'
import { DIAS_SEMANA, type DiaSemana } from '@/api/dominio'
import type { OrdenFueraVentanaRespuesta, VentanaPeticion, VentanaRespuesta } from '@/api/types'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { SelectorHora } from '@/components/ui/SelectorHora'
import { VistaSemanalVentanas } from './VistaSemanalVentanas'
import { ETIQUETA_DIA } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'

interface VentanasModalProps {
  abierto: boolean
  hostname: string
  actuales: VentanaRespuesta[]
  onCerrar: () => void
  /** Órdenes pendientes que quedarían fuera de la ventana propuesta, sin guardarla */
  onVerificar: (ventanas: VentanaPeticion[]) => Promise<OrdenFueraVentanaRespuesta[]>
  onGuardar: (ventanas: VentanaPeticion[], confirmarFueraVentana: boolean) => Promise<void>
}

/** Edición, advertencia por las órdenes que quedan fuera y confirmación expresa de guardarla sin reprogramarlas */
type Paso = 'edicion' | 'advertencia' | 'confirmacion'

function siguienteDia(dia: DiaSemana): DiaSemana {
  return DIAS_SEMANA[(DIAS_SEMANA.indexOf(dia) + 1) % 7]
}

function aFila(v: VentanaRespuesta): VentanaPeticion {
  return {
    diaInicio: v.diaInicio ?? 'SABADO',
    horaInicio: (v.horaInicio ?? '01:00').slice(0, 5),
    diaFin: v.diaFin ?? 'SABADO',
    horaFin: (v.horaFin ?? '05:00').slice(0, 5),
  }
}

function cantidadOrdenes(n: number) {
  return n === 1 ? '1 orden programada' : `${n} órdenes programadas`
}

/**
 * Un intervalo que cruza la medianoche se define eligiendo el día siguiente como día de fin. Cambiar la ventana no
 * reprograma las órdenes (DEC-60): si alguna queda fuera del nuevo horario, se advierte y se pide confirmarlo dos veces.
 */
export function VentanasModal({ abierto, hostname, actuales, onCerrar, onVerificar, onGuardar }: VentanasModalProps) {
  const [filas, setFilas] = useState<VentanaPeticion[]>(() => actuales.map(aFila))
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()
  const [paso, setPaso] = useState<Paso>('edicion')
  const [fuera, setFuera] = useState<OrdenFueraVentanaRespuesta[]>([])
  const [confirmaFuera, setConfirmaFuera] = useState(false)

  function cambiar(indice: number, cambios: Partial<VentanaPeticion>) {
    setFilas((prev) => prev.map((f, i) => (i === indice ? { ...f, ...cambios } : f)))
  }

  function mismoDiaInvalido(f: VentanaPeticion): boolean {
    return f.diaInicio === f.diaFin && f.horaFin <= f.horaInicio
  }

  function volverAEditar() {
    setPaso('edicion')
    setConfirmaFuera(false)
    setError(undefined)
  }

  async function ejecutar(accion: () => Promise<void>) {
    setGuardando(true)
    setError(undefined)
    try {
      await accion()
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  function guardar(evento: FormEvent) {
    evento.preventDefault()
    void ejecutar(async () => {
      const afectadas = await onVerificar(filas)
      if (afectadas.length === 0) {
        await onGuardar(filas, false)
        return
      }
      setFuera(afectadas)
      setConfirmaFuera(false)
      setPaso('advertencia')
    })
  }

  const hayInvalidas = filas.some(mismoDiaInvalido)

  const pie =
    paso === 'edicion' ? (
      <>
        <Boton onClick={onCerrar}>Cancelar</Boton>
        <Boton type="submit" form="form-ventanas" variante="primario" icono={Save} cargando={guardando} disabled={hayInvalidas}>
          Guardar ventana
        </Boton>
      </>
    ) : paso === 'advertencia' ? (
      <>
        <Boton icono={ArrowLeft} onClick={volverAEditar}>Volver a editar</Boton>
        <Boton variante="primario" onClick={() => setPaso('confirmacion')}>
          Continuar
        </Boton>
      </>
    ) : (
      <>
        <Boton icono={ArrowLeft} onClick={volverAEditar}>Volver a editar</Boton>
        <Boton
          variante="peligro"
          icono={Save}
          cargando={guardando}
          disabled={!confirmaFuera}
          onClick={() => void ejecutar(() => onGuardar(filas, true))}
        >
          Guardar sin reprogramar
        </Boton>
      </>
    )

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      ancho="lg"
      titulo={`Ventana permisiva de ${hostname}`}
      descripcion="Los cambios se aplican a los próximos mantenimientos. Las órdenes ya programadas no se reprograman."
      pie={pie}
    >
      {paso === 'edicion' ? (
        <form id="form-ventanas" onSubmit={guardar} className="flex flex-col gap-3">
          <MensajeError error={error} />
          <p className="text-xs text-ink-soft">
            Para un intervalo que cruza la medianoche elija el día siguiente como día de fin (por ejemplo, sábado 22:00 a domingo
            02:00). Para terminar a medianoche use 00:00 del día siguiente.
          </p>

          {filas.length === 0 && (
            <p className="rounded-lg border border-dashed border-line px-4 py-6 text-center text-sm text-ink-soft">
              Sin intervalos: el servidor no podrá planificarse hasta definir su ventana permisiva.
            </p>
          )}

          {filas.map((f, i) => (
            <div key={i} className="flex flex-col gap-1">
              <div className="grid grid-cols-[1fr_auto_1fr_auto_auto] items-center gap-2">
                <div className="grid grid-cols-2 gap-2">
                  <Selector aria-label="Día de inicio" value={f.diaInicio} onChange={(e) => cambiar(i, { diaInicio: e.target.value as DiaSemana })}>
                    {DIAS_SEMANA.map((d) => (
                      <option key={d} value={d}>
                        {ETIQUETA_DIA[d]}
                      </option>
                    ))}
                  </Selector>
                  <SelectorHora aria-label="Hora de inicio" value={f.horaInicio} onChange={(v) => cambiar(i, { horaInicio: v })} />
                </div>
                <span className="text-ink-faint">a</span>
                <div className="grid grid-cols-2 gap-2">
                  <Selector aria-label="Día de fin" value={f.diaFin} onChange={(e) => cambiar(i, { diaFin: e.target.value as DiaSemana })}>
                    {DIAS_SEMANA.map((d) => (
                      <option key={d} value={d}>
                        {ETIQUETA_DIA[d]}
                      </option>
                    ))}
                  </Selector>
                  <SelectorHora aria-label="Hora de fin" value={f.horaFin} onChange={(v) => cambiar(i, { horaFin: v })} />
                </div>
                <button
                  type="button"
                  className="whitespace-nowrap text-xs text-accent hover:underline"
                  onClick={() => cambiar(i, { diaFin: siguienteDia(f.diaInicio) })}
                  title="Terminar al día siguiente"
                >
                  +1 día
                </button>
                <BotonIcono icono={Trash2} etiqueta="Quitar intervalo" onClick={() => setFilas((prev) => prev.filter((_, j) => j !== i))} />
              </div>
              {mismoDiaInvalido(f) && (
                <span className="text-xs text-danger">En un intervalo del mismo día la hora de fin debe ser posterior a la de inicio.</span>
              )}
            </div>
          ))}

          <Boton
            icono={Plus}
            variante="fantasma"
            className="self-start"
            onClick={() => setFilas((prev) => [...prev, { diaInicio: 'SABADO', horaInicio: '01:00', diaFin: 'SABADO', horaFin: '05:00' }])}
          >
            Agregar intervalo
          </Boton>

          <div className="mt-1 border-t border-line pt-4">
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-faint">Vista de la semana</h4>
            <VistaSemanalVentanas ventanas={filas.filter((f) => !mismoDiaInvalido(f))} vacio="Sin intervalos." altoFila={22} />
          </div>
        </form>
      ) : (
        <div className="flex flex-col gap-3 text-sm">
          <MensajeError error={error} />
          <div className="flex flex-col gap-2 rounded-lg border border-warning/40 bg-warning-soft px-3 py-3" role="alert">
            <p className="flex items-center gap-2 font-medium text-ink">
              <TriangleAlert className="h-4 w-4 text-warning" aria-hidden />
              La nueva ventana deja {cantidadOrdenes(fuera.length)} fuera del horario establecido
            </p>
            <p className="text-ink-soft">
              No se reprogramarán: conservan su horario programado aunque quede fuera de la ventana permisiva de {hostname}.
            </p>
            <ul className="flex flex-col gap-1">
              {fuera.map((o) => (
                <li key={o.idOrden} className="flex flex-wrap items-baseline gap-x-2">
                  <span className="font-mono font-medium text-ink">{o.codigo}</span>
                  <span className="text-ink-soft">
                    {formatearFechaHora(o.inicio)} – {formatearFechaHora(o.fin)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
          {paso === 'confirmacion' && (
            <label className="flex items-start gap-2 text-ink">
              <input
                type="checkbox"
                checked={confirmaFuera}
                onChange={(e) => setConfirmaFuera(e.target.checked)}
                className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
              />
              Confirmo que guardo la ventana con {cantidadOrdenes(fuera.length)} fuera del horario establecido, sin
              reprogramarlas. La confirmación queda registrada en la auditoría.
            </label>
          )}
        </div>
      )}
    </Modal>
  )
}
