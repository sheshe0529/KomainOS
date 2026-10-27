import type { LucideIcon } from 'lucide-react'
import {
  BookOpen,
  Boxes,
  CalendarDays,
  ClipboardList,
  Gauge,
  KeyRound,
  Palette,
  Server,
  ServerCog,
  Settings,
  SlidersHorizontal,
  Users,
  Workflow,
  Terminal,
  ListChecks,
  ShieldAlert,
  ShieldCheck,
  Activity,
  Bell,
  History,
} from 'lucide-react'
import type { Rol } from '@/api/dominio'

/** Única fuente para el Sidebar, el título del Topbar y la ruta activa. Cada path necesita su Route en App.tsx */
export interface NavLeaf {
  id: string
  label: string
  icon: LucideIcon
  path: string
  roles?: Rol[]
  exact?: boolean
}

export interface NavGroup {
  id: string
  label: string
  icon: LucideIcon
  children: NavLeaf[]
}

export type NavItem = NavLeaf | NavGroup

export function isNavGroup(item: NavItem): item is NavGroup {
  return 'children' in item
}

export const navigation: NavItem[] = [
  { id: 'cronograma', label: 'Cronograma', icon: CalendarDays, path: '/cronograma' },
  {
    id: 'automatizacion',
    label: 'Automatización',
    icon: Workflow,
    children: [
      {
        id: 'sala',
        label: 'Sala de operaciones',
        icon: Activity,
        path: '/automatizacion',
        exact: true,
        roles: ['ADMINISTRADOR', 'OPERADOR'],
      },
      {
        id: 'scripts',
        label: 'Scripts',
        icon: Terminal,
        path: '/automatizacion/scripts',
        roles: ['ADMINISTRADOR', 'OPERADOR'],
      },
      {
        id: 'tareas',
        label: 'Tareas',
        icon: ListChecks,
        path: '/automatizacion/tareas',
        roles: ['ADMINISTRADOR', 'OPERADOR'],
      },
      {
        id: 'ejecuciones',
        label: 'Historial de ejecución',
        icon: History,
        path: '/automatizacion/ejecuciones',
        roles: ['ADMINISTRADOR', 'OPERADOR'],
      },
      {
        id: 'incidencias',
        label: 'Incidencias',
        icon: ShieldAlert,
        path: '/automatizacion/incidencias',
        roles: ['ADMINISTRADOR', 'OPERADOR'],
      },
    ],
  },
  { id: 'alertas', label: 'Mis alertas', icon: Bell, path: '/alertas' },
  { id: 'ordenes', label: 'Órdenes', icon: ClipboardList, path: '/ordenes', roles: ['ADMINISTRADOR', 'OPERADOR'] },
  {
    id: 'inventario',
    label: 'Inventario',
    icon: ServerCog,
    children: [
      { id: 'servidores', label: 'Servidores', icon: Server, path: '/servidores' },
      { id: 'grupos', label: 'Grupos de mantenimiento', icon: Boxes, path: '/grupos' },
    ],
  },
  // Catálogos es un apartado propio: rige el inventario y la planificación
  { id: 'catalogos', label: 'Catálogos', icon: BookOpen, path: '/catalogos', roles: ['ADMINISTRADOR'] },
  { id: 'umbrales', label: 'Umbrales', icon: Gauge, path: '/umbrales', roles: ['ADMINISTRADOR', 'OPERADOR'] },
  {
    id: 'configuracion',
    label: 'Configuración',
    icon: Settings,
    children: [
      {
        id: 'politicas',
        label: 'Políticas de cambio',
        icon: ShieldCheck,
        path: '/configuracion/politicas-cambio',
        roles: ['ADMINISTRADOR', 'OPERADOR'],
      },
      {
        id: 'usuarios',
        label: 'Usuarios y roles',
        icon: Users,
        path: '/configuracion/usuarios',
        roles: ['ADMINISTRADOR'],
      },
      {
        id: 'cuentas-servicio',
        label: 'Cuentas de servicio',
        icon: KeyRound,
        path: '/configuracion/cuentas-servicio',
        roles: ['ADMINISTRADOR'],
      },
      {
        id: 'parametros',
        label: 'Parámetros',
        icon: SlidersHorizontal,
        path: '/configuracion/parametros',
        roles: ['ADMINISTRADOR', 'OPERADOR'],
      },
      { id: 'preferencias', label: 'Preferencias', icon: Palette, path: '/configuracion/preferencias' },
    ],
  },
]

/** Los grupos sin hijos visibles desaparecen */
export function navegacionPara(rol: Rol | undefined): NavItem[] {
  const permitido = (leaf: NavLeaf) => !leaf.roles || (rol !== undefined && leaf.roles.includes(rol))
  return navigation.flatMap((item): NavItem[] => {
    if (!isNavGroup(item)) return permitido(item) ? [item] : []
    const hijos = item.children.filter(permitido)
    return hijos.length > 0 ? [{ ...item, children: hijos }] : []
  })
}

const TITULOS_FUERA_DEL_MENU: Record<string, string> = {
  '/mi-cuenta': 'Mi cuenta',
}

/** Las rutas de detalle (/servidores/12) toman el título de su sección */
export function findNavLabel(pathname: string): string {
  if (TITULOS_FUERA_DEL_MENU[pathname]) return TITULOS_FUERA_DEL_MENU[pathname]
  let mejor: { label: string; largo: number } | null = null
  for (const item of navigation) {
    const hojas = isNavGroup(item) ? item.children : [item]
    for (const leaf of hojas) {
      const coincide = pathname === leaf.path || pathname.startsWith(`${leaf.path}/`)
      if (coincide && (!mejor || leaf.path.length > mejor.largo)) {
        mejor = { label: leaf.label, largo: leaf.path.length }
      }
    }
  }
  return mejor?.label ?? 'KomainOS'
}
