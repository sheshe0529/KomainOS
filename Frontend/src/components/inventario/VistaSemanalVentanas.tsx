import { Fragment, useState } from 'react'
import { DIAS_SEMANA, type DiaSemana } from '@/api/dominio'
import { ETIQUETA_DIA, textoVentana } from '@/utils/etiquetas'
import { formatearDuracion } from '@/utils/formato'
import { MINUTOS_DIA, duracion, hhmm, tramos, tramosEn, unir, type Tramo, type Ventana } from '@/utils/ventanas'

interface VistaSemanalVentanasProps {
  ventanas: Ventana[]
  vacio?: string
  /** Alto de cada fila, en píxeles */
  altoFila?: number
  /** Horas que agrupa cada fila: 4 deja seis filas por día */
  horasPorFila?: number
}

const ABREVIATURA: Record<DiaSemana, string> = {
  LUNES: 'Lun',
  MARTES: 'Mar',
  MIERCOLES: 'Mié',
  JUEVES: 'Jue',
  VIERNES: 'Vie',
  SABADO: 'Sáb',
  DOMINGO: 'Dom',
}

/** Una columna por día y una fila por franja de horas, un intervalo que cruza la medianoche continúa en el día siguiente (RF18) */
export function VistaSemanalVentanas({
  ventanas,
  vacio = 'Sin ventana permisiva definida.',
  altoFila = 30,
  horasPorFila = 4,
}: VistaSemanalVentanasProps) {
  const [celda, setCelda] = useState<{ dia: number; fila: number } | null>(null)
  const largoFila = horasPorFila * 60
  const filas = Array.from({ length: Math.ceil(MINUTOS_DIA / largoFila) }, (_, f) => f)
  const partes = tramos(ventanas)
  const minutosPorDia = DIAS_SEMANA.map((_, dia) =>
    unir(partes.filter((t) => t.dia === dia).map((t) => [t.desde, t.hasta])).reduce((suma, [d, h]) => suma + h - d, 0),
  )
  const totalSemana = minutosPorDia.reduce((a, b) => a + b, 0)
  const enFila = (dia: number, fila: number) => tramosEn(partes, dia, fila * largoFila, Math.min((fila + 1) * largoFila, MINUTOS_DIA))
  const resaltados = celda ? new Set(enFila(celda.dia, celda.fila).map((t) => t.intervalo)) : new Set<number>()

  return (
    <div className="flex flex-col gap-2">
      <div
        role="img"
        aria-label={`Ventana permisiva semanal: ${ventanas.length ? ventanas.map(textoVentana).join(', ') : vacio}`}
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

        {filas.map((fila) => (
          <Fragment key={fila}>
            <span
              className="-translate-y-1.5 pr-1.5 text-right font-mono text-[10px] tabular-nums text-ink-faint"
              style={{ height: altoFila }}
              aria-hidden="true"
            >
              {hhmm(fila * largoFila)}
            </span>
            {DIAS_SEMANA.map((dia, indiceDia) => {
              const dentro = enFila(indiceDia, fila)
              const activa = celda?.dia === indiceDia && celda.fila === fila
              return (
                <div
                  key={dia}
                  onMouseEnter={() => setCelda({ dia: indiceDia, fila })}
                  className={`relative border-t border-line bg-panel-muted ${fila === 0 ? 'rounded-t' : ''} ${
                    fila === filas.length - 1 ? 'rounded-b' : ''
                  } ${activa ? 'outline-1 outline-accent' : ''}`}
                  style={{ height: altoFila }}
                >
                  {dentro.map((t, i) => (
                    <span
                      key={i}
                      className={`absolute inset-x-0 bg-serie-1 transition-opacity ${
                        resaltados.size === 0 || resaltados.has(t.intervalo) ? 'opacity-90' : 'opacity-30'
                      }`}
                      style={{
                        top: `${((t.desde - fila * largoFila) / largoFila) * 100}%`,
                        height: `${((t.hasta - t.desde) / largoFila) * 100}%`,
                      }}
                    />
                  ))}
                  {activa && (
                    <DetalleFranja dia={indiceDia} fila={fila} total={filas.length} largoFila={largoFila} dentro={dentro} ventanas={ventanas} />
                  )}
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
          {ventanas.length ? 'Mantenimiento permitido · pase el cursor por una franja para ver el detalle' : vacio}
        </span>
        {ventanas.length > 0 && <span className="tabular-nums">{formatearDuracion(totalSemana)} por semana</span>}
      </div>
    </div>
  )
}

interface DetalleFranjaProps {
  dia: number
  fila: number
  total: number
  largoFila: number
  dentro: Tramo[]
  ventanas: Ventana[]
}

function DetalleFranja({ dia, fila, total, largoFila, dentro, ventanas }: DetalleFranjaProps) {
  const desde = fila * largoFila
  const hasta = Math.min(desde + largoFila, MINUTOS_DIA)
  const rangos = unir(dentro.map((t) => [t.desde, t.hasta]))
  const permitidos = rangos.reduce((suma, [d, h]) => suma + h - d, 0)
  const intervalos = [...new Set(dentro.map((t) => t.intervalo))].map((i) => ventanas[i])
  // Cerca de los bordes el globo se abre hacia adentro para que no lo recorte el contenedor
  const vertical = fila < total / 2 ? 'top-full mt-1.5' : 'bottom-full mb-1.5'
  const horizontal = dia < 2 ? 'left-0' : dia > 4 ? 'right-0' : 'left-1/2 -translate-x-1/2'

  return (
    <div
      className={`pointer-events-none absolute z-20 w-max max-w-64 rounded-lg border border-line bg-panel px-3 py-2 text-xs text-ink shadow-lg ${vertical} ${horizontal}`}
    >
      <p className="font-medium">
        {ETIQUETA_DIA[DIAS_SEMANA[dia]]} · {hhmm(desde)} – {hhmm(hasta)}
      </p>
      <p className="mt-0.5 text-ink-soft">
        {permitidos === 0
          ? 'No permitido'
          : permitidos === hasta - desde
            ? 'Permitido toda la franja'
            : `Permitido ${rangos.map(([d, h]) => `de ${hhmm(d)} a ${hhmm(h)}`).join(' y ')}`}
      </p>
      {intervalos.map((v, i) => (
        <p key={i} className="mt-1 text-ink-soft">
          Intervalo: <span className="text-ink">{textoVentana(v)}</span> · {formatearDuracion(duracion(v))}
        </p>
      ))}
    </div>
  )
}
