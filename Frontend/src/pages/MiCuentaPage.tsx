import { Link, useNavigate } from 'react-router-dom'
import { LogOut, Palette } from 'lucide-react'
import { autenticacionApi } from '@/api/autenticacion'
import type { Rol } from '@/api/dominio'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { ETIQUETA_ROL } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'

/** Alcance de cada clase de usuario (R2.1, tabla 3). */
const ALCANCE_ROL: Record<Rol, string> = {
  ADMINISTRADOR:
    'Gestiona usuarios, inventario, catálogos, políticas, cronograma, incidencias y parámetros globales, e interviene en las autorizaciones que correspondan según la política configurada.',
  OPERADOR:
    'Supervisa las órdenes de mantenimiento, la cola de ejecución y las ejecuciones automatizadas, consultando su avance, resultados y estado operativo.',
  RESPONSABLE:
    'Autoriza y valida las órdenes de mantenimiento de sus servidores y gestiona sus ventanas permisivas. Solo ve la información de los servidores a su cargo.',
}

/** Detalle de la cuenta del usuario autenticado. */
export function MiCuentaPage() {
  const { cerrarSesion } = useSesion()
  const navigate = useNavigate()
  const perfil = useConsulta(() => autenticacionApi.perfil(), [])
  const p = perfil.datos

  if (perfil.cargando && !p) return <Cargando texto="Cargando su cuenta…" />

  return (
    <>
      <PageHeader
        title="Mi cuenta"
        description="Datos de la cuenta con la que inició sesión."
        actions={
          <>
            <Link
              to="/configuracion/preferencias"
              className="inline-flex items-center gap-2 rounded-lg border border-line bg-panel px-3.5 py-2 text-sm font-medium text-ink hover:bg-panel-muted"
            >
              <Palette className="h-4 w-4" aria-hidden="true" /> Preferencias
            </Link>
            <Boton
              icono={LogOut}
              onClick={() => {
                cerrarSesion()
                navigate('/login', { replace: true })
              }}
            >
              Cerrar sesión
            </Boton>
          </>
        }
      />
      <MensajeError error={perfil.error} onReintentar={perfil.recargar} />
      {p && (
        <div className="grid gap-6 lg:grid-cols-3">
          <Tarjeta titulo="Datos de la cuenta" className="lg:col-span-2">
            <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
              <Dato etiqueta="Nombre completo" valor={p.nombreCompleto} />
              <Dato etiqueta="Código de usuario" valor={p.codigo} mono />
              <Dato etiqueta="Rol" valor={p.rol ? ETIQUETA_ROL[p.rol] : undefined} />
              <Dato
                etiqueta="Estado"
                valor={<StatusPill tone={p.activo ? 'success' : 'neutral'} label={p.activo ? 'Activa' : 'Desactivada'} />}
              />
              <Dato etiqueta="Cuenta creada" valor={formatearFechaHora(p.fechaCreacion)} />
              <Dato etiqueta="Última actualización" valor={formatearFechaHora(p.fechaActualizacion)} />
              <Dato etiqueta="Vigencia de la sesión" valor={p.minutosSesion ? `${p.minutosSesion} minutos` : undefined} />
              {p.servidoresACargo !== undefined && (
                <Dato
                  etiqueta="Servidores a su cargo"
                  valor={
                    <Link to="/servidores" className="text-accent hover:underline">
                      {p.servidoresACargo} {p.servidoresACargo === 1 ? 'servidor' : 'servidores'}
                    </Link>
                  }
                />
              )}
            </dl>
          </Tarjeta>
          <Tarjeta titulo="Alcance del rol">
            <p className="text-sm text-ink-soft">{p.rol ? ALCANCE_ROL[p.rol] : ''}</p>
            <p className="mt-4 text-xs text-ink-faint">
              Los datos de la cuenta y el rol los administra un usuario con rol Administrador.
            </p>
          </Tarjeta>
        </div>
      )}
    </>
  )
}
