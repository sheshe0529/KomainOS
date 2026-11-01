import { describe, expect, it } from 'vitest'
import { etapasAlcanzadas, pasoDe } from './etapasOrden'

describe('Etapas del ciclo de una orden', () => {
  it('la incidencia ocupa el lugar de la validación en el recorrido', () => {
    expect(pasoDe('INCIDENCIA')).toBe('VALIDACION')
    expect(pasoDe('EJECUCION')).toBe('EJECUCION')
  })

  it('un cierre sin intervención no alcanza la autorización ni la ejecución', () => {
    const alcanzadas = etapasAlcanzadas('CIERRE', ['PROGRAMADA', 'EN_EVALUACION', 'CERRADA'])
    expect([...alcanzadas].sort()).toEqual(['CIERRE', 'EVALUACION', 'PLANIFICACION'])
  })

  it('un ciclo completo alcanza todas las etapas', () => {
    const alcanzadas = etapasAlcanzadas('CIERRE', [
      'PROGRAMADA',
      'EN_EVALUACION',
      'PENDIENTE_AUTORIZACION',
      'AUTORIZADA',
      'EN_COLA',
      'EN_EJECUCION',
      'PENDIENTE_VALIDACION',
      'CERRADA',
    ])
    expect(alcanzadas.size).toBe(6)
  })
})
