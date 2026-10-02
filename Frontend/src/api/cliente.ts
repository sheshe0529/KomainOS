/** Concentra el token y el manejo de errores para que ninguna pantalla los resuelva por su cuenta */

import type { ErrorRespuesta } from './types'

// En desarrollo Vite reenvía /api al backend (vite.config.ts)
const BASE = import.meta.env.VITE_API_URL ?? '/api'
const CLAVE_TOKEN = 'komainos.token'

/** Se emite cuando el backend rechaza la sesión (token vencido o cuenta desactivada, RF02) */
export const EVENTO_SESION_EXPIRADA = 'komainos:sesion-expirada'

export class ErrorApi extends Error {
  readonly estado: number
  readonly codigo: string
  readonly errores?: { campo?: string; mensaje?: string }[]

  constructor(estado: number, codigo: string, mensaje: string, errores?: { campo?: string; mensaje?: string }[]) {
    super(mensaje)
    this.estado = estado
    this.codigo = codigo
    this.errores = errores
  }

  errorDe(campo: string): string | undefined {
    return this.errores?.find((e) => e.campo === campo)?.mensaje
  }
}

export function leerToken(): string | null {
  return localStorage.getItem(CLAVE_TOKEN)
}

export function guardarToken(token: string): void {
  localStorage.setItem(CLAVE_TOKEN, token)
}

export function borrarToken(): void {
  localStorage.removeItem(CLAVE_TOKEN)
}

type ValorParametro = string | number | boolean | null | undefined

/** Construye "?a=1&b=2" omitiendo los valores vacíos */
export function consulta(parametros: Record<string, ValorParametro | ValorParametro[]>): string {
  const busqueda = new URLSearchParams()
  for (const [clave, valor] of Object.entries(parametros)) {
    const valores = Array.isArray(valor) ? valor : [valor]
    for (const v of valores) {
      if (v !== undefined && v !== null && v !== '') busqueda.append(clave, String(v))
    }
  }
  const texto = busqueda.toString()
  return texto ? `?${texto}` : ''
}

/** Un FormData viaja sin Content-Type para que el navegador agregue el separador del multipart */
async function solicitar(ruta: string, opciones: RequestInit = {}): Promise<Response> {
  const token = leerToken()
  const esFormulario = opciones.body instanceof FormData

  let respuesta: Response
  try {
    respuesta = await fetch(`${BASE}${ruta}`, {
      ...opciones,
      headers: {
        ...(esFormulario ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...opciones.headers,
      },
    })
  } catch {
    throw new ErrorApi(0, 'SIN_CONEXION', 'No se pudo conectar con el servidor. Verifique que el backend esté en ejecución')
  }

  if (!respuesta.ok) {
    let detalle: ErrorRespuesta | null = null
    try {
      detalle = (await respuesta.json()) as ErrorRespuesta
    } catch {
      throw new ErrorApi(respuesta.status, 'ERROR_INTERNO', 'El servidor respondió con un error inesperado')
    }

    if (respuesta.status === 401 && token) {
      borrarToken()
      window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA))
    }

    throw new ErrorApi(
      respuesta.status,
      detalle.codigo ?? 'ERROR_INTERNO',
      detalle.mensaje ?? 'Ocurrió un error inesperado',
      detalle.errores ?? undefined,
    )
  }
  return respuesta
}

export async function api<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const respuesta = await solicitar(ruta, opciones)
  if (respuesta.status === 204) {
    return undefined as T
  }
  return (await respuesta.json()) as T
}

/** Nombre del archivo según Content-Disposition (admite filename* en UTF-8) */
function nombreDeArchivo(cabecera: string | null, porDefecto: string): string {
  if (!cabecera) return porDefecto
  const codificado = /filename\*=UTF-8''([^;]+)/i.exec(cabecera)
  if (codificado) return decodeURIComponent(codificado[1])
  const simple = /filename="?([^";]+)"?/i.exec(cabecera)
  return simple ? simple[1] : porDefecto
}

/** Con fetch y no con un enlace directo porque la petición necesita el token (RF13) */
export async function descargar(ruta: string, porDefecto = 'archivo'): Promise<string> {
  const respuesta = await solicitar(ruta)
  const nombre = nombreDeArchivo(respuesta.headers.get('Content-Disposition'), porDefecto)
  const url = URL.createObjectURL(await respuesta.blob())
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  // Se libera después de que el navegador tomó el archivo
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  return nombre
}

export const http = {
  get: <T>(ruta: string) => api<T>(ruta),
  post: <T>(ruta: string, cuerpo?: unknown) =>
    api<T>(ruta, { method: 'POST', body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) }),
  put: <T>(ruta: string, cuerpo: unknown) => api<T>(ruta, { method: 'PUT', body: JSON.stringify(cuerpo) }),
  delete: <T>(ruta: string) => api<T>(ruta, { method: 'DELETE' }),
  enviarArchivo: <T>(ruta: string, formulario: FormData) => api<T>(ruta, { method: 'POST', body: formulario }),
}
