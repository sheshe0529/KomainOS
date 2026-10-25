import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { automatizacionApi } from '@/api/automatizacion'
import type { AlertaRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { Modal } from '@/components/ui/Modal'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Cargando } from '@/components/ui/Cargando'
import { formatearFechaHora } from '@/utils/formato'
import { AlertasContext } from './alertas-context'
import { colorAlerta, nombreAlerta } from './aparienciaAlerta'

export function AlertasProvider({ children }: { children: ReactNode }) {
  const { usuario, tieneRol } = useSesion()
  const [recientes, setRecientes] = useState<AlertaRespuesta[]>([])
  const [pendientes, setPendientes] = useState(0)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<unknown>()
  const [version, setVersion] = useState(0)
  const [detalle, setDetalle] = useState<AlertaRespuesta>()
  const [errorDetalle, setErrorDetalle] = useState<unknown>()
  const [consultando, setConsultando] = useState(false)
  const [abierto, setAbierto] = useState(false)
  const idAbierto = useRef<number | null>(null)
  const recargar = useCallback(() => setVersion((v) => v + 1), [])

  useEffect(() => {
    if (!usuario?.id) return
    let vigente = true
    setCargando(true)
    Promise.all([automatizacionApi.alertas(0, 8), automatizacionApi.alertasPendientes()])
      .then(([lista, contador]) => {
        if (vigente) {
          setRecientes(lista.contenido ?? [])
          setPendientes(contador.pendientes)
          setError(undefined)
        }
      })
      .catch((e) => {
        if (vigente) setError(e)
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
  }, [usuario?.id, version])

  useEffect(() => {
    const actualizar = () => {
      if (!document.hidden) recargar()
    }
    const timer = window.setInterval(actualizar, 30000)
    window.addEventListener('focus', actualizar)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', actualizar)
    }
  }, [recargar])

  async function marcarLeida(id: number) {
    await automatizacionApi.leerAlerta(id)
    setDetalle((d) => (d?.id === id ? { ...d, leida: true } : d))
    recargar()
  }
  async function abrir(id: number) {
    idAbierto.current = id
    setAbierto(true)
    setDetalle(undefined)
    setErrorDetalle(undefined)
    setConsultando(true)
    try {
      const dato = await automatizacionApi.alerta(id)
      if (idAbierto.current !== id) return
      setDetalle(dato)
      if (!dato.leida) await marcarLeida(id)
    } catch (e) {
      if (idAbierto.current === id) setErrorDetalle(e)
    } finally {
      if (idAbierto.current === id) setConsultando(false)
    }
  }
  function cerrar() {
    idAbierto.current = null
    setAbierto(false)
  }
  return (
    <AlertasContext.Provider value={{ recientes, pendientes, cargando, error, version, recargar, abrir, marcarLeida }}>
      {children}
      <Modal
        abierto={abierto}
        titulo="Detalle de alerta"
        ancho="lg"
        onCerrar={cerrar}
        pie={<Boton onClick={cerrar}>Cerrar</Boton>}
      >
        {consultando && !detalle && <Cargando texto="Cargando alerta…" />}
        <MensajeError error={errorDetalle} onReintentar={() => idAbierto.current && abrir(idAbierto.current)} />
        {detalle && (
          <div className="flex flex-col gap-5">
            <div>
              <p className="mb-2 flex items-center gap-2 text-xs text-ink-soft">
                <span className={`h-2 w-2 rounded-full ${colorAlerta(detalle.tipo)}`} />
                {nombreAlerta(detalle.tipo)}
              </p>
              <h4 className="text-lg font-semibold">{detalle.titulo}</h4>
              <p className="mt-2 text-xs text-ink-faint">
                {formatearFechaHora(detalle.fecha)} · {detalle.leida ? 'Leída' : 'Sin leer'}
              </p>
            </div>
            <p className="whitespace-pre-wrap break-words rounded-xl bg-panel-muted p-4 text-sm leading-relaxed">
              {detalle.mensaje || 'Sin información adicional.'}
            </p>
            <div className="flex flex-wrap gap-2">
              {detalle.idServidor && (
                <Link
                  onClick={cerrar}
                  className="rounded-lg border border-line px-3 py-2 text-sm text-accent hover:bg-panel-muted"
                  to={`/servidores/${detalle.idServidor}`}
                >
                  Ver servidor #{detalle.idServidor}
                </Link>
              )}
              {detalle.idGrupo && (
                <Link
                  onClick={cerrar}
                  className="rounded-lg border border-line px-3 py-2 text-sm text-accent hover:bg-panel-muted"
                  to={`/grupos/${detalle.idGrupo}`}
                >
                  Ver grupo #{detalle.idGrupo}
                </Link>
              )}
              {detalle.idOrden && (
                <Link
                  onClick={cerrar}
                  className="rounded-lg border border-line px-3 py-2 text-sm text-accent hover:bg-panel-muted"
                  to={`/ordenes/${detalle.idOrden}`}
                >
                  Ver orden #{detalle.idOrden}
                </Link>
              )}
              {detalle.idIncidencia && tieneRol('ADMINISTRADOR', 'OPERADOR') && (
                <Link
                  onClick={cerrar}
                  className="rounded-lg border border-line px-3 py-2 text-sm text-accent hover:bg-panel-muted"
                  to={`/automatizacion/incidencias?incidencia=${detalle.idIncidencia}`}
                >
                  Ver incidencia #{detalle.idIncidencia}
                </Link>
              )}
            </div>
          </div>
        )}
      </Modal>
    </AlertasContext.Provider>
  )
}
