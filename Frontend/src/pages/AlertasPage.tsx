import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CheckCheck } from 'lucide-react'
import { alertasApi } from '@/api/automatizacion'
import { PageHeader } from '@/components/common/PageHeader'
import { useAlertas } from '@/components/alertas/alertas-context'
import { colorAlerta, nombreAlerta } from '@/components/alertas/aparienciaAlerta'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { useConsulta } from '@/hooks/useConsulta'
import { formatearFechaHora } from '@/utils/formato'

/** Notificaciones dirigidas al usuario: autorizaciones, validaciones, incidencias y cierres (RF50, RF59, RF75) */
export function AlertasPage() {
  const alertas = useAlertas()
  const { avisar } = useAvisos()
  const [pagina, setPagina] = useState(0)
  const [soloNoLeidas, setSoloNoLeidas] = useState(false)
  const [marcando, setMarcando] = useState(false)
  const [error, setError] = useState<unknown>()
  const lista = useConsulta(
    () => alertasApi.listar(pagina, 20, soloNoLeidas),
    [pagina, soloNoLeidas, alertas.version],
  )
  const filas = lista.datos?.contenido ?? []

  async function leerTodas() {
    setMarcando(true)
    setError(undefined)
    try {
      const r = await alertasApi.leerTodas()
      avisar(r.leidas === 1 ? 'Una alerta marcada como leída.' : `${r.leidas} alertas marcadas como leídas.`)
      alertas.recargar()
    } catch (e) {
      setError(e)
    } finally {
      setMarcando(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Mis alertas"
        description="Lo que espera una decisión suya y lo que ocurrió en los mantenimientos de sus servidores."
        actions={
          <Boton icono={CheckCheck} cargando={marcando} disabled={alertas.pendientes === 0} onClick={leerTodas}>
            Marcar todas como leídas
          </Boton>
        }
      />

      <div role="radiogroup" aria-label="Alertas a mostrar" className="mb-4 flex w-fit gap-1 rounded-lg bg-panel-muted p-1">
        {[
          [false, 'Todas'],
          [true, `No leídas${alertas.pendientes ? ` (${alertas.pendientes})` : ''}`],
        ].map(([valor, etiqueta]) => (
          <button
            key={String(valor)}
            type="button"
            role="radio"
            aria-checked={soloNoLeidas === valor}
            onClick={() => {
              setSoloNoLeidas(valor as boolean)
              setPagina(0)
            }}
            className={`rounded-md px-3 py-1 text-sm transition-colors ${
              soloNoLeidas === valor ? 'bg-panel font-medium text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
            }`}
          >
            {etiqueta as string}
          </button>
        ))}
      </div>

      <MensajeError error={error ?? lista.error} onReintentar={lista.recargar} />

      {lista.cargando && !lista.datos ? (
        <Cargando />
      ) : filas.length === 0 ? (
        <p className="rounded-xl border border-line bg-panel px-5 py-10 text-center text-sm text-ink-soft">
          {soloNoLeidas ? 'No tiene alertas sin leer.' : 'No tiene alertas.'}
        </p>
      ) : (
        <div className="overflow-hidden rounded-xl border border-line bg-panel">
          <ul className="divide-y divide-line">
            {filas.map((a) => (
              <li key={a.id} className={`flex flex-wrap items-start gap-x-4 gap-y-2 px-5 py-4 ${a.leida ? '' : 'bg-accent-soft/50'}`}>
                <span className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${colorAlerta(a.tipo)}`} aria-hidden="true" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-ink-soft">
                    {nombreAlerta(a.tipo)} · {formatearFechaHora(a.fecha)}
                    {!a.leida && <span className="ml-2 font-medium text-ink">Nueva</span>}
                  </p>
                  <button
                    type="button"
                    onClick={() => alertas.abrir(a.id!)}
                    className={`text-left text-sm hover:text-accent hover:underline ${a.leida ? 'text-ink' : 'font-semibold text-ink'}`}
                  >
                    {a.titulo}
                  </button>
                  <p className="mt-0.5 line-clamp-2 text-sm text-ink-soft">{a.mensaje}</p>
                  <div className="mt-1 flex flex-wrap gap-x-4 text-sm">
                    {a.idOrden && (
                      <Link to={`/ordenes/${a.idOrden}`} className="text-accent hover:underline">
                        Ver orden
                      </Link>
                    )}
                    {a.idIncidencia && (
                      <Link to={`/automatizacion/incidencias?incidencia=${a.idIncidencia}`} className="text-accent hover:underline">
                        Ver incidencia
                      </Link>
                    )}
                    {a.idServidor && (
                      <Link to={`/servidores/${a.idServidor}`} className="text-accent hover:underline">
                        Ver servidor
                      </Link>
                    )}
                  </div>
                </div>
                {!a.leida && (
                  <Boton
                    variante="fantasma"
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
              </li>
            ))}
          </ul>
          <Paginacion
            pagina={pagina}
            totalPaginas={lista.datos?.totalPaginas ?? 0}
            totalElementos={lista.datos?.totalElementos ?? 0}
            onCambiar={setPagina}
          />
        </div>
      )}
    </>
  )
}
