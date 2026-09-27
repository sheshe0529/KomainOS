import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '@/theme/ThemeProvider'
import { SesionProvider } from '@/auth/SesionProvider'
import { RutaProtegida } from '@/auth/RutaProtegida'
import { AvisosProvider } from '@/components/ui/AvisosProvider'
import { MainLayout } from '@/components/layout/MainLayout'
import { LoginPage } from '@/pages/LoginPage'
import { ServidoresPage } from '@/pages/ServidoresPage'
import { FichaServidorPage } from '@/pages/FichaServidorPage'
import { GruposPage } from '@/pages/GruposPage'
import { FichaGrupoPage } from '@/pages/FichaGrupoPage'
import { CatalogosPage } from '@/pages/CatalogosPage'
import { ParametrosPage } from '@/pages/ParametrosPage'
import { UsuariosPage } from '@/pages/UsuariosPage'
import { PreferenciasPage } from '@/pages/PreferenciasPage'
import { NoEncontradoPage } from '@/pages/NoEncontradoPage'
import { CronogramaPage } from '@/pages/CronogramaPage'
import { CronogramaDiaPage } from '@/pages/CronogramaDiaPage'
import { OrdenesPage } from '@/pages/OrdenesPage'
import { OrdenDetallePage } from '@/pages/OrdenDetallePage'
import { MiCuentaPage } from '@/pages/MiCuentaPage'

/** Disposición común de las vistas autenticadas: sidebar, topbar y contenido centrado. */
function DisposicionAutenticada() {
  return (
    <RutaProtegida>
      <MainLayout>
        <Outlet />
      </MainLayout>
    </RutaProtegida>
  )
}

/**
 * El árbol de rutas refleja src/config/navigation.ts: cada `path` del menú
 * tiene aquí su <Route>. Las restricciones de rol repiten las del menú para
 * que un acceso directo por URL tampoco exponga la vista.
 */
function App() {
  return (
    <ThemeProvider>
      <SesionProvider>
        <AvisosProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<DisposicionAutenticada />}>
              <Route path="/" element={<Navigate to="/cronograma" replace />} />
              <Route path="/cronograma" element={<CronogramaPage />} />
              <Route path="/cronograma/dia/:fecha" element={<CronogramaDiaPage />} />
              <Route
                path="/ordenes"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <OrdenesPage />
                  </RutaProtegida>
                }
              />
              <Route path="/ordenes/:id" element={<OrdenDetallePage />} />
              <Route path="/servidores" element={<ServidoresPage />} />
              <Route path="/servidores/:id" element={<FichaServidorPage />} />
              <Route path="/grupos" element={<GruposPage />} />
              <Route path="/grupos/:id" element={<FichaGrupoPage />} />
              <Route
                path="/configuracion/usuarios"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR']}>
                    <UsuariosPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/catalogos"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR']}>
                    <CatalogosPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/configuracion/parametros"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <ParametrosPage />
                  </RutaProtegida>
                }
              />
              <Route path="/configuracion/preferencias" element={<PreferenciasPage />} />
              <Route path="/mi-cuenta" element={<MiCuentaPage />} />
              <Route path="*" element={<NoEncontradoPage />} />
            </Route>
          </Routes>
        </AvisosProvider>
      </SesionProvider>
    </ThemeProvider>
  )
}

export default App
