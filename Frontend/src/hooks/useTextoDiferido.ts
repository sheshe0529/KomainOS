import { useEffect, useState } from 'react'

/** Devuelve el texto cuando se deja de escribir, para no consultar al backend por cada tecla */
export function useTextoDiferido(texto: string, espera = 350): string {
  const [aplicado, setAplicado] = useState(texto)

  useEffect(() => {
    const t = window.setTimeout(() => setAplicado(texto), espera)
    return () => window.clearTimeout(t)
  }, [texto, espera])

  return aplicado
}
