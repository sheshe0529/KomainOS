import { Suspense } from 'react'
import { Navigate, Outlet, Route, Routes, useParams } from 'react-router-dom'
import { ThemeProvider } from '@/theme/ThemeProvider'
import { SesionProvider } from '@/auth/SesionProvider'
import { RutaProtegida } from '@/auth/RutaProtegida'
import { useSesion } from '@/auth/sesion-context'
import { AvisosProvider } from '@/components/ui/AvisosProvider'
import { Cargando } from '@/components/ui/Cargando'
import { MainLayout } from '@/components/layout/MainLayout'
import { inicioPara } from '@/config/navigation'
import {
  AlertasPage,
  AutomatizacionPage,
  CatalogosPage,
  CronogramaDiaPage,
  CronogramaPage,
  CuentasServicioPage,
  DashboardPage,
  EjecucionesPage,
  FichaGrupoPage,
  FichaServidorPage,
  GruposPage,
  IncidenciasPage,
  LoginPage,
  MiCuentaPage,
  NoEncontradoPage,
  OrdenDetallePage,
  OrdenesPage,
  ParametrosPage,
  PoliticasCambioPage,
  PublicarVersionPage,
  ScriptDetallePage,
  ScriptsPage,
  ServidoresPage,
  TareasPage,
  UmbralDetallePage,
  UmbralesPage,
  UsuariosPage,
} from '@/pages'

function RedirigirUmbral() {
  const { idUmbral } = useParams()
  return <Navigate to={`/umbrales/${idUmbral}`} replace />
}

/** El administrador y el operador entran al dashboard; el responsable, a su cronograma */
function Inicio() {
  const { usuario } = useSesion()
  return <Navigate to={inicioPara(usuario?.rol)} replace />
}

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
              <Route path="/" element={<Inicio />} />
              <Route
                path="/dashboard"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <DashboardPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/automatizacion"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <AutomatizacionPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/automatizacion/scripts"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <ScriptsPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/automatizacion/scripts/:id"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <ScriptDetallePage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/automatizacion/scripts/:id/publicar"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR']}>
                    <PublicarVersionPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/automatizacion/ejecuciones"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <EjecucionesPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/automatizacion/tareas"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <TareasPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/configuracion/politicas-cambio"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <PoliticasCambioPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/automatizacion/politicas"
                element={<Navigate to="/configuracion/politicas-cambio" replace />}
              />
              <Route
                path="/automatizacion/incidencias"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <IncidenciasPage />
                  </RutaProtegida>
                }
              />
              <Route path="/alertas" element={<AlertasPage />} />
              <Route path="/cronograma" element={<CronogramaPage />} />
              <Route path="/cronograma/dia/:fecha" element={<CronogramaDiaPage />} />
              <Route path="/ordenes" element={<OrdenesPage />} />
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
              <Route path="/configuracion/umbrales" element={<Navigate to="/umbrales" replace />} />
              <Route path="/configuracion/umbrales/:idUmbral" element={<RedirigirUmbral />} />
              <Route
                path="/umbrales"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <UmbralesPage />
                  </RutaProtegida>
                }
              />
              <Route
                path="/umbrales/:idUmbral"
                element={
                  <RutaProtegida roles={['ADMINISTRADOR', 'OPERADOR']}>
                    <UmbralDetallePage />
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
              {/* Las preferencias son parte de la cuenta: el enlace anterior lleva a Mi cuenta */}
              <Route path="/configuracion/preferencias" element={<Navigate to="/mi-cuenta" replace />} />
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
