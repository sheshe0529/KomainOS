import { useSesion } from '@/auth/sesion-context'
import { Tarjeta } from '@/components/common/Tarjeta'
import { PruebaAcceso } from './PruebaAcceso'

/** CP021: comprueba el acceso de la cuenta de evaluación o mantenimiento en cada integrante. */
export function ConectividadGrupo({ idGrupo }: { idGrupo: number }) {
  const { tieneRol } = useSesion()
  if (!tieneRol('ADMINISTRADOR')) return null

  return (
    <Tarjeta titulo="Acceso de las cuentas de servicio">
      <p className="mb-3 text-sm text-ink-soft">
        Comprueba en cada integrante la conexión y los permisos de la finalidad elegida. Puede probar una cuenta
        candidata antes de asignarla al grupo.
      </p>
      <PruebaAcceso idGrupo={idGrupo} />
    </Tarjeta>
  )
}
