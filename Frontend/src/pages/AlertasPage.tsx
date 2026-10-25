import { useState } from 'react'
import { Link } from 'react-router-dom'
import { automatizacionApi } from '@/api/automatizacion'
import { PageHeader } from '@/components/common/PageHeader'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { TablaAutomatizacion, celda } from '@/components/automatizacion/TablaAutomatizacion'
import { useConsulta } from '@/hooks/useConsulta'
import { formatearFechaHora } from '@/utils/formato'
import { useAlertas } from '@/components/alertas/alertas-context'
import { colorAlerta, nombreAlerta } from '@/components/alertas/aparienciaAlerta'
export function AlertasPage() {
  const [pagina, setPagina] = useState(0)
  const alertas = useAlertas()
  const lista = useConsulta(() => automatizacionApi.alertas(pagina), [pagina, alertas.version])
  const [error, setError] = useState<unknown>()
  return (
    <>
      <PageHeader
        title="Mis alertas"
        description="Notificaciones dirigidas a su usuario sobre autorizaciones, validaciones e incidencias."
      />
      <MensajeError error={error ?? lista.error} />
      <TablaAutomatizacion
        pagina={lista.datos}
        onPagina={setPagina}
        cabeceras={['Evento', 'Detalle', 'Fecha', 'Estado', 'Acciones']}
        fila={(a) => (
          <tr key={a.id} className={a.leida ? '' : 'bg-accent-soft'}>
            <td className={celda}>
              <span className="mb-1 flex items-center gap-2 text-xs text-ink-soft">
                <span className={`h-2 w-2 rounded-full ${colorAlerta(a.tipo)}`} />
                {nombreAlerta(a.tipo)}
              </span>
              <button
                type="button"
                className="text-left font-medium hover:text-accent hover:underline"
                onClick={() => alertas.abrir(a.id!)}
              >
                {a.titulo}
              </button>
            </td>
            <td className={celda}>
              <p className="max-w-md line-clamp-2">{a.mensaje}</p>
              {a.idOrden && (
                <Link className="text-accent hover:underline" to={`/ordenes/${a.idOrden}`}>
                  Ver orden
                </Link>
              )}
            </td>
            <td className={celda}>{formatearFechaHora(a.fecha)}</td>
            <td className={celda}>{a.leida ? 'Leída' : 'Pendiente'}</td>
            <td className={celda}>
              <Boton onClick={() => alertas.abrir(a.id!)}>Ver detalle</Boton>
              {!a.leida && (
                <Boton
                  onClick={async () => {
                    try {
                      await alertas.marcarLeida(a.id!)
                    } catch (e) {
                      setError(e)
                    }
                  }}
                >
                  Marcar leída
                </Boton>
              )}
            </td>
          </tr>
        )}
      />
    </>
  )
}
