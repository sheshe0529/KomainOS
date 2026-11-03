import { Selector } from './Selector'

interface SelectorHoraProps {
  /** «HH:mm» */
  value: string
  onChange: (valor: string) => void
  disabled?: boolean
  'aria-label'?: string
}

const HORAS = Array.from({ length: 24 }, (_, h) => String(h).padStart(2, '0'))
const MINUTOS = Array.from({ length: 12 }, (_, m) => String(m * 5).padStart(2, '0'))

/**
 * Hora y minutos con los mismos desplegables del panel. Los minutos van de cinco en cinco; si el valor guardado tiene
 * otro minuto (por ejemplo 23:59), se conserva como opción
 */
export function SelectorHora({ value, onChange, disabled, 'aria-label': ariaLabel = 'Hora' }: SelectorHoraProps) {
  const [horas = '00', minutos = '00'] = value.split(':')
  const opcionesMinutos = MINUTOS.includes(minutos) ? MINUTOS : [...MINUTOS, minutos].sort()

  return (
    <div className="flex items-center gap-1" role="group" aria-label={ariaLabel}>
      <Selector
        aria-label={`${ariaLabel}: horas`}
        value={horas}
        disabled={disabled}
        onChange={(e) => onChange(`${e.target.value}:${minutos}`)}
      >
        {HORAS.map((h) => (
          <option key={h} value={h}>
            {h}
          </option>
        ))}
      </Selector>
      <span className="text-ink-faint" aria-hidden="true">
        :
      </span>
      <Selector
        aria-label={`${ariaLabel}: minutos`}
        value={minutos}
        disabled={disabled}
        onChange={(e) => onChange(`${horas}:${e.target.value}`)}
      >
        {opcionesMinutos.map((m) => (
          <option key={m} value={m}>
            {m}
          </option>
        ))}
      </Selector>
    </div>
  )
}
