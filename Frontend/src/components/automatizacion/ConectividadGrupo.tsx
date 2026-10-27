import { useSesion } from '@/auth/sesion-context'
import { Tarjeta } from '@/components/common/Tarjeta'
import { PruebaAcceso } from './PruebaAcceso'

/** CP021: antes de un mantenimiento grupal, comprueba el acceso de la cuenta en cada integrante */
export function ConectividadGrupo({ idGrupo }: { idGrupo: number }) {
  const { tieneRol } = useSesion()
  if (!tieneRol('ADMINISTRADOR')) return null

  return (
    <Tarjeta titulo="Acceso de la cuenta de servicio">
      <p className="mb-3 text-sm text-ink-soft">
        Comprueba en cada integrante la conexión, la autenticación y los permisos de administrador. Puede probar una
        cuenta candidata antes de asignarla al grupo.
      </p>
      <PruebaAcceso idGrupo={idGrupo} />
    </Tarjeta>
  )
}
