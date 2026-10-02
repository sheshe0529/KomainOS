import { Suspense } from 'react'
import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '@/theme/ThemeProvider'
import { SesionProvider } from '@/auth/SesionProvider'
import { RutaProtegida } from '@/auth/RutaProtegida'
import { AvisosProvider } from '@/components/ui/AvisosProvider'
import { Cargando } from '@/components/ui/Cargando'
import { MainLayout } from '@/components/layout/MainLayout'
import {
  CatalogosPage,
  CronogramaDiaPage,
  CronogramaPage,
  CuentasServicioPage,
  FichaGrupoPage,
  FichaServidorPage,
  GruposPage,
  LoginPage,
  MiCuentaPage,
  NoEncontradoPage,
  OrdenDetallePage,
  OrdenesPage,
  ParametrosPage,
  PreferenciasPage,
  ServidoresPage,
  UsuariosPage,
} from '@/pages'

/** El menú y la barra superior se quedan mientras se descarga la pantalla */
function DisposicionAutenticada() {
  return (
    <RutaProtegida>
      <MainLayout>
        <Suspense fallback={<Cargando />}>
          <Outlet />
        </Suspense>
      </MainLayout>
    </RutaProtegida>
  )
}

/** Cada path de navigation.ts tiene aquí su Route, con las mismas restricciones de rol para el acceso directo por URL */
function App() {
  return (
    <ThemeProvider>
      <SesionProvider>
        <AvisosProvider>
          <Routes>
            <Route
              path="/login"
              element={
                <Suspense fallback={<Cargando pantallaCompleta />}>
                  <LoginPage />
                </Suspense>
              }
            />
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
                path="/configuracion/cuentas-servicio"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR']}>
                    <CuentasServicioPage />
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
