import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { Rol } from '@/api/dominio'
import { Cargando } from '@/components/ui/Cargando'
import { useSesion } from './sesion-context'

interface RutaProtegidaProps {
  children: ReactNode
  /** Si se omite, cualquier usuario autenticado */
  roles?: Rol[]
}

/** Comodidad de interfaz: la autorización efectiva la verifica el backend en cada petición */
export function RutaProtegida({ children, roles }: RutaProtegidaProps) {
  const { usuario, cargando } = useSesion()
  const location = useLocation()

  if (cargando) {
    return <Cargando texto="Verificando sesión…" pantallaCompleta />
  }
  if (!usuario) {
    return <Navigate to="/login" replace state={{ desde: location.pathname }} />
  }
  if (roles && (!usuario.rol || !roles.includes(usuario.rol))) {
    return <Navigate to="/" replace />
  }
  return <>{children}</>
}
