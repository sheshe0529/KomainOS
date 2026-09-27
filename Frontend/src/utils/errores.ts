import { ErrorApi } from '@/api/cliente'

/** Texto legible de cualquier error (los del backend ya vienen en español, RNF05). */
export function textoDeError(error: unknown): string {
  if (error instanceof ErrorApi) return error.message
  if (error instanceof Error) return error.message
  return 'Ocurrió un error inesperado'
}

/**
 * Verdadero si el error trae mensajes por campo (400 VALIDACION): esos se
 * muestran junto a cada campo y no hace falta repetirlos arriba del formulario.
 */
export function tieneErroresDeCampo(error: unknown): boolean {
  return error instanceof ErrorApi && (error.errores?.length ?? 0) > 0
}

/** Mensaje de validación de un campo, si el backend lo devolvió en "errores". */
export function errorDeCampo(error: unknown, campo: string): string | undefined {
  return error instanceof ErrorApi ? error.errorDe(campo) : undefined
}
