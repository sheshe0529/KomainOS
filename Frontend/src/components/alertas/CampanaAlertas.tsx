import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { MensajeError } from '@/components/ui/MensajeError'
import { formatearFechaHora } from '@/utils/formato'
import { useAlertas } from './alertas-context'
import { colorAlerta } from './aparienciaAlerta'

function tiempoRelativo(fecha?: string) {
  if (!fecha || Number.isNaN(new Date(fecha).getTime())) return 'Fecha no disponible'
  const minutos = Math.max(0, Math.floor((Date.now() - new Date(fecha).getTime()) / 60000))
  if (!minutos) return 'Ahora'
  if (minutos < 60) return `Hace ${minutos} min`
  if (minutos < 1440) return `Hace ${Math.floor(minutos / 60)} h`
  if (minutos < 10080) return `Hace ${Math.floor(minutos / 1440)} días`
  return formatearFechaHora(fecha)
}

export function CampanaAlertas() {
  const alertas = useAlertas()
  const [abierto, setAbierto] = useState(false)
  const contenedor = useRef<HTMLDivElement>(null)
  const boton = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (!abierto) return
    function cerrarFuera(e: PointerEvent) {
      if (!contenedor.current?.contains(e.target as Node)) setAbierto(false)
    }
    function teclado(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setAbierto(false)
        boton.current?.focus()
      }
    }
    document.addEventListener('pointerdown', cerrarFuera)
    document.addEventListener('keydown', teclado)
    return () => {
      document.removeEventListener('pointerdown', cerrarFuera)
      document.removeEventListener('keydown', teclado)
    }
  }, [abierto])
  return (
    <div ref={contenedor} className="relative">
      <button
        ref={boton}
        type="button"
        aria-label={`Alertas, ${alertas.pendientes} sin leer`}
        title="Alertas"
        aria-expanded={abierto}
        aria-controls="alertas-recientes"
        aria-haspopup="dialog"
        onClick={() => {
          setAbierto(!abierto)
          if (!abierto) alertas.recargar()
        }}
        className={`relative flex h-10 w-10 items-center justify-center rounded-xl text-ink-soft transition-colors hover:bg-panel-muted ${abierto ? 'bg-accent-soft text-accent' : ''}`}
      >
        <Bell className="h-5 w-5" aria-hidden="true" />
        {alertas.pendientes > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex min-h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold leading-4 text-white">
            {alertas.pendientes > 99 ? '99+' : alertas.pendientes}
          </span>
        )}
      </button>
      {abierto && (
        <div
          id="alertas-recientes"
          role="dialog"
          aria-label="Alertas recientes"
          className="fixed right-4 top-[4.5rem] z-50 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-2xl border border-line bg-panel shadow-xl shadow-black/10 sm:absolute sm:right-0 sm:top-12"
        >
          <div className="flex items-center justify-between px-5 pb-2 pt-4">
            <h2 className="text-sm font-semibold">Alertas</h2>
            <span className="rounded-full bg-accent-soft px-2 py-1 text-[10px] font-medium text-accent">
              {alertas.pendientes} sin leer
            </span>
          </div>
          <MensajeError error={alertas.error} onReintentar={alertas.recargar} />
          <div className="max-h-80 overflow-y-auto px-2 pb-2">
            {alertas.cargando && !alertas.recientes.length && (
              <p className="px-3 py-5 text-sm text-ink-soft">Cargando alertas…</p>
            )}
            {!alertas.cargando && !alertas.error && !alertas.recientes.length && (
              <p className="px-3 py-5 text-sm text-ink-soft">No tiene alertas.</p>
            )}
            {alertas.recientes.map((a) => (
              <button
                key={a.id}
                type="button"
                onClick={() => {
                  setAbierto(false)
                  alertas.abrir(a.id!)
                }}
                className="flex w-full items-start gap-3 rounded-xl px-3 py-3 text-left hover:bg-panel-muted"
              >
                <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${colorAlerta(a.tipo)}`} />
                <span className="min-w-0 flex-1">
                  <span
                    className={`block text-xs leading-relaxed ${a.leida ? 'text-ink-soft' : 'font-medium text-ink'}`}
                  >
                    {a.titulo}
                  </span>
                  <span title={formatearFechaHora(a.fecha)} className="mt-1 block font-mono text-[10px] text-ink-faint">
                    {a.idServidor ? `Servidor #${a.idServidor} · ` : a.idOrden ? `Orden #${a.idOrden} · ` : ''}
                    {tiempoRelativo(a.fecha)}
                  </span>
                </span>
                {!a.leida && (
                  <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-accent" aria-label="Sin leer" />
                )}
              </button>
            ))}
          </div>
          <Link
            to="/alertas"
            onClick={() => setAbierto(false)}
            className="block border-t border-line px-4 py-3 text-center text-xs font-medium text-accent hover:bg-panel-muted"
          >
            Ver todas las alertas
          </Link>
        </div>
      )}
    </div>
  )
}
