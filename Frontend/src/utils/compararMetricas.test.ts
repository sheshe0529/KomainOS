import { describe, it, expect } from 'vitest'
import { compararMetricas } from './compararMetricas'
describe('Reporte comparativo', () => {
  it('mantiene servidor e instancia separados y empareja antes y después', () => {
    const filas = compararMetricas([
      {
        idServidor: 1,
        servidor: 'srv-1',
        tipo: 'PREVIA',
        mediciones: [
          { codigo: 'disco', nombre: 'Disco', instancia: 'C', valor: 90 },
          { codigo: 'disco', instancia: 'D', valor: 20 },
        ],
      },
      {
        idServidor: 1,
        servidor: 'srv-1',
        tipo: 'POSTERIOR',
        mediciones: [{ codigo: 'disco', instancia: 'C', valor: 60, umbrales: [{ nivel: 'NORMAL' }] }],
      },
    ])
    expect(filas).toHaveLength(2)
    expect(filas[0]).toMatchObject({ antes: '90', despues: '60', nivel: 'Normal' })
    expect(filas[1].despues).toBe('—')
  })
  it('distingue advertencia de crítico y conserva listas de elementos', () => {
    const filas = compararMetricas([
      {
        idServidor: 1,
        tipo: 'POSTERIOR',
        mediciones: [
          {
            codigo: 'usuarios',
            elementos: ['root', 'admin'],
            umbrales: [{ nivel: 'ADVERTENCIA' }, { nivel: 'CRITICO' }],
          },
        ],
      },
    ])
    expect(filas[0]).toMatchObject({ antes: '—', despues: 'root, admin', nivel: 'Crítico' })
  })
})
