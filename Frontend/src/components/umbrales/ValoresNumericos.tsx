import type { OperadorComparacion, TipoDato } from '@/api/dominio'
import { Campo, Entrada, Selector } from '@/components/ui/Campo'
import { simbolo } from '@/utils/umbrales'

interface ValoresNumericosProps {
  tipo?: TipoDato
  /** Si se indica, las etiquetas lo muestran: en una excepción el operador es el del umbral */
  operador?: OperadorComparacion
  advertencia: string
  critico: string
  onAdvertencia: (valor: string) => void
  onCritico: (valor: string) => void
  errorAdvertencia?: string
  errorCritico?: string
  autoFocus?: boolean
}

/**
 * Advertencia y crítico de una métrica numérica. Una booleana alerta con un solo valor: se elige cuál (verdadero o
 * falso) y con qué nivel, en vez de escribir 0 o 1 en dos casillas.
 */
export function ValoresNumericos(props: ValoresNumericosProps) {
  const { tipo, operador, advertencia, critico, onAdvertencia, onCritico, errorAdvertencia, errorCritico, autoFocus } = props
  const sufijo = operador ? ` (${simbolo(operador)})` : ''

  if (tipo === 'BOOLEAN') {
    const valor = advertencia !== '' ? advertencia : critico
    const nivel = advertencia !== '' ? 'ADVERTENCIA' : 'CRITICO'
    const asignar = (nuevoValor: string, nuevoNivel: string) => {
      onAdvertencia(nuevoNivel === 'ADVERTENCIA' ? nuevoValor : '')
      onCritico(nuevoNivel === 'CRITICO' ? nuevoValor : '')
    }
    return (
      <>
        <Campo etiqueta={`Valor que alerta${sufijo}`} obligatorio error={errorAdvertencia ?? errorCritico}>
          <Selector value={valor} onChange={(e) => asignar(e.target.value, nivel)} aria-label="Valor que alerta">
            <option value="">Elegir…</option>
            <option value="1">Verdadero (1)</option>
            <option value="0">Falso (0)</option>
          </Selector>
        </Campo>
        <Campo etiqueta="Nivel">
          <Selector value={nivel} onChange={(e) => asignar(valor, e.target.value)} aria-label="Nivel de la alerta">
            <option value="ADVERTENCIA">Advertencia</option>
            <option value="CRITICO">Crítico</option>
          </Selector>
        </Campo>
      </>
    )
  }

  // Un entero no admite decimales: el paso del campo lo indica antes de que el backend lo rechace
  const paso = tipo === 'INTEGER' ? '1' : 'any'
  return (
    <>
      <Campo etiqueta={`Advertencia${sufijo}`} error={errorAdvertencia}>
        <Entrada type="number" step={paso} value={advertencia} onChange={(e) => onAdvertencia(e.target.value)} placeholder="80" autoFocus={autoFocus} />
      </Campo>
      <Campo etiqueta={`Crítico${sufijo}`} error={errorCritico}>
        <Entrada type="number" step={paso} value={critico} onChange={(e) => onCritico(e.target.value)} placeholder="90" />
      </Campo>
    </>
  )
}
