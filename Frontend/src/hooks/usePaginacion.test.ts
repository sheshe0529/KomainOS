import { describe, expect, it } from 'vitest'
import { paginarLocal, paginasVisibles } from './usePaginacion'

describe('Paginación de tablas', () => {
  it('muestra todas las páginas cuando son pocas', () => {
    expect(paginasVisibles(0, 5)).toEqual([0, 1, 2, 3, 4])
  })

  it('resume con «…» las páginas lejanas a la actual', () => {
    expect(paginasVisibles(10, 20)).toEqual([0, '…', 9, 10, 11, '…', 19])
  })

  it('cerca de un extremo muestra las primeras o las últimas seguidas', () => {
    expect(paginasVisibles(0, 20)).toEqual([0, 1, 2, 3, '…', 19])
    expect(paginasVisibles(19, 20)).toEqual([0, '…', 16, 17, 18, 19])
  })

  it('recorta una lista cargada como si viniera paginada del backend', () => {
    const elementos = Array.from({ length: 60 }, (_, i) => i)
    const segunda = paginarLocal(elementos, 1, 25)
    expect(segunda.contenido).toEqual(elementos.slice(25, 50))
    expect(segunda.totalPaginas).toBe(3)
    expect(segunda.totalElementos).toBe(60)
  })

  it('si la lista se achica, vuelve a la última página que existe', () => {
    const pagina = paginarLocal([1, 2, 3], 4, 25)
    expect(pagina.pagina).toBe(0)
    expect(pagina.contenido).toEqual([1, 2, 3])
  })
})
