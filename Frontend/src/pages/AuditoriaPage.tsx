import { useState } from 'react'
import { Link } from 'react-router-dom'
import { RefreshCw } from 'lucide-react'
import { auditoriaApi } from '@/api/auditoria'
import type { FiltroAuditoria } from '@/api/auditoria'
import type { AuditoriaRespuesta } from '@/api/types'
import { PageHeader } from '@/components/common/PageHeader'
import { Boton } from '@/components/ui/Boton'
import { Campo } from '@/components/ui/Campo'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { Selector } from '@/components/ui/Selector'
import { SelectorFecha } from '@/components/ui/SelectorFecha'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacion } from '@/hooks/usePaginacion'
import { useTextoDiferido } from '@/hooks/useTextoDiferido'
import { formatearFechaHora } from '@/utils/formato'

function etiqueta(valor = '—') {
  const texto = valor.replaceAll('_', ' ').toLocaleLowerCase('es-PE')
  return texto.charAt(0).toLocaleUpperCase('es-PE') + texto.slice(1)
}

function ValorHistorico({ titulo, valor }: { titulo: string; valor?: unknown }) {
  return (
    <section className="min-w-0">
      <h4 className="mb-2 text-sm font-medium">{titulo}</h4>
      <pre className="max-h-80 overflow-auto rounded-lg border border-line bg-panel-muted p-3 text-xs whitespace-pre-wrap break-words">
        {valor == null ? 'Sin valor registrado' : JSON.stringify(valor, null, 2)}
      </pre>
    </section>
  )
}

/** RNF06: consulta administrativa de solo lectura, con filtros aplicados y paginados por el backend. */
export function AuditoriaPage() {
  const [busqueda, setBusqueda] = useState('')
  const texto = useTextoDiferido(busqueda)
  const [actor, setActor] = useState('')
  const [operacion, setOperacion] = useState('')
  const [entidad, setEntidad] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [version, setVersion] = useState(0)
  const [abierto, setAbierto] = useState<AuditoriaRespuesta>()
  const { pagina, tamano, setPagina, setTamano } = usePaginacion('auditoria')
  const opciones = useConsulta(() => auditoriaApi.filtros(), [version])
  const fin = hasta ? new Date(`${hasta}T00:00:00`) : undefined
  fin?.setDate(fin.getDate() + 1)
  const filtro: FiltroAuditoria = {
    texto,
    idUsuario: actor && actor !== 'sistema' ? Number(actor) : undefined,
    sistema: actor === 'sistema' ? true : undefined,
    operacion,
    entidad,
    desde: desde ? new Date(`${desde}T00:00:00`).toISOString() : undefined,
    hasta: fin?.toISOString(),
  }
  const clave = JSON.stringify(filtro)
  const informe = useConsulta(() => auditoriaApi.listar(filtro, pagina, tamano), [clave, pagina, tamano, version])
  const filas = informe.datos?.contenido ?? []

  const cambiar = (fijar: (v: string) => void) => (v: string) => {
    fijar(v)
    setPagina(0)
  }

  function limpiar() {
    setBusqueda('')
    setActor('')
    setOperacion('')
    setEntidad('')
    setDesde('')
    setHasta('')
    setPagina(0)
  }

  return (
    <>
      <PageHeader title="Auditoría" description="Cambios registrados en el sistema: quién los hizo, cuándo y sobre qué registro."
        actions={<Boton icono={RefreshCw} onClick={() => setVersion((v) => v + 1)} cargando={informe.cargando}>Actualizar</Boton>} />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <CampoBusqueda valor={busqueda} onCambiar={cambiar(setBusqueda)} aria-label="Buscar en auditoría"
          placeholder="Buscar actor, operación, motivo o identificador" />
        <Campo etiqueta="Actor" className="w-60">
          <Selector value={actor} onChange={(e) => cambiar(setActor)(e.target.value)}>
            <option value="">Todos los actores</option>
            <option value="sistema">Sistema (procesos automáticos)</option>
            {opciones.datos?.usuarios?.map((u) => <option key={u.id} value={u.id}>{u.nombre} · {u.codigo}</option>)}
          </Selector>
        </Campo>
        <Campo etiqueta="Operación" className="w-60">
          <Selector value={operacion} onChange={(e) => cambiar(setOperacion)(e.target.value)}>
            <option value="">Todas las operaciones</option>
            {opciones.datos?.operaciones?.map((o) => <option key={o} value={o}>{etiqueta(o)}</option>)}
          </Selector>
        </Campo>
        <Campo etiqueta="Entidad" className="w-52">
          <Selector value={entidad} onChange={(e) => cambiar(setEntidad)(e.target.value)}>
            <option value="">Todas las entidades</option>
            {opciones.datos?.entidades?.map((e) => <option key={e} value={e}>{etiqueta(e)}</option>)}
          </Selector>
        </Campo>
        <Campo etiqueta="Desde" className="w-44">
          <SelectorFecha value={desde} max={hasta || undefined} onChange={cambiar(setDesde)} aria-label="Desde" />
        </Campo>
        <Campo etiqueta="Hasta (inclusive)" className="w-44">
          <SelectorFecha value={hasta} min={desde || undefined} onChange={cambiar(setHasta)} aria-label="Hasta" />
        </Campo>
        <Boton onClick={limpiar}>Limpiar filtros</Boton>
      </div>
      <MensajeError error={informe.error ?? opciones.error} />
      {informe.cargando && !informe.datos && <Cargando texto="Consultando eventos…" />}
      {!informe.cargando && !informe.error && filas.length === 0 && (
        <p className="rounded-xl border border-line bg-panel p-8 text-center text-sm text-ink-soft">No hay eventos para estos filtros.</p>
      )}
      {filas.length > 0 && (
        <div className="overflow-hidden rounded-xl border border-line bg-panel">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[980px] text-left text-sm">
              <thead><tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                {['Fecha y hora', 'Actor', 'Operación', 'Registro', 'Motivo'].map((t) => <th key={t} className="px-4 py-3 font-medium">{t}</th>)}
              </tr></thead>
              <tbody className="divide-y divide-line">{filas.map((e) => (
                <tr key={e.id} onClick={() => setAbierto(e)} className="cursor-pointer align-top hover:bg-panel-muted">
                  <td className="px-4 py-3 whitespace-nowrap text-ink-soft">{formatearFechaHora(e.fechaHora)}</td>
                  <td className="px-4 py-3">{e.actor}<span className="block text-xs text-ink-faint">{e.codigoUsuario ?? e.proceso}</span></td>
                  <td className="px-4 py-3"><button type="button" className="text-left text-accent hover:underline" onClick={() => setAbierto(e)}>{etiqueta(e.operacion)}</button></td>
                  <td className="px-4 py-3">{etiqueta(e.entidad)}{e.idEntidad != null && <span className="ml-1 font-mono text-ink-soft">#{e.idEntidad}</span>}</td>
                  <td className="max-w-sm px-4 py-3 text-ink-soft"><span className="line-clamp-2">{e.motivo ?? '—'}</span></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          <Paginacion pagina={pagina} tamano={tamano} totalPaginas={informe.datos?.totalPaginas ?? 0}
            totalElementos={informe.datos?.totalElementos ?? 0} onCambiar={setPagina} onTamano={setTamano} />
        </div>
      )}
      <p className="mt-4 text-xs text-ink-faint">Los eventos se conservan como historial. Los procesos automáticos aparecen como Sistema; esta vista no permite modificar ni eliminar registros.</p>
      {abierto && <Modal abierto titulo={etiqueta(abierto.operacion)} descripcion={`Evento #${abierto.id} · ${formatearFechaHora(abierto.fechaHora)}`}
        ancho="xl" onCerrar={() => setAbierto(undefined)}>
        <dl className="mb-5 grid gap-3 text-sm sm:grid-cols-2">
          <div><dt className="text-ink-faint">Actor</dt><dd>{abierto.actor} · {abierto.codigoUsuario ?? abierto.proceso}</dd></div>
          <div><dt className="text-ink-faint">Registro</dt><dd>{etiqueta(abierto.entidad)} {abierto.idEntidad != null ? `#${abierto.idEntidad}` : ''}</dd></div>
          <div className="sm:col-span-2"><dt className="text-ink-faint">Motivo</dt><dd className="whitespace-pre-wrap">{abierto.motivo ?? 'Sin motivo adicional'}</dd></div>
        </dl>
        <div className="mb-5 flex flex-wrap gap-4 text-sm text-accent">
          {abierto.idServidor != null && <Link to={`/servidores/${abierto.idServidor}`}>Servidor #{abierto.idServidor}</Link>}
          {abierto.idGrupo != null && <Link to={`/grupos/${abierto.idGrupo}`}>Grupo #{abierto.idGrupo}</Link>}
          {abierto.idOrden != null && <Link to={`/ordenes/${abierto.idOrden}`}>Orden #{abierto.idOrden}</Link>}
        </div>
        <div className="grid gap-4 md:grid-cols-2"><ValorHistorico titulo="Antes" valor={abierto.valorAnterior} /><ValorHistorico titulo="Después" valor={abierto.valorNuevo} /></div>
      </Modal>}
    </>
  )
}
