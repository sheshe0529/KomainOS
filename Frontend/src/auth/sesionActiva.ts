/** Lectura de las fechas del token (sin validarlo: eso lo hace el backend) para renovar la sesión a tiempo */

interface CargaToken {
  /** Emisión y vencimiento en segundos desde 1970 (JWT) */
  iat?: number
  exp?: number
}

function carga(token: string | null): CargaToken | undefined {
  if (!token) return undefined
  try {
    const parte = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(parte + '='.repeat((4 - (parte.length % 4)) % 4))) as CargaToken
  } catch {
    return undefined
  }
}

/** Milisegundos desde 1970 en que vence el token, si se puede leer */
export function venceEl(token: string | null): number | undefined {
  const exp = carga(token)?.exp
  return exp ? exp * 1000 : undefined
}

/** Milisegundos desde 1970 en que se emitió el token, si se puede leer */
export function emitidoEl(token: string | null): number | undefined {
  const iat = carga(token)?.iat
  return iat ? iat * 1000 : undefined
}
