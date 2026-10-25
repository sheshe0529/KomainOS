import { describe, expect, it, vi } from 'vitest'
import { consulta, todasLasPaginas } from './cliente'
import type { Pagina } from './dominio'

function pagina<T>(contenido: T[], numero: number, totalPaginas: number): Pagina<T> {
  return { contenido, pagina: numero, tamano: contenido.length, totalPaginas, totalElementos: totalPaginas * contenido.length }
}

describe('todasLasPaginas', () => {
  it('recorre todas las páginas: nada queda después de la primera', async () => {
    const obtener = vi.fn(async (n: number) => pagina([`usuario-${n}a`, `usuario-${n}b`], n, 3))

    const todos = await todasLasPaginas(obtener)

    expect(todos).toEqual(['usuario-0a', 'usuario-0b', 'usuario-1a', 'usuario-1b', 'usuario-2a', 'usuario-2b'])
    expect(obtener).toHaveBeenCalledTimes(3)
  })

  it('un listado vacío pide una sola página', async () => {
    const obtener = vi.fn(async () => pagina<string>([], 0, 0))

    expect(await todasLasPaginas(obtener)).toEqual([])
    expect(obtener).toHaveBeenCalledTimes(1)
  })

  it('se detiene en el tope aunque el backend informe más páginas', async () => {
    const obtener = vi.fn(async (n: number) => pagina([n], n, Number.MAX_SAFE_INTEGER))

    const todos = await todasLasPaginas(obtener)

    expect(todos).toHaveLength(100)
  })
})

describe('consulta', () => {
  it('omite los valores vacíos y repite los parámetros con varios valores', () => {
    expect(consulta({ texto: '', rol: 'RESPONSABLE', activo: false, page: 0, sort: ['nombreCompleto,asc', 'id,asc'] })).toBe(
      '?rol=RESPONSABLE&activo=false&page=0&sort=nombreCompleto%2Casc&sort=id%2Casc',
    )
    expect(consulta({ texto: undefined })).toBe('')
  })
})
