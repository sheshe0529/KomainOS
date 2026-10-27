import { describe, expect, it } from 'vitest'
import type { ParametroPeticion } from '@/api/types'
import { aPeticion, editables, problemaLocal, usoEnScript } from './parametrosScript'

const p = (cambios: Partial<ParametroPeticion>): ParametroPeticion => ({
  nombre: 'DIAS',
  tipo: 'INTEGER',
  obligatorio: true,
  valorPorDefecto: '7',
  ...cambios,
})

describe('problemaLocal: las mismas reglas que PreparadorScript del backend (RF25)', () => {
  it('acepta un nombre de variable válido con un valor de su tipo', () => {
    expect(problemaLocal(p({}), [p({})])).toBeUndefined()
    expect(problemaLocal(p({ tipo: 'DECIMAL', valorPorDefecto: '0,5' }), [])).toBeUndefined()
    expect(problemaLocal(p({ tipo: 'BOOLEAN', valorPorDefecto: 'TRUE' }), [])).toBeUndefined()
  })

  it('rechaza nombres que no sirven como variable en Bash y PowerShell', () => {
    expect(problemaLocal(p({ nombre: '1DIAS' }), [])).toContain('no empieza con número')
    expect(problemaLocal(p({ nombre: 'DÍAS' }), [])).toContain('sin tilde')
    expect(problemaLocal(p({ nombre: '' }), [])).toBe('Escriba el nombre')
  })

  it('detecta nombres repetidos sin distinguir mayúsculas', () => {
    expect(problemaLocal(p({}), [p({}), p({ nombre: 'dias' })])).toBe('Nombre repetido')
  })

  it('exige valor al obligatorio: la MOP se ejecuta sin intervención', () => {
    expect(problemaLocal(p({ valorPorDefecto: '' }), [])).toContain('necesita valor')
    expect(problemaLocal(p({ valorPorDefecto: '', obligatorio: false }), [])).toBeUndefined()
  })

  it('valida el valor según el tipo', () => {
    expect(problemaLocal(p({ valorPorDefecto: '7.5' }), [])).toBe('Debe ser un número entero')
    expect(problemaLocal(p({ tipo: 'BOOLEAN', valorPorDefecto: 'si' }), [])).toBe('Debe ser true o false')
  })
})

describe('conversión de la tabla de parámetros', () => {
  it('quita la clave de edición y los espacios, y omite el valor vacío', () => {
    const filas = editables([p({ nombre: ' RUTA ', tipo: 'STRING', valorPorDefecto: '  ', obligatorio: false })])
    expect(filas[0].clave).toBeTruthy()
    expect(aPeticion(filas)).toEqual([{ nombre: 'RUTA', tipo: 'STRING', obligatorio: false, valorPorDefecto: undefined }])
  })

  it('muestra cómo leer el parámetro en cada intérprete', () => {
    expect(usoEnScript('RUTA', 'bash')).toBe('"$RUTA"')
    expect(usoEnScript('RUTA', 'powershell')).toBe('$RUTA')
  })
})
