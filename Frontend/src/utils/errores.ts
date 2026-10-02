import { ErrorApi } from '@/api/cliente'

export function textoDeError(error: unknown): string {
  if (error instanceof ErrorApi) return error.message
  if (error instanceof Error) return error.message
  return 'Ocurrió un error inesperado'
}

/** Los errores por campo se muestran junto a cada campo y no se repiten arriba del formulario */
export function tieneErroresDeCampo(error: unknown): boolean {
  return error instanceof ErrorApi && (error.errores?.length ?? 0) > 0
}

export function errorDeCampo(error: unknown, campo: string): string | undefined {
  return error instanceof ErrorApi ? error.errorDe(campo) : undefined
}
