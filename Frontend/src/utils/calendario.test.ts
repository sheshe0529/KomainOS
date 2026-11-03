import { describe, expect, it } from 'vitest'
import { claveDia } from './formato'
import { fueraDeRango, leerFecha, semanasDelMes, sumarMeses, valorFechaHora } from './calendario'

describe('Calendario de los selectores de fecha', () => {
  it('lee el día en hora local: «2026-10-31» es el 31 y no el 30 por la zona horaria', () => {
    const fecha = leerFecha('2026-10-31')!
    expect([fecha.getFullYear(), fecha.getMonth(), fecha.getDate(), fecha.getHours()]).toEqual([2026, 9, 31, 0])
  })

  it('con hora devuelve el mismo formato que el campo nativo que reemplaza', () => {
    expect(valorFechaHora(leerFecha('2026-11-02T07:05')!)).toBe('2026-11-02T07:05')
    expect(leerFecha('31/10/2026')).toBeUndefined()
  })

  it('arma seis semanas que empiezan en lunes y contienen todo el mes', () => {
    const semanas = semanasDelMes(2026, 9) // octubre de 2026 empieza en jueves
    expect(semanas).toHaveLength(6)
    expect(claveDia(semanas[0][0])).toBe('2026-09-28')
    expect(semanas[0][0].getDay()).toBe(1)
    expect(semanas.flat().some((d) => claveDia(d) === '2026-10-31')).toBe(true)
  })

  it('al cambiar de mes conserva el día o toma el último si no existe', () => {
    expect(claveDia(sumarMeses(leerFecha('2026-01-31')!, 1))).toBe('2026-02-28')
    expect(claveDia(sumarMeses(leerFecha('2026-10-15')!, -1))).toBe('2026-09-15')
  })

  it('los límites mínimo y máximo incluyen el día completo', () => {
    const dia = leerFecha('2026-10-31T23:30')!
    expect(fueraDeRango(dia, '2026-10-31', '2026-10-31')).toBe(false)
    expect(fueraDeRango(dia, '2026-11-01')).toBe(true)
    expect(fueraDeRango(dia, undefined, '2026-10-30')).toBe(true)
  })
})
