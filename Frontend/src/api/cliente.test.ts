import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { autenticacionApi } from './autenticacion'
import { api, borrarToken, consulta, EVENTO_SESION_EXPIRADA, guardarToken, leerToken, todasLasPaginas } from './cliente'
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

describe('peticiones de sesión', () => {
  const emitir = vi.fn()

  beforeEach(() => {
    const datos = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (clave: string) => datos.get(clave) ?? null,
      setItem: (clave: string, valor: string) => datos.set(clave, valor),
      removeItem: (clave: string) => datos.delete(clave),
    })
    emitir.mockClear()
    vi.stubGlobal('window', { dispatchEvent: emitir })
    guardarToken('inicial')
  })

  afterEach(() => vi.unstubAllGlobals())

  function demorarRespuesta() {
    let responder!: (respuesta: Response) => void
    const pendiente = new Promise<Response>((resolver) => { responder = resolver })
    const fetch = vi.fn<typeof globalThis.fetch>().mockReturnValue(pendiente)
    vi.stubGlobal('fetch', fetch)
    return { fetch, responder }
  }

  it('conserva el token nuevo cuando una petición anterior responde 401', async () => {
    const { responder } = demorarRespuesta()
    const pendiente = api('/auth/yo')
    guardarToken('vigente')
    responder(Response.json({ codigo: 'NO_AUTENTICADO' }, { status: 401 }))

    await expect(pendiente).rejects.toMatchObject({ estado: 401 })
    expect(leerToken()).toBe('vigente')
    expect(emitir).not.toHaveBeenCalled()
  })

  it('cierra la sesión local cuando el 401 corresponde al token vigente', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ codigo: 'NO_AUTENTICADO' }, { status: 401 })))

    await expect(api('/auth/yo')).rejects.toMatchObject({ estado: 401 })
    expect(leerToken()).toBeNull()
    expect(emitir).toHaveBeenCalledWith(expect.objectContaining({ type: EVENTO_SESION_EXPIRADA }))
  })

  it('descarta la renovación que llega después de cerrar sesión', async () => {
    const { fetch, responder } = demorarRespuesta()
    const pendiente = autenticacionApi.renovar()
    borrarToken()
    responder(Response.json({ token: 'renovado' }))

    expect(new Headers(fetch.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer inicial')
    await expect(pendiente).resolves.toBe(false)
    expect(leerToken()).toBeNull()
  })

  it('guarda la renovación de la sesión que sigue abierta', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ token: 'renovado' })))

    await expect(autenticacionApi.renovar()).resolves.toBe(true)
    expect(leerToken()).toBe('renovado')
  })
})
