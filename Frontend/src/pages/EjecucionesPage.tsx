import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Download } from 'lucide-react'
import { operacionApi, scriptsApi } from '@/api/automatizacion'
import type { FiltroEjecuciones } from '@/api/automatizacion'
import type { EstadoTareaMop } from '@/api/dominio'
import type { EjecucionRespuesta } from '@/api/types'
import { PageHeader } from '@/components/common/PageHeader'
import { Dato } from '@/components/common/Tarjeta'
import { ActividadTarea } from '@/components/automatizacion/RegistroActividad'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Campo, Selector } from '@/components/ui/Campo'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { Cargando } from '@/components/ui/Cargando'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { SelectorFecha } from '@/components/ui/SelectorFecha'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacion } from '@/hooks/usePaginacion'
import { useRefrescoConsulta } from '@/hooks/useRefrescoConsulta'
import { useTextoDiferido } from '@/hooks/useTextoDiferido'
import { descargarTexto, diagnosticarFallo, ESTADO_TAREA_MOP, filaCsv, formatearSegundos } from '@/utils/automatizacion'
import { formatearDuracion, formatearFechaHora } from '@/utils/formato'

/** Tope de la exportación: 20 páginas de 100 filas, las más recientes */
const MAX_PAGINAS_EXPORTACION = 20

function DetalleEjecucion({ id, onCerrar }: { id: number; onCerrar: () => void }) {
  const detalle = useConsulta(() => operacionApi.ejecucion(id), [id])
  const e = detalle.datos?.ejecucion
  useRefrescoConsulta(detalle, e?.estado === 'EN_EJECUCION', 1000)

  return (
    <Modal abierto ancho="xl" titulo={e ? `${e.tarea} en ${e.servidor}` : 'Ejecución'} onCerrar={onCerrar}>
      <MensajeError error={detalle.error} onReintentar={detalle.recargar} />
      {detalle.cargando && !detalle.datos && <Cargando />}
      {e && detalle.datos && (
        <div className="flex flex-col gap-5">
          <div role="status">
            {e.estado && <StatusPill tone={ESTADO_TAREA_MOP[e.estado].tono} label={ESTADO_TAREA_MOP[e.estado].etiqueta} />}
          </div>
          <ActividadTarea
            idEjecucion={id}
            refrescoMs={e.estado === 'EN_EJECUCION' ? 1000 : undefined}
            tarea={{
              id: e.id,
              tarea: e.tarea,
              cuenta: e.cuenta,
              estado: e.estado,
              fechaInicio: e.inicio,
              fechaFin: e.fin,
              codigoRetorno: e.codigoRetorno,
              duracionSegundos: e.duracionSegundos,
              error: e.error,
              salida: detalle.datos.salida,
            }}
            nombreSalida={`ejecucion_${e.id}_${e.servidor}`}
          />
          <dl className="grid grid-cols-2 gap-4 lg:grid-cols-4">
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
  const { pagina, tamano, setPagina, setTamano } = usePaginacion('ejecuciones')
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
  const ejecuciones = useConsulta(() => operacionApi.ejecuciones(filtro, pagina, tamano), [clave, pagina, tamano])
  const filas = ejecuciones.datos?.contenido ?? []
  useRefrescoConsulta(ejecuciones, abierta == null && filas.some((e) => e.estado === 'EN_EJECUCION'))

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
        <Campo etiqueta="Desde" className="w-48">
          <SelectorFecha value={desde} max={hasta || undefined} onChange={cambiar(setDesde)} aria-label="Desde" />
        </Campo>
        <Campo etiqueta="Hasta" className="w-48">
          <SelectorFecha value={hasta} min={desde || undefined} onChange={cambiar(setHasta)} aria-label="Hasta" />
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
                  <FilaDetalle key={e.id} onAbrir={() => setAbierta(e.id)} className="align-top">
                    <td className="px-5 py-3 whitespace-nowrap text-ink-soft">{formatearFechaHora(e.inicio)}</td>
                    <td className="px-3 py-3 font-mono">{e.servidor}</td>
                    <td className="max-w-xs px-3 py-3">
                      <BotonDetalle onAbrir={() => setAbierta(e.id)} etiqueta={`Ver actividad de ${e.tarea} en ${e.servidor}`}>
                        {e.tarea}
                      </BotonDetalle>
                      <span className="block text-xs text-ink-faint">
                        {e.script} v{e.numeroVersion} · {e.sistemaOperativo}
                      </span>
                      {e.estado === 'FALLIDA' && (
                        <span className="mt-1 block text-xs text-danger">{diagnosticarFallo(e.error).titulo}</span>
                      )}
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
                        className="font-mono text-accent hover:underline"
                      >
                        {e.orden}
                      </Link>
                    </td>
                  </FilaDetalle>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion
            pagina={pagina}
            totalPaginas={ejecuciones.datos?.totalPaginas ?? 0}
            totalElementos={ejecuciones.datos?.totalElementos ?? 0}
            onCambiar={setPagina}
            tamano={tamano}
            onTamano={setTamano}
          />
        </div>
      )}

      {abierta != null && <DetalleEjecucion id={abierta} onCerrar={() => setAbierta(undefined)} />}
    </>
  )
}
