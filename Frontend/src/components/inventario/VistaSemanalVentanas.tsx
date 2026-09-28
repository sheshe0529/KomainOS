import { Fragment, useState } from 'react'
import { DIAS_SEMANA, type DiaSemana } from '@/api/dominio'
import { ETIQUETA_DIA, textoVentana } from '@/utils/etiquetas'
import { formatearDuracion } from '@/utils/formato'

interface Ventana {
  diaInicio?: DiaSemana
  horaInicio?: string
  diaFin?: DiaSemana
  horaFin?: string
  duracionMinutos?: number
}

interface VistaSemanalVentanasProps {
  ventanas: Ventana[]
  /** Texto cuando no hay intervalos. */
  vacio?: string
  /** Alto de cada fila de hora, en píxeles. */
  altoHora?: number
}

const MINUTOS_DIA = 24 * 60
const MINUTOS_SEMANA = 7 * MINUTOS_DIA
const HORAS = Array.from({ length: 24 }, (_, h) => h)
const ABREVIATURA: Record<DiaSemana, string> = {
  LUNES: 'Lun',
  MARTES: 'Mar',
  MIERCOLES: 'Mié',
  JUEVES: 'Jue',
  VIERNES: 'Vie',
  SABADO: 'Sáb',
  DOMINGO: 'Dom',
}

interface Tramo {
  dia: number
  desde: number
  hasta: number
  intervalo: number
}

function minutos(hora?: string): number {
  const [h, m] = (hora ?? '00:00').split(':').map(Number)
  return (h || 0) * 60 + (m || 0)
}

function hhmm(minutosDelDia: number): string {
  const h = Math.floor(minutosDelDia / 60)
  return `${String(h).padStart(2, '0')}:${String(minutosDelDia % 60).padStart(2, '0')}`
}

/**
 * Duración del intervalo: la informada por el backend o, mientras se edita,
 * la calculada igual que él (el día de fin puede ser el siguiente o dar la
 * vuelta a la semana). Cero si el intervalo no es válido.
 */
function duracion(v: Ventana): number {
  if (v.duracionMinutos !== undefined) return v.duracionMinutos
  if (!v.diaInicio || !v.diaFin) return 0
  const dias = (DIAS_SEMANA.indexOf(v.diaFin) - DIAS_SEMANA.indexOf(v.diaInicio) + 7) % 7
  const total = dias * MINUTOS_DIA + minutos(v.horaFin) - minutos(v.horaInicio)
  return total > 0 ? total : 0
}

/** Reparte cada intervalo en tramos de un solo día. */
function tramos(ventanas: Ventana[]): Tramo[] {
  const resultado: Tramo[] = []
  ventanas.forEach((v, intervalo) => {
    if (!v.diaInicio) return
    let posicion = DIAS_SEMANA.indexOf(v.diaInicio) * MINUTOS_DIA + minutos(v.horaInicio)
    let restante = Math.min(duracion(v), MINUTOS_SEMANA)
    while (restante > 0) {
      const dia = Math.floor(posicion / MINUTOS_DIA) % 7
      const desde = posicion % MINUTOS_DIA
      const largo = Math.min(restante, MINUTOS_DIA - desde)
      resultado.push({ dia, desde, hasta: desde + largo, intervalo })
      posicion = (posicion + largo) % MINUTOS_SEMANA
      restante -= largo
    }
  })
  return resultado
}

/** Une rangos solapados o contiguos, para contar cada minuto una sola vez. */
function unir(rangos: [number, number][]): [number, number][] {
  const ordenados = [...rangos].sort((a, b) => a[0] - b[0])
  const unidos: [number, number][] = []
  for (const [desde, hasta] of ordenados) {
    const ultimo = unidos[unidos.length - 1]
    if (ultimo && desde <= ultimo[1]) ultimo[1] = Math.max(ultimo[1], hasta)
    else unidos.push([desde, hasta])
  }
  return unidos
}

/**
 * Semana de la ventana permisiva (RF18, HU14): una columna por día y una fila
 * por cada una de las 24 horas, con los minutos permitidos coloreados. Al pasar
 * el cursor por una hora se ve su detalle y se resalta el intervalo al que
 * pertenece; un intervalo que cruza la medianoche continúa en el día siguiente.
 */
export function VistaSemanalVentanas({ ventanas, vacio = 'Sin ventana permisiva definida.', altoHora = 18 }: VistaSemanalVentanasProps) {
  const [celda, setCelda] = useState<{ dia: number; hora: number } | null>(null)
  const partes = tramos(ventanas)
  const porDia = DIAS_SEMANA.map((_, dia) => unir(partes.filter((t) => t.dia === dia).map((t) => [t.desde, t.hasta])))
  const minutosPorDia = porDia.map((rangos) => rangos.reduce((suma, [d, h]) => suma + h - d, 0))
  const totalSemana = minutosPorDia.reduce((a, b) => a + b, 0)

  /** Tramos del día que caen dentro de la hora, recortados a ella. */
  const enHora = (dia: number, hora: number) =>
    partes
      .filter((t) => t.dia === dia && t.desde < (hora + 1) * 60 && t.hasta > hora * 60)
      .map((t) => ({ ...t, desde: Math.max(t.desde, hora * 60), hasta: Math.min(t.hasta, (hora + 1) * 60) }))

  const resaltados = celda ? new Set(enHora(celda.dia, celda.hora).map((t) => t.intervalo)) : new Set<number>()

  return (
    <div className="flex flex-col gap-2">
      <div
        role="img"
        aria-label={`Ventana permisiva semanal: ${ventanas.length ? ventanas.map(textoVentana).join('; ') : vacio}`}
        className="grid grid-cols-[2.75rem_repeat(7,minmax(0,1fr))] gap-x-1"
        onMouseLeave={() => setCelda(null)}
      >
        <span />
        {DIAS_SEMANA.map((d) => (
          <span key={d} className="truncate pb-1 text-center text-xs font-medium text-ink-soft" title={ETIQUETA_DIA[d]}>
            <span className="hidden sm:inline">{ETIQUETA_DIA[d]}</span>
            <span className="sm:hidden">{ABREVIATURA[d]}</span>
          </span>
        ))}

        {HORAS.map((hora) => (
          <Fragment key={hora}>
            <span
              className="-translate-y-1.5 pr-1.5 text-right font-mono text-[10px] tabular-nums text-ink-faint"
              style={{ height: altoHora }}
              aria-hidden="true"
            >
              {hhmm(hora * 60)}
            </span>
            {DIAS_SEMANA.map((dia, indiceDia) => {
              const dentro = enHora(indiceDia, hora)
              const activa = celda?.dia === indiceDia && celda.hora === hora
              return (
                <div
                  key={dia}
                  onMouseEnter={() => setCelda({ dia: indiceDia, hora })}
                  className={`relative border-t bg-panel-muted ${hora % 6 === 0 ? 'border-line' : 'border-line/50'} ${
                    hora === 0 ? 'rounded-t' : ''
                  } ${hora === 23 ? 'rounded-b' : ''} ${activa ? 'outline-1 outline-accent' : ''}`}
                  style={{ height: altoHora }}
                >
                  {dentro.map((t, i) => (
                    <span
                      key={i}
                      className={`absolute inset-x-0 bg-serie-1 transition-opacity ${
                        resaltados.size === 0 || resaltados.has(t.intervalo) ? 'opacity-90' : 'opacity-30'
                      }`}
                      style={{ top: `${((t.desde - hora * 60) / 60) * 100}%`, height: `${((t.hasta - t.desde) / 60) * 100}%` }}
                    />
                  ))}
                  {activa && <DetalleHora dia={indiceDia} hora={hora} dentro={dentro} ventanas={ventanas} />}
                </div>
              )
            })}
          </Fragment>
        ))}

        <span className="pt-1.5 pr-1.5 text-right text-[10px] text-ink-faint">Total</span>
        {minutosPorDia.map((m, i) => (
          <span key={i} className="pt-1.5 text-center font-mono text-[11px] tabular-nums text-ink-soft">
            {m ? formatearDuracion(m) : '—'}
          </span>
        ))}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 pl-12 text-xs text-ink-soft">
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm bg-serie-1" aria-hidden="true" />
          {ventanas.length ? 'Mantenimiento permitido · pase el cursor por una hora para ver el detalle' : vacio}
        </span>
        {ventanas.length > 0 && <span className="tabular-nums">{formatearDuracion(totalSemana)} por semana</span>}
      </div>
    </div>
  )
}

/** Globo con el detalle de una hora: cuánto de ella está permitido y a qué intervalo pertenece. */
function DetalleHora({ dia, hora, dentro, ventanas }: { dia: number; hora: number; dentro: Tramo[]; ventanas: Ventana[] }) {
  const rangos = unir(dentro.map((t) => [t.desde, t.hasta]))
  const permitidos = rangos.reduce((suma, [d, h]) => suma + h - d, 0)
  const intervalos = [...new Set(dentro.map((t) => t.intervalo))].map((i) => ventanas[i])
  // Cerca de los bordes el globo se abre hacia adentro para que no lo recorte el contenedor.
  const vertical = hora < 6 ? 'top-full mt-1.5' : 'bottom-full mb-1.5'
  const horizontal = dia < 2 ? 'left-0' : dia > 4 ? 'right-0' : 'left-1/2 -translate-x-1/2'

  return (
    <div
      className={`pointer-events-none absolute z-20 w-max max-w-64 rounded-lg border border-line bg-panel px-3 py-2 text-xs text-ink shadow-lg ${vertical} ${horizontal}`}
    >
      <p className="font-medium">
        {ETIQUETA_DIA[DIAS_SEMANA[dia]]} · {hhmm(hora * 60)} – {hora === 23 ? '24:00' : hhmm((hora + 1) * 60)}
      </p>
      <p className="mt-0.5 text-ink-soft">
        {permitidos === 0
          ? 'No permitido'
          : permitidos === 60
            ? 'Permitido toda la hora'
            : `Permitido ${rangos.map(([d, h]) => `de ${hhmm(d)} a ${h === MINUTOS_DIA ? '24:00' : hhmm(h)}`).join(' y ')}`}
      </p>
      {intervalos.map((v, i) => (
        <p key={i} className="mt-1 text-ink-soft">
          Intervalo: <span className="text-ink">{textoVentana(v)}</span> · {formatearDuracion(duracion(v))}
        </p>
      ))}
    </div>
  )
}
