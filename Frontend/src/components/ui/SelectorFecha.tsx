import { useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent, MouseEvent } from 'react'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react'
import { claveDia, formatearMesAnio } from '@/utils/formato'
import {
  fueraDeRango,
  leerFecha,
  mismoDia,
  semanasDelMes,
  sumarDias,
  sumarMeses,
  valorFechaHora,
} from '@/utils/calendario'
import { usePopoverAnclado } from './popoverAnclado'
import { Selector } from './Selector'

interface SelectorFechaProps {
  /** «AAAA-MM-DD», o «AAAA-MM-DDTHH:mm» con hora; vacío sin fecha */
  value: string
  onChange: (valor: string) => void
  conHora?: boolean
  /** Primer y último día que se pueden elegir, «AAAA-MM-DD» */
  min?: string
  max?: string
  placeholder?: string
  required?: boolean
  disabled?: boolean
  className?: string
  'aria-label'?: string
}

const DIAS = ['L', 'M', 'X', 'J', 'V', 'S', 'D']
const NOMBRES_DIAS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo']
const HORAS = Array.from({ length: 24 }, (_, h) => String(h).padStart(2, '0'))
const MINUTOS = Array.from({ length: 12 }, (_, m) => String(m * 5).padStart(2, '0'))

const fechaCorta = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', year: 'numeric' })
const fechaLarga = new Intl.DateTimeFormat('es-PE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

function texto(fecha: Date, conHora: boolean): string {
  const dia = fechaCorta.format(fecha)
  if (!conHora) return dia
  return `${dia}, ${String(fecha.getHours()).padStart(2, '0')}:${String(fecha.getMinutes()).padStart(2, '0')}`
}

/**
 * Selector de fecha con el mismo aspecto que los demás desplegables del panel: el campo nativo no respeta el tema y
 * cambia según el navegador. Se maneja con el teclado como una grilla (flechas, Inicio, Fin, Re Pág y Av Pág)
 */
export function SelectorFecha({
  value,
  onChange,
  conHora = false,
  min,
  max,
  placeholder = 'Elegir fecha…',
  required,
  disabled,
  className = '',
  'aria-label': ariaLabel,
}: SelectorFechaProps) {
  const elegida = leerFecha(value)
  const idPanel = useId()
  const disparador = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)
  const grilla = useRef<HTMLDivElement>(null)
  const [enfocada, setEnfocada] = useState<Date>(() => elegida ?? new Date())
  const abierto = usePopoverAnclado(disparador, panel, {
    altoMaximo: 480,
    alAbrir: () => setEnfocada(elegida ?? new Date()),
  })

  // El día enfocado recibe el foco del teclado, también al cambiar de mes
  useEffect(() => {
    if (abierto) grilla.current?.querySelector<HTMLButtonElement>(`[data-dia="${claveDia(enfocada)}"]`)?.focus()
  }, [abierto, enfocada])

  const hoy = new Date()
  const semanas = semanasDelMes(enfocada.getFullYear(), enfocada.getMonth())

  function emitir(dia: Date, horas = elegida?.getHours() ?? 0, minutos = elegida?.getMinutes() ?? 0) {
    if (conHora) {
      onChange(valorFechaHora(new Date(dia.getFullYear(), dia.getMonth(), dia.getDate(), horas, minutos)))
    } else {
      onChange(claveDia(dia))
      panel.current?.hidePopover()
    }
  }

  function elegir(dia: Date) {
    if (fueraDeRango(dia, min, max)) return
    setEnfocada(dia)
    emitir(dia)
  }

  function alTeclear(e: KeyboardEvent<HTMLDivElement>) {
    const pasos: Record<string, () => Date> = {
      ArrowLeft: () => sumarDias(enfocada, -1),
      ArrowRight: () => sumarDias(enfocada, 1),
      ArrowUp: () => sumarDias(enfocada, -7),
      ArrowDown: () => sumarDias(enfocada, 7),
      Home: () => sumarDias(enfocada, -((enfocada.getDay() + 6) % 7)),
      End: () => sumarDias(enfocada, 6 - ((enfocada.getDay() + 6) % 7)),
      PageUp: () => sumarMeses(enfocada, -1),
      PageDown: () => sumarMeses(enfocada, 1),
    }
    const siguiente = pasos[e.key]
    if (!siguiente) return
    e.preventDefault()
    setEnfocada(siguiente())
  }

  /** Dentro de un <label> (Campo), un clic en el panel activaría el disparador y lo cerraría */
  function alHacerClic(e: MouseEvent<HTMLDivElement>) {
    if (!(e.target as HTMLElement).closest('button, [role="option"]')) e.preventDefault()
  }

  const minutosOpciones = elegida && !MINUTOS.includes(String(elegida.getMinutes()).padStart(2, '0'))
    ? [...MINUTOS, String(elegida.getMinutes()).padStart(2, '0')].sort()
    : MINUTOS

  return (
    <div className={`relative w-full ${className}`}>
      <button
        ref={disparador}
        type="button"
        disabled={disabled}
        popoverTarget={idPanel}
        aria-haspopup="dialog"
        aria-expanded={abierto}
        aria-controls={idPanel}
        aria-label={ariaLabel}
        className={`flex w-full items-center justify-between gap-2 rounded-lg border bg-panel px-3 py-2 text-left text-sm transition-colors hover:border-ink-faint focus:border-accent focus:outline-none disabled:cursor-not-allowed disabled:bg-panel-muted disabled:text-ink-soft ${
          abierto ? 'border-accent' : 'border-line'
        } ${elegida ? 'text-ink' : 'text-ink-faint'}`}
      >
        <span className="flex min-w-0 items-center gap-2">
          <CalendarDays className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
          <span className="truncate">{elegida ? texto(elegida, conHora) : placeholder}</span>
        </span>
        <ChevronDown
          className={`h-4 w-4 shrink-0 text-ink-faint transition-transform ${abierto ? 'rotate-180' : ''}`}
          aria-hidden="true"
        />
      </button>

      {/* Conserva la validación nativa de campo obligatorio del formulario */}
      {required && (
        <input
          tabIndex={-1}
          aria-hidden="true"
          required
          value={value}
          onChange={() => {}}
          className="pointer-events-none absolute inset-x-0 bottom-0 h-px w-full opacity-0"
        />
      )}

      <div
        ref={panel}
        id={idPanel}
        popover="auto"
        role="dialog"
        aria-label={ariaLabel ? `Calendario: ${ariaLabel}` : 'Calendario'}
        onClick={alHacerClic}
        className="m-0 w-72 overflow-y-auto rounded-lg border border-line bg-panel p-3 text-sm text-ink shadow-lg outline-none"
      >
        <div className="mb-2 flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={() => setEnfocada(sumarMeses(enfocada, -1))}
            aria-label="Mes anterior"
            className="rounded-md p-1.5 text-ink-soft hover:bg-panel-muted hover:text-ink"
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </button>
          <span className="font-medium" aria-live="polite">
            {formatearMesAnio(enfocada)}
          </span>
          <button
            type="button"
            onClick={() => setEnfocada(sumarMeses(enfocada, 1))}
            aria-label="Mes siguiente"
            className="rounded-md p-1.5 text-ink-soft hover:bg-panel-muted hover:text-ink"
          >
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <div ref={grilla} role="grid" aria-label={formatearMesAnio(enfocada)} onKeyDown={alTeclear} className="flex flex-col gap-0.5">
          <div role="row" className="grid grid-cols-7 text-center text-xs text-ink-faint">
            {DIAS.map((d, i) => (
              <span key={d} role="columnheader" aria-label={NOMBRES_DIAS[i]} className="py-1">
                {d}
              </span>
            ))}
          </div>
          {semanas.map((semana) => (
            <div key={claveDia(semana[0])} role="row" className="grid grid-cols-7 gap-0.5">
              {semana.map((dia) => {
                const delMes = dia.getMonth() === enfocada.getMonth()
                const seleccionado = !!elegida && mismoDia(dia, elegida)
                const esHoy = mismoDia(dia, hoy)
                const bloqueado = fueraDeRango(dia, min, max)
                return (
                  <button
                    key={claveDia(dia)}
                    type="button"
                    role="gridcell"
                    data-dia={claveDia(dia)}
                    tabIndex={mismoDia(dia, enfocada) ? 0 : -1}
                    aria-selected={seleccionado}
                    aria-disabled={bloqueado || undefined}
                    aria-label={fechaLarga.format(dia)}
                    onClick={() => elegir(dia)}
                    className={`h-8 rounded-md text-sm tabular-nums transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                      seleccionado
                        ? 'bg-accent font-medium text-accent-ink'
                        : bloqueado
                          ? 'cursor-not-allowed text-ink-faint opacity-40'
                          : `hover:bg-panel-muted ${delMes ? 'text-ink' : 'text-ink-faint'}`
                    } ${esHoy && !seleccionado ? 'font-semibold underline decoration-accent decoration-2 underline-offset-4' : ''}`}
                  >
                    {dia.getDate()}
                  </button>
                )
              })}
            </div>
          ))}
        </div>

        {conHora && (
          <div className="mt-3 flex items-center gap-2 border-t border-line pt-3">
            <span className="text-xs text-ink-soft">Hora</span>
            <Selector
              aria-label="Hora"
              className="!w-20"
              value={elegida ? String(elegida.getHours()).padStart(2, '0') : ''}
              disabled={!elegida}
              onChange={(e) => elegida && emitir(elegida, Number(e.target.value), elegida.getMinutes())}
            >
              {!elegida && <option value="">--</option>}
              {HORAS.map((h) => (
                <option key={h} value={h}>
                  {h}
                </option>
              ))}
            </Selector>
            <span className="text-ink-faint">:</span>
            <Selector
              aria-label="Minutos"
              className="!w-20"
              value={elegida ? String(elegida.getMinutes()).padStart(2, '0') : ''}
              disabled={!elegida}
              onChange={(e) => elegida && emitir(elegida, elegida.getHours(), Number(e.target.value))}
            >
              {!elegida && <option value="">--</option>}
              {minutosOpciones.map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </Selector>
          </div>
        )}

        <div className="mt-3 flex items-center justify-between gap-2 border-t border-line pt-2">
          <button
            type="button"
            disabled={fueraDeRango(hoy, min, max)}
            onClick={() => {
              setEnfocada(hoy)
              if (conHora) {
                // Con hora, «Ahora» toma también la hora actual redondeada a los próximos 5 minutos
                const ahora = new Date(Math.ceil(hoy.getTime() / 300_000) * 300_000)
                onChange(valorFechaHora(ahora))
              } else {
                elegir(hoy)
              }
            }}
            className="rounded-md px-2 py-1 text-xs font-medium text-accent hover:bg-panel-muted disabled:opacity-40"
          >
            {conHora ? 'Ahora' : 'Hoy'}
          </button>
          <div className="flex gap-1">
            {!required && value && (
              <button
                type="button"
                onClick={() => {
                  onChange('')
                  panel.current?.hidePopover()
                }}
                className="rounded-md px-2 py-1 text-xs text-ink-soft hover:bg-panel-muted hover:text-ink"
              >
                Borrar
              </button>
            )}
            {conHora && (
              <button
                type="button"
                onClick={() => panel.current?.hidePopover()}
                className="rounded-md bg-accent px-2.5 py-1 text-xs font-medium text-accent-ink hover:opacity-90"
              >
                Listo
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
