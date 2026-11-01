import { describe, expect, it } from 'vitest'
import type { MantenimientoBitacora } from '@/api/types'
import { aniosDe, periodoDe, posicionEnPeriodo, resumenBitacora } from './bitacora'

const mantenimiento = (m: MantenimientoBitacora): MantenimientoBitacora => ({ servidores: [], ...m })

const historia: MantenimientoBitacora[] = [
  mantenimiento({ idOrden: 4, resultado: 'PROGRAMADO', inicioProgramado: '2026-12-01T07:00:00Z' }),
  mantenimiento({ idOrden: 3, resultado: 'CON_INCIDENCIA', inicioReal: '2026-10-01T07:00:00Z' }),
  mantenimiento({ idOrden: 2, resultado: 'SIN_INTERVENCION', inicioProgramado: '2026-09-01T07:00:00Z' }),
  mantenimiento({ idOrden: 1, resultado: 'EXITOSO', inicioReal: '2025-08-01T07:00:00Z' }),
  mantenimiento({ idOrden: 0, resultado: 'CANCELADO', inicioProgramado: '2025-07-01T07:00:00Z' }),
]

describe('Resumen de la bitácora de mantenimiento', () => {
  it('cuenta solo los ciclos terminados y calcula la tasa de éxito sobre ellos', () => {
    const r = resumenBitacora(historia, new Date('2026-10-31T00:00:00Z'))
    expect(r.realizados).toBe(3)
    expect(r.exitosos).toBe(1)
    expect(r.conProblemas).toBe(1)
    // Exitoso y sin intervención terminaron bien: 2 de 3
    expect(r.tasaExito).toBe(67)
  })

  it('el último es el que se ejecutó más recientemente y el próximo, el programado más cercano', () => {
    const r = resumenBitacora(historia, new Date('2026-10-31T00:00:00Z'))
    expect(r.ultimo?.idOrden).toBe(3)
    expect(r.proximo?.idOrden).toBe(4)
  })

  it('sin ciclos terminados no inventa una tasa', () => {
    expect(resumenBitacora([historia[0]]).tasaExito).toBeUndefined()
  })
})

describe('Línea de tiempo', () => {
  it('ubica cada fecha dentro del periodo, con margen en los extremos', () => {
    const periodo = periodoDe(historia)!
    const primero = posicionEnPeriodo('2025-07-01T07:00:00Z', periodo.desde, periodo.hasta)
    const ultimo = posicionEnPeriodo('2026-12-01T07:00:00Z', periodo.desde, periodo.hasta)
    expect(primero).toBeGreaterThan(0)
    expect(ultimo).toBeLessThan(100)
    expect(primero).toBeLessThan(ultimo)
  })

  it('con una sola fecha la deja al centro', () => {
    const periodo = periodoDe([historia[1]])!
    expect(posicionEnPeriodo(historia[1].inicioReal, periodo.desde, periodo.hasta)).toBeCloseTo(50)
  })

  it('lista los años del más reciente al más antiguo', () => {
    expect(aniosDe(historia)).toEqual([2026, 2025])
  })
})
