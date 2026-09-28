import { useState } from 'react'
import type { FormEvent } from 'react'
import { Plus, Save, Trash2 } from 'lucide-react'
import { DIAS_SEMANA, type DiaSemana } from '@/api/dominio'
import type { VentanaPeticion, VentanaRespuesta } from '@/api/types'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Entrada, Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { VistaSemanalVentanas } from './VistaSemanalVentanas'
import { ETIQUETA_DIA } from '@/utils/etiquetas'

interface VentanasModalProps {
  abierto: boolean
  hostname: string
  actuales: VentanaRespuesta[]
  onCerrar: () => void
  onGuardar: (ventanas: VentanaPeticion[]) => Promise<void>
}

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

/**
 * Edición de la ventana permisiva (RF18, RF19, HU14). Admite intervalos que
 * cruzan la medianoche eligiendo el día siguiente como día de fin; los
 * intervalos contiguos entre días se tratan como uno continuo.
 */
export function VentanasModal({ abierto, hostname, actuales, onCerrar, onGuardar }: VentanasModalProps) {
  const [filas, setFilas] = useState<VentanaPeticion[]>(() => actuales.map(aFila))
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  function cambiar(indice: number, cambios: Partial<VentanaPeticion>) {
    setFilas((prev) => prev.map((f, i) => (i === indice ? { ...f, ...cambios } : f)))
  }

  function mismoDiaInvalido(f: VentanaPeticion): boolean {
    return f.diaInicio === f.diaFin && f.horaFin <= f.horaInicio
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      await onGuardar(filas)
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  const hayInvalidas = filas.some(mismoDiaInvalido)

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      ancho="lg"
      titulo={`Ventana permisiva de ${hostname}`}
      descripcion="Los cambios se aplican a los próximos mantenimientos."
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-ventanas" variante="primario" icono={Save} cargando={guardando} disabled={hayInvalidas}>
            Guardar ventana
          </Boton>
        </>
      }
    >
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
                <Entrada type="time" aria-label="Hora de inicio" value={f.horaInicio} onChange={(e) => cambiar(i, { horaInicio: e.target.value })} required />
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
                <Entrada type="time" aria-label="Hora de fin" value={f.horaFin} onChange={(e) => cambiar(i, { horaFin: e.target.value })} required />
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
          <VistaSemanalVentanas ventanas={filas.filter((f) => !mismoDiaInvalido(f))} vacio="Sin intervalos." altoHora={14} />
        </div>
      </form>
    </Modal>
  )
}
