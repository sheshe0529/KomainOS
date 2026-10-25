import { lazy } from 'react'

/** Cada pantalla se descarga al visitarla por primera vez: el archivo inicial solo lleva el esqueleto del panel */
export const LoginPage = lazy(() => import('./LoginPage').then((m) => ({ default: m.LoginPage })))
export const CronogramaPage = lazy(() => import('./CronogramaPage').then((m) => ({ default: m.CronogramaPage })))
export const CronogramaDiaPage = lazy(() => import('./CronogramaDiaPage').then((m) => ({ default: m.CronogramaDiaPage })))
export const OrdenesPage = lazy(() => import('./OrdenesPage').then((m) => ({ default: m.OrdenesPage })))
export const OrdenDetallePage = lazy(() => import('./OrdenDetallePage').then((m) => ({ default: m.OrdenDetallePage })))
export const ServidoresPage = lazy(() => import('./ServidoresPage').then((m) => ({ default: m.ServidoresPage })))
export const FichaServidorPage = lazy(() => import('./FichaServidorPage').then((m) => ({ default: m.FichaServidorPage })))
export const GruposPage = lazy(() => import('./GruposPage').then((m) => ({ default: m.GruposPage })))
export const FichaGrupoPage = lazy(() => import('./FichaGrupoPage').then((m) => ({ default: m.FichaGrupoPage })))
export const CatalogosPage = lazy(() => import('./CatalogosPage').then((m) => ({ default: m.CatalogosPage })))
export const UsuariosPage = lazy(() => import('./UsuariosPage').then((m) => ({ default: m.UsuariosPage })))
export const CuentasServicioPage = lazy(() => import('./CuentasServicioPage').then((m) => ({ default: m.CuentasServicioPage })))
export const UmbralesPage = lazy(() => import('./UmbralesPage').then((m) => ({ default: m.UmbralesPage })))
export const UmbralDetallePage = lazy(() => import('./UmbralDetallePage').then((m) => ({ default: m.UmbralDetallePage })))
export const ParametrosPage = lazy(() => import('./ParametrosPage').then((m) => ({ default: m.ParametrosPage })))
export const PreferenciasPage = lazy(() => import('./PreferenciasPage').then((m) => ({ default: m.PreferenciasPage })))
export const MiCuentaPage = lazy(() => import('./MiCuentaPage').then((m) => ({ default: m.MiCuentaPage })))
export const NoEncontradoPage = lazy(() => import('./NoEncontradoPage').then((m) => ({ default: m.NoEncontradoPage })))

export const AutomatizacionPage=lazy(()=>import('./AutomatizacionPage').then(m=>({default:m.AutomatizacionPage})))

export const ScriptsPage=lazy(()=>import('./ScriptsPage').then(m=>({default:m.ScriptsPage})))

export const TareasPage=lazy(()=>import('./TareasPage').then(m=>({default:m.TareasPage})))

export const PoliticasCambioPage=lazy(()=>import('./PoliticasCambioPage').then(m=>({default:m.PoliticasCambioPage})))

export const IncidenciasPage=lazy(()=>import('./IncidenciasPage').then(m=>({default:m.IncidenciasPage})))

export const AlertasPage=lazy(()=>import('./AlertasPage').then(m=>({default:m.AlertasPage})))
