import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LogOut, Menu } from 'lucide-react'
import { findNavLabel } from '@/config/navigation'
import { useSesion } from '@/auth/sesion-context'
import { ETIQUETA_ROL } from '@/utils/etiquetas'

interface TopbarProps {
  onOpenMobileNav: () => void
}

function iniciales(nombre?: string): string {
  if (!nombre) return '?'
  const partes = nombre.trim().split(/\s+/)
  return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? (partes[partes.length - 1][0] ?? '') : '')).toUpperCase()
}

/** El tema se cambia solo desde Configuración › Preferencias */
export function Topbar({ onOpenMobileNav }: TopbarProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const { usuario, cerrarSesion } = useSesion()
  const title = findNavLabel(location.pathname)

  function salir() {
    cerrarSesion()
    navigate('/login', { replace: true })
  }

  return (
    <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-3 border-b border-line bg-panel/90 px-4 backdrop-blur sm:px-6 lg:px-8">
      <button
        type="button"
        onClick={onOpenMobileNav}
        aria-label="Abrir menú"
        className="text-ink-soft hover:text-ink lg:hidden"
      >
        <Menu className="h-5 w-5" aria-hidden="true" />
      </button>

      <h1 className="min-w-0 flex-1 truncate text-base font-semibold text-ink sm:text-lg">{title}</h1>

      {usuario && (
        <div className="flex items-center gap-2">
          <Link
            to="/mi-cuenta"
            title="Mi cuenta"
            className="flex items-center gap-2 rounded-lg px-2 py-1 transition-colors hover:bg-panel-muted"
          >
            <span className="hidden text-right sm:block">
              <span className="block text-sm font-medium leading-tight text-ink">{usuario.nombreCompleto}</span>
              <span className="block text-xs leading-tight text-ink-faint">{usuario.rol ? ETIQUETA_ROL[usuario.rol] : ''}</span>
            </span>
            <span
              className="flex h-9 w-9 items-center justify-center rounded-full bg-accent-soft text-xs font-semibold text-accent"
              aria-hidden="true"
            >
              {iniciales(usuario.nombreCompleto)}
            </span>
          </Link>
          <button
            type="button"
            onClick={salir}
            title="Cerrar sesión"
            aria-label="Cerrar sesión"
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-line bg-panel text-ink-soft transition-colors hover:bg-panel-muted hover:text-ink"
          >
            <LogOut className="h-[18px] w-[18px]" aria-hidden="true" />
          </button>
        </div>
      )}
    </header>
  )
}
