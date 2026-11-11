import type { FamiliaSistemaOperativo, MetodoElevacion, TipoAutenticacion } from '@/api/dominio'

export const METODOS_ELEVACION: MetodoElevacion[] = ['NINGUNO', 'SUDO', 'SU', 'RUNAS']

/**
 * Métodos que sirven para una familia y un mecanismo (DEC-57): sudo y su son de Linux, runas de Windows (que además
 * solo admite contraseña) y ninguno sirve en ambas. Sin familia (una cuenta de servicio), los de cualquiera de las dos.
 */
export function metodosAdmitidos(familia: FamiliaSistemaOperativo | undefined, tipo: TipoAutenticacion): MetodoElevacion[] {
  return METODOS_ELEVACION.filter((m) => {
    if (m === 'SUDO' || m === 'SU') return familia !== 'WINDOWS'
    if (m === 'RUNAS') return familia !== 'LINUX' && tipo === 'PASSWORD'
    return true
  })
}

/** su siempre pide la contraseña de root; sudo solo si se indica; ninguno y runas no llevan contraseña de elevación */
export function llevaContrasenaElevacion(metodo: MetodoElevacion, sudoPideContrasena: boolean): boolean {
  return metodo === 'SU' || (metodo === 'SUDO' && sudoPideContrasena)
}
