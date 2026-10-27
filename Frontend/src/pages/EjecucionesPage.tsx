import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Download } from 'lucide-react'
import { operacionApi, scriptsApi } from '@/api/automatizacion'
import type { FiltroEjecuciones } from '@/api/automatizacion'
import type { EstadoTareaMop } from '@/api/dominio'
import type { EjecucionRespuesta } from '@/api/types'
import { PageHeader } from '@/components/common/PageHeader'
import { Dato } from '@/components/common/Tarjeta'
import { SalidaTerminal } from '@/components/automatizacion/SalidaTerminal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada, Selector } from '@/components/ui/Campo'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { useTextoDiferido } from '@/hooks/useTextoDiferido'
import { descargarTexto, ESTADO_TAREA_MOP, filaCsv, formatearSegundos } from '@/utils/automatizacion'
import { formatearDuracion, formatearFechaHora } from '@/utils/formato'

/** Tope de la exportación: 20 páginas de 100 filas, las más recientes */
const MAX_PAGINAS_EXPORTACION = 20

function DetalleEjecucion({ id, onCerrar }: { id: number; onCerrar: () => void }) {
  const detalle = useConsulta(() => operacionApi.ejecucion(id), [id])
  const e = detalle.datos?.ejecucion

  return (
    <Modal abierto ancho="xl" titulo={e ? `${e.tarea} en ${e.servidor}` : 'Ejecución'} onCerrar={onCerrar}>
      <MensajeError error={detalle.error} onReintentar={detalle.recargar} />
      {detalle.cargando && !detalle.datos && <Cargando />}
      {e && detalle.datos && (
        <div className="flex flex-col gap-5">
          <div className="flex flex-wrap items-center gap-3">
            {e.estado && <StatusPill tone={ESTADO_TAREA_MOP[e.estado].tono} label={ESTADO_TAREA_MOP[e.estado].etiqueta} />}
            {e.codigoRetorno !== undefined && e.codigoRetorno !== null && (
              <span className="font-mono text-sm text-ink-soft">código de salida {e.codigoRetorno}</span>
            )}
          </div>
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Dato
              etiqueta="Orden"
              valor={
                <Link to={`/ordenes/${e.idOrden}`} className="font-mono text-accent hover:underline">
                  {e.orden}
                </Link>
              }
            />
            <Dato
              etiqueta="Script"
              valor={
                <Link to={`/automatizacion/scripts/${e.idScript}`} className="text-accent hover:underline">
                  {e.script} v{e.numeroVersion}
                </Link>
              }
            />
            <Dato etiqueta="Sistema operativo" valor={e.sistemaOperativo} />
            <Dato etiqueta="Cuenta de servicio" valor={e.cuenta} />
            <Dato etiqueta="Inicio" valor={formatearFechaHora(e.inicio)} />
            <Dato etiqueta="Fin" valor={formatearFechaHora(e.fin)} />
            <Dato
              etiqueta="Duración"
              valor={`${formatearSegundos(e.duracionSegundos)} de ${formatearDuracion(detalle.datos.duracionEstimadaMinutos)} estimados`}
            />
            <Dato etiqueta="Prioridad" valor={detalle.datos.prioridad} />
          </dl>
          <div>
            <h4 className="mb-2 text-sm font-medium text-ink">Salida</h4>
            <SalidaTerminal
              salida={detalle.datos.salida}
              error={e.error}
              nombre={`ejecucion_${e.id}_${e.servidor}`}
              altoMaximo="24rem"
            />
          </div>
        </div>
      )}
    </Modal>
  )
}

/** Historial de ejecución de scripts (R3.4): qué se ejecutó, dónde, con qué versión y cuenta, y cómo terminó */
export function EjecucionesPage() {
  const { avisar } = useAvisos()
  const [busqueda, setBusqueda] = useState('')
  const texto = useTextoDiferido(busqueda)
  const [estado, setEstado] = useState<EstadoTareaMop | ''>('')
  const [idScript, setIdScript] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [pagina, setPagina] = useState(0)
  const [abierta, setAbierta] = useState<number>()
  const [exportando, setExportando] = useState(false)
  const [error, setError] = useState<unknown>()

  const scripts = useConsulta(() => scriptsApi.todos({ tipo: 'MANTENIMIENTO' }), [])

  // Los días del filtro son locales; el fin es inclusivo
  const filtro: FiltroEjecuciones = {
    texto,
    estado,
    idScript: idScript ? Number(idScript) : undefined,
    desde: desde ? new Date(`${desde}T00:00:00`).toISOString() : undefined,
    hasta: hasta ? new Date(new Date(`${hasta}T00:00:00`).getTime() + 86_400_000).toISOString() : undefined,
  }
  const clave = JSON.stringify(filtro)
  const ejecuciones = useConsulta(() => operacionApi.ejecuciones(filtro, pagina), [clave, pagina])
  const filas = ejecuciones.datos?.contenido ?? []

  const cambiar = <T,>(fijar: (v: T) => void) => (v: T) => {
    fijar(v)
    setPagina(0)
  }

  async function exportar() {
    setExportando(true)
    setError(undefined)
    try {
      const todas: EjecucionRespuesta[] = []
      for (let p = 0; p < MAX_PAGINAS_EXPORTACION; p++) {
        const r = await operacionApi.ejecuciones(filtro, p, 100)
        todas.push(...(r.contenido ?? []))
        if (p + 1 >= (r.totalPaginas ?? 0)) break
      }
      const lineas = [
        filaCsv(['Inicio', 'Fin', 'Orden', 'Servidor', 'Tarea', 'Script', 'Versión', 'Sistema operativo', 'Cuenta',
          'Estado', 'Código de salida', 'Duración (s)', 'Error']),
        ...todas.map((e) =>
          filaCsv([e.inicio, e.fin, e.orden, e.servidor, e.tarea, e.script, e.numeroVersion, e.sistemaOperativo, e.cuenta,
            e.estado ? ESTADO_TAREA_MOP[e.estado].etiqueta : '', e.codigoRetorno, e.duracionSegundos, e.error]),
        ),
      ]
      descargarTexto(`historial_ejecucion_${new Date().toISOString().slice(0, 10)}.csv`, lineas.join('\r\n'), 'text/csv')
      avisar(`${todas.length} ejecuciones exportadas.`)
    } catch (e) {
      setError(e)
    } finally {
      setExportando(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Historial de ejecución"
        description="Cada tarea ejecutada en un servidor: versión del script, cuenta usada, duración y resultado."
        actions={
          <Boton icono={Download} cargando={exportando} disabled={filas.length === 0} onClick={exportar}>
            Exportar CSV
          </Boton>
        }
      />

      <div className="mb-4 flex flex-wrap items-end gap-2">
        <CampoBusqueda
          valor={busqueda}
          onCambiar={cambiar(setBusqueda)}
          placeholder="Buscar por servidor, tarea u orden"
          aria-label="Buscar ejecuciones"
        />
        <Selector
          aria-label="Filtrar por estado"
          className="!w-auto"
          value={estado}
          onChange={(e) => cambiar(setEstado)(e.target.value as EstadoTareaMop | '')}
        >
          <option value="">Todos los estados</option>
          {(Object.keys(ESTADO_TAREA_MOP) as EstadoTareaMop[]).map((e) => (
            <option key={e} value={e}>
              {ESTADO_TAREA_MOP[e].etiqueta}
            </option>
          ))}
        </Selector>
        <Selector aria-label="Filtrar por script" className="!w-auto" value={idScript} onChange={(e) => cambiar(setIdScript)(e.target.value)}>
          <option value="">Todos los scripts</option>
          {scripts.datos?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.nombre}
            </option>
          ))}
        </Selector>
        <Campo etiqueta="Desde" className="w-40">
          <Entrada type="date" value={desde} max={hasta || undefined} onChange={(e) => cambiar(setDesde)(e.target.value)} />
        </Campo>
        <Campo etiqueta="Hasta" className="w-40">
          <Entrada type="date" value={hasta} min={desde || undefined} onChange={(e) => cambiar(setHasta)(e.target.value)} />
        </Campo>
      </div>

      <MensajeError error={error ?? ejecuciones.error} onReintentar={ejecuciones.recargar} />

      {ejecuciones.cargando && !ejecuciones.datos ? (
        <Cargando />
      ) : filas.length === 0 ? (
        <p className="rounded-xl border border-line bg-panel px-5 py-10 text-center text-sm text-ink-soft">
          No hay ejecuciones con estos filtros.
        </p>
      ) : (
        <div className="overflow-hidden rounded-xl border border-line bg-panel">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[960px] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="px-5 py-3 font-medium">Inicio</th>
                  <th className="px-3 py-3 font-medium">Servidor</th>
                  <th className="px-3 py-3 font-medium">Tarea · script</th>
                  <th className="px-3 py-3 font-medium">Cuenta</th>
                  <th className="px-3 py-3 font-medium">Estado</th>
                  <th className="px-3 py-3 text-right font-medium">Código</th>
                  <th className="px-3 py-3 text-right font-medium">Duración</th>
                  <th className="px-3 py-3 font-medium">Orden</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filas.map((e) => (
                  <tr key={e.id} onClick={() => setAbierta(e.id)} className="cursor-pointer align-top hover:bg-panel-muted">
                    <td className="px-5 py-3 whitespace-nowrap text-ink-soft">{formatearFechaHora(e.inicio)}</td>
                    <td className="px-3 py-3 font-mono">{e.servidor}</td>
                    <td className="max-w-xs px-3 py-3">
                      <span className="text-ink">{e.tarea}</span>
                      <span className="block text-xs text-ink-faint">
                        {e.script} v{e.numeroVersion} · {e.sistemaOperativo}
                      </span>
                      {e.error && <span className="mt-1 line-clamp-1 block text-xs text-danger">{e.error}</span>}
                    </td>
                    <td className="px-3 py-3 text-ink-soft">{e.cuenta ?? '—'}</td>
                    <td className="px-3 py-3">
                      {e.estado && <StatusPill tone={ESTADO_TAREA_MOP[e.estado].tono} label={ESTADO_TAREA_MOP[e.estado].etiqueta} />}
                    </td>
                    <td className="px-3 py-3 text-right font-mono tabular-nums">{e.codigoRetorno ?? '—'}</td>
                    <td className="px-3 py-3 text-right whitespace-nowrap tabular-nums">{formatearSegundos(e.duracionSegundos)}</td>
                    <td className="px-3 py-3">
                      <Link
                        to={`/ordenes/${e.idOrden}`}
                        onClick={(ev) => ev.stopPropagation()}
                        className="font-mono text-accent hover:underline"
                      >
                        {e.orden}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion
            pagina={pagina}
            totalPaginas={ejecuciones.datos?.totalPaginas ?? 0}
            totalElementos={ejecuciones.datos?.totalElementos ?? 0}
            onCambiar={setPagina}
          />
        </div>
      )}

      {abierta && <DetalleEjecucion id={abierta} onCerrar={() => setAbierta(undefined)} />}
    </>
  )
}
