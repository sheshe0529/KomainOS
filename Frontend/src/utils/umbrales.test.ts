import { describe, expect, it } from 'vitest'
import { aNumero, normalizarElemento, operadoresPara, problemaDeValores } from './umbrales'

describe('problemaDeValores: las mismas reglas que el backend (HU12 CA2)', () => {
  it('acepta valores coherentes con el operador, o uno solo de los dos niveles', () => {
    expect(problemaDeValores('DECIMAL', 'MAYOR_IGUAL', 80, 90)).toBeNull()
    expect(problemaDeValores('DECIMAL', 'MENOR_IGUAL', 20, 10)).toBeNull()
    expect(problemaDeValores('DECIMAL', 'MAYOR', undefined, 95)).toBeNull()
    expect(problemaDeValores('INTEGER', 'IGUAL', 1, 2)).toBeNull()
  })

  it('exige al menos un valor', () => {
    expect(problemaDeValores('DECIMAL', 'MAYOR', undefined, undefined)).toContain('advertencia, el crítico o ambos')
  })

  it('rechaza un crítico del lado equivocado de la advertencia', () => {
    expect(problemaDeValores('DECIMAL', 'MAYOR_IGUAL', 90, 80)).toContain('mayor que la advertencia')
    expect(problemaDeValores('DECIMAL', 'MENOR', 10, 20)).toContain('menor que la advertencia')
  })

  it('CP203: advertencia y crítico iguales dejarían la advertencia sin efecto', () => {
    expect(problemaDeValores('DECIMAL', 'MAYOR', 80, 80)).toContain('no pueden ser iguales')
    expect(problemaDeValores('INTEGER', 'IGUAL', 3, 3)).toContain('no pueden ser iguales')
  })

  it('CP203: con ≠ admite un solo valor', () => {
    expect(problemaDeValores('DECIMAL', 'DISTINTO', 1, 2)).toContain('indique un solo valor')
    expect(problemaDeValores('DECIMAL', 'DISTINTO', undefined, 0)).toBeNull()
  })

  it('CP202: una métrica booleana se compara con = o ≠, con un solo valor 0 o 1', () => {
    expect(problemaDeValores('BOOLEAN', 'MAYOR', undefined, 1)).toContain('= o ≠')
    expect(problemaDeValores('BOOLEAN', 'IGUAL', 1, 0)).toContain('un solo valor')
    expect(problemaDeValores('BOOLEAN', 'IGUAL', undefined, 2)).toContain('debe ser 0 o 1')
    expect(problemaDeValores('BOOLEAN', 'IGUAL', undefined, 0)).toBeNull()
  })

  it('una métrica entera no admite decimales', () => {
    expect(problemaDeValores('INTEGER', 'MAYOR', 1.5, 3)).toContain('no puede tener decimales')
  })
})

describe('operadoresPara', () => {
  it('ofrece solo = y ≠ para una métrica booleana', () => {
    expect(operadoresPara('BOOLEAN').map((o) => o.valor)).toEqual(['IGUAL', 'DISTINTO'])
    expect(operadoresPara('DECIMAL')).toHaveLength(6)
  })
})

describe('aNumero', () => {
  it('trata el vacío como «no se evalúa» y acepta la coma decimal', () => {
    expect(aNumero('  ')).toBeUndefined()
    expect(aNumero('0,5')).toBe(0.5)
    expect(aNumero('80')).toBe(80)
    expect(aNumero('abc')).toBeNaN()
  })
})

describe('normalizarElemento: misma forma canónica que TipoDato del backend', () => {
  it('quita ceros sobrantes y recorta el texto', () => {
    expect(normalizarElemento('0443', 'INTEGER', false)).toBe('443')
    expect(normalizarElemento('1.50', 'DECIMAL', false)).toBe('1.5')
    expect(normalizarElemento(' SSHD ', 'STRING', true)).toBe('sshd')
    expect(normalizarElemento('SSHD', 'STRING', false)).toBe('SSHD')
  })

  it('rechaza lo que no corresponde al tipo', () => {
    expect(normalizarElemento('http', 'INTEGER', false)).toBeNull()
    expect(normalizarElemento('', 'STRING', false)).toBeNull()
    expect(normalizarElemento('x'.repeat(256), 'STRING', false)).toBeNull()
  })
})
