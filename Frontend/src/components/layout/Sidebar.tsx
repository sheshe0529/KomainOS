import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { ChevronDown, PanelLeftClose, PanelLeftOpen, X } from 'lucide-react'
import { isNavGroup, navegacionPara, type NavGroup, type NavItem, type NavLeaf } from '@/config/navigation'
import { useSesion } from '@/auth/sesion-context'
import { Logo } from '@/components/common/Logo'

interface SidebarProps {
  collapsed: boolean
  onToggleCollapsed: () => void
  mobileOpen: boolean
  onCloseMobile: () => void
}

function coincide(pathname: string, path: string): boolean {
  return pathname === path || pathname.startsWith(`${path}/`)
}

function groupsContainingPath(items: NavItem[], pathname: string): string[] {
  return items
    .filter((item): item is NavGroup => isNavGroup(item) && item.children.some((child) => coincide(pathname, child.path)))
    .map((item) => item.id)
}

export function Sidebar({ collapsed, onToggleCollapsed, mobileOpen, onCloseMobile }: SidebarProps) {
  const location = useLocation()
  const { usuario } = useSesion()
  const navigation = navegacionPara(usuario?.rol)
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(
    // Todos los grupos inician desplegados, como en las pantallas preliminares
    () => new Set(navigation.filter(isNavGroup).map((g) => g.id)),
  )
  const [trackedPathname, setTrackedPathname] = useState(location.pathname)

  // Despliega el grupo de la ruta activa durante el render y no en un efecto, para no mostrar un frame colapsado
  if (location.pathname !== trackedPathname) {
    setTrackedPathname(location.pathname)
    const containing = groupsContainingPath(navigation, location.pathname)
    if (containing.length > 0) {
      setExpandedGroups((prev) => new Set([...prev, ...containing]))
    }
  }

  function handleGroupClick(group: NavGroup) {
    if (collapsed) {
      onToggleCollapsed()
      setExpandedGroups((prev) => new Set(prev).add(group.id))
      return
    }
    setExpandedGroups((prev) => {
      const next = new Set(prev)
      if (next.has(group.id)) {
        next.delete(group.id)
      } else {
        next.add(group.id)
      }
      return next
    })
  }

  function leafClasses({ isActive }: { isActive: boolean }) {
    // El cian del logo marca la sección activa como detalle, el fondo y el color del texto ya la distinguen
    return `relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
      isActive
        ? 'bg-accent-soft text-accent before:absolute before:inset-y-1.5 before:left-0 before:w-[3px] before:rounded-full before:bg-detail'
        : 'text-ink-soft hover:bg-panel-muted hover:text-ink'
    }`
  }

  function renderLeaf(item: NavLeaf) {
    const Icon = item.icon
    return (
      <NavLink
        key={item.id}
        to={item.path}
        className={leafClasses}
        onClick={onCloseMobile}
        title={collapsed ? item.label : undefined}
      >
        <Icon className="h-[18px] w-[18px] shrink-0" aria-hidden="true" />
        <span className={collapsed ? 'sr-only' : 'truncate'}>{item.label}</span>
      </NavLink>
    )
  }

  function renderGroup(item: NavGroup) {
    const Icon = item.icon
    const expanded = expandedGroups.has(item.id) && !collapsed
    const hasActiveChild = item.children.some((child) => coincide(location.pathname, child.path))

    return (
      <div key={item.id}>
        <button
          type="button"
          onClick={() => handleGroupClick(item)}
          aria-expanded={expanded}
          title={collapsed ? item.label : undefined}
          className={`flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
            hasActiveChild ? 'text-ink' : 'text-ink-soft hover:bg-panel-muted hover:text-ink'
          }`}
        >
          <Icon className="h-[18px] w-[18px] shrink-0" aria-hidden="true" />
          <span className={collapsed ? 'sr-only' : 'flex-1 truncate text-left'}>{item.label}</span>
          {!collapsed && (
            <ChevronDown
              className={`h-4 w-4 shrink-0 transition-transform ${expanded ? 'rotate-180' : ''}`}
              aria-hidden="true"
            />
          )}
        </button>
        {expanded && (
          <div className="ml-[22px] mt-1 flex flex-col gap-0.5 border-l border-line pl-3">
            {item.children.map((child) => renderLeaf(child))}
          </div>
        )}
      </div>
    )
  }

  return (
    <>
      {mobileOpen && (
        <button
          type="button"
          aria-label="Cerrar menú"
          onClick={onCloseMobile}
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-50 flex w-[260px] flex-col border-r border-line bg-panel transition-transform duration-200 ease-in-out lg:sticky lg:top-0 lg:h-dvh lg:translate-x-0 lg:transition-[width] ${
          collapsed ? 'lg:w-[76px]' : 'lg:w-[260px]'
        } ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}`}
      >
        <div
          className={`flex h-16 shrink-0 items-center justify-between gap-2 border-b border-line px-4 ${
            collapsed ? 'lg:justify-center lg:px-0' : ''
          }`}
        >
          {/* Con el menú colapsado no caben logo y botón: en escritorio el logo pasa a ser el botón de expandir */}
          <div className={`flex min-w-0 items-center gap-2 ${collapsed ? 'lg:hidden' : ''}`}>
            <Logo decorativo className="h-9 w-9 shrink-0" />
            <span className="truncate font-semibold text-ink">KomainOS</span>
          </div>

          <button
            type="button"
            onClick={onCloseMobile}
            aria-label="Cerrar menú"
            className="text-ink-soft hover:text-ink lg:hidden"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
          {collapsed ? (
            <button
              type="button"
              onClick={onToggleCollapsed}
              aria-label="Expandir menú"
              title="Expandir menú"
              className="group relative hidden h-9 w-9 items-center justify-center rounded-lg text-ink-soft hover:bg-panel-muted hover:text-ink lg:flex"
            >
              <Logo decorativo className="h-9 w-9 transition-opacity group-hover:opacity-0 group-focus-visible:opacity-0" />
              <PanelLeftOpen
                className="absolute h-[18px] w-[18px] opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100"
                aria-hidden="true"
              />
            </button>
          ) : (
            <button
              type="button"
              onClick={onToggleCollapsed}
              aria-label="Colapsar menú"
              title="Colapsar menú"
              className="hidden shrink-0 text-ink-soft hover:text-ink lg:block"
            >
              <PanelLeftClose className="h-5 w-5" aria-hidden="true" />
            </button>
          )}
        </div>

        <nav aria-label="Navegación principal" className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          {navigation.map((item) => (isNavGroup(item) ? renderGroup(item) : renderLeaf(item)))}
        </nav>
      </aside>
    </>
  )
}
