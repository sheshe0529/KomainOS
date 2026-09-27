import { useState } from 'react'
import type { ReactNode } from 'react'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'

const SIDEBAR_COLLAPSED_KEY = 'komainos:sidebar-collapsed'

export function MainLayout({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(
    () => window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1',
  )
  const [mobileOpen, setMobileOpen] = useState(false)

  function toggleCollapsed() {
    setCollapsed((prev) => {
      const next = !prev
      window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? '1' : '0')
      return next
    })
  }

  return (
    <div className="min-h-dvh bg-canvas lg:flex">
      <Sidebar
        collapsed={collapsed}
        onToggleCollapsed={toggleCollapsed}
        mobileOpen={mobileOpen}
        onCloseMobile={() => setMobileOpen(false)}
      />

      {/* min-w-0 evita que el contenido empuje el ancho de la fila flex en pantallas angostas */}
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onOpenMobileNav={() => setMobileOpen(true)} />

        {/* Todo el contenido de página vive centrado acá, con el mismo margen
            en los cuatro breakpoints — el sidebar queda fuera de este contenedor
            a propósito, tal como pide la consigna. */}
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <div className="mx-auto w-full max-w-7xl">{children}</div>
        </main>
      </div>
    </div>
  )
}
