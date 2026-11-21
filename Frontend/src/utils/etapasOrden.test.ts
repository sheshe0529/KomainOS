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
      'EN_VALIDACION_TECNICA',
      'PENDIENTE_VALIDACION_FUNCIONAL',
      'CERRADA',
    ])
    expect(alcanzadas.size).toBe(6)
  })

  it('la validación técnica y la funcional son la etapa de validación (DEC-61)', () => {
    const alcanzadas = etapasAlcanzadas('VALIDACION', ['PROGRAMADA', 'EN_EVALUACION', 'AUTORIZADA', 'EN_COLA',
      'EN_EJECUCION', 'EN_VALIDACION_TECNICA'])
    expect(alcanzadas.has('VALIDACION')).toBe(true)
    expect(alcanzadas.has('CIERRE')).toBe(false)
  })
})
