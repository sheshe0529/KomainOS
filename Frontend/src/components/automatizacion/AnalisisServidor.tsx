import { useState } from 'react'
import { automatizacionApi } from '@/api/automatizacion'
import { Tarjeta } from '@/components/common/Tarjeta'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { useConsulta } from '@/hooks/useConsulta'
import { useSesion } from '@/auth/sesion-context'
import { formatearFechaHora } from '@/utils/formato'
export function AnalisisServidor({ id }: { id: number }) {
  const { tieneRol } = useSesion()
  const admin = tieneRol('ADMINISTRADOR')
  const [pagina, setPagina] = useState(0)
  const historial = useConsulta(() => automatizacionApi.evaluaciones(id, pagina), [id, pagina])
  const [trabajando, setTrabajando] = useState(false)
  const [error, setError] = useState<unknown>()
  const [mensaje, setMensaje] = useState('')
  async function actuar(conexion: boolean) {
    setTrabajando(true)
    setError(undefined)
    setMensaje('')
    try {
      if (conexion) {
        const r = await automatizacionApi.probarConexion(id)
        setMensaje(r.mensaje)
      } else {
        const r = await automatizacionApi.revisar(id)
        setMensaje(r.estado === 'FINALIZADA' ? 'Revisión del servidor completada.' : 'La revisión registró un error.')
        historial.recargar()
      }
    } catch (e) {
      setError(e)
    } finally {
      setTrabajando(false)
    }
  }
  return (
    <Tarjeta
      titulo="Análisis y revisiones periódicas"
      className="mt-6"
      acciones={
        admin && (
          <>
            <Boton cargando={trabajando} onClick={() => actuar(true)}>
              Probar conectividad
            </Boton>
            <Boton cargando={trabajando} onClick={() => actuar(false)}>
              Revisar ahora
            </Boton>
          </>
        )
      }
    >
      <MensajeError error={error ?? historial.error} />
      {mensaje && (
        <p role="status" className="mb-3 text-sm text-ink-soft">
          {mensaje}
        </p>
      )}
      <div className="flex flex-col gap-2">
        {historial.datos?.contenido?.length === 0 && (
          <p className="text-sm text-ink-soft">Sin evaluaciones registradas.</p>
        )}
        {historial.datos?.contenido?.map((e) => (
          <details key={e.id} className="rounded-lg border border-line p-3 text-sm">
            <summary className="cursor-pointer">
              {e.tipo?.replaceAll('_', ' ')} · {e.estado} · {formatearFechaHora(e.fechaInicio)}
            </summary>
            {e.error && <p className="mt-2 text-danger">{e.error}</p>}
            <div className="mt-3 grid gap-2 sm:grid-cols-3">
              {e.mediciones?.map((m) => (
                <div key={`${m.codigo}/${m.instancia}`} className="rounded bg-panel-muted p-3">
                  <span className="block text-xs text-ink-soft">
                    {m.nombre} {m.instancia}
                  </span>
                  <strong>{m.valor ?? m.elementos?.join(', ')}</strong>
                  <p className="text-xs">{m.umbrales?.map((u) => u.nivel).join(' · ')}</p>
                </div>
              ))}
            </div>
          </details>
        ))}
      </div>
      <Paginacion
        pagina={pagina}
        totalPaginas={historial.datos?.totalPaginas ?? 0}
        totalElementos={historial.datos?.totalElementos ?? 0}
        onCambiar={setPagina}
      />
    </Tarjeta>
  )
}
