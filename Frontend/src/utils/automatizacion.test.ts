import { describe, expect, it } from 'vitest'
import { detalleFallo, filaCsv, formatearSegundos } from './automatizacion'

describe('presentación de la ejecución', () => {
  it('no repite el tipo de fallo que ya se muestra como título (RF71)', () => {
    expect(detalleFallo('AUTENTICACION', 'Error de autenticación: el servidor rechazó la cuenta')).toBe(
      'El servidor rechazó la cuenta',
    )
    expect(detalleFallo('PERMISOS', 'Otro mensaje sin prefijo')).toBe('Otro mensaje sin prefijo')
    expect(detalleFallo(undefined, 'Sin tipo')).toBe('Sin tipo')
  })

  it('formatea duraciones de tareas en segundos', () => {
    expect(formatearSegundos(0)).toBe('0 s')
    expect(formatearSegundos(65)).toBe('1 min 05 s')
    expect(formatearSegundos(120)).toBe('2 min')
    expect(formatearSegundos(3725)).toBe('1 h 2 min')
    expect(formatearSegundos(undefined)).toBe('—')
  })

  it('arma filas CSV con punto y coma y comillas solo donde hacen falta', () => {
    expect(filaCsv(['a', 1, undefined, 'x;y', 'dijo "hola"'])).toBe('a;1;;"x;y";"dijo ""hola"""')
  })
})
