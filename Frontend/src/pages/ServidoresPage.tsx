import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowDown, ArrowUp, ArrowUpDown, Download, Eye, Pencil, Plus, Search, Upload } from 'lucide-react'
import { servidoresApi, type FiltroServidores } from '@/api/inventario'
import type { EstadoServidor } from '@/api/dominio'
import type { FichaServidorRespuesta, ServidorResumenRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { ExportarModal } from '@/components/inventario/ExportarModal'
import { ImportarModal } from '@/components/inventario/ImportarModal'
import { ServidorFormulario } from '@/components/inventario/ServidorFormulario'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { SelectorColumnas } from '@/components/ui/SelectorColumnas'
import { StatusPill, type StatusTone } from '@/components/ui/StatusPill'
import { useCatalogos, useResponsables } from '@/hooks/useCatalogos'
import { useColumnasVisibles, type DefinicionColumna } from '@/hooks/useColumnasVisibles'
import { useConsulta } from '@/hooks/useConsulta'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'
import { ESTADO_SERVIDOR } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'

const FILTROS_ESTADO: { valor: EstadoServidor | ''; etiqueta: string }[] = [
  { valor: '', etiqueta: 'Todos' },
  { valor: 'ACTIVO', etiqueta: 'Activos' },
  { valor: 'PENDIENTE_DE_CONFIGURACION', etiqueta: 'Pendientes de configuración' },
  { valor: 'DADO_DE_BAJA', etiqueta: 'Dados de baja' },
]

interface Contexto {
  tonoCriticidad: (prioridad?: number) => StatusTone
  ve: (id: string) => boolean
}

interface ColumnaServidor extends DefinicionColumna {
  /** Propiedad del backend para el parámetro sort, sin ella la columna no se ordena */
  orden?: string
  celda: (s: ServidorResumenRespuesta, ctx: Contexto) => ReactNode
}

const texto = (valor?: string) => <span className="text-ink-soft">{valor ?? '—'}</span>

const recurso = (valor?: number, unidad?: string) => (
  <span className="whitespace-nowrap font-mono text-xs tabular-nums text-ink-soft">
    {valor === undefined || valor === null ? '—' : `${valor.toLocaleString('es-PE')}${unidad ? ` ${unidad}` : ''}`}
  </span>
)

/** Las ocultas por defecto se agregan desde «Columnas», servidor y acciones siempre se muestran (DEC-32) */
const COLUMNAS: ColumnaServidor[] = [
  {
    id: 'servidor',
    etiqueta: 'Servidor',
    fija: true,
    orden: 'hostname',
    celda: (s) => (
      <>
        <Link to={`/servidores/${s.id}`} className="font-mono text-sm font-medium text-ink hover:text-accent">
          {s.hostname}
        </Link>
        <p className="font-mono text-xs text-ink-faint">
          {s.direccionIp}
          {(s.cantidadDireccionesIp ?? 1) > 1 && (
            <span
              className="ml-1.5 rounded bg-panel-muted px-1 font-sans text-[10px] text-ink-soft"
              title={`${(s.cantidadDireccionesIp ?? 1) - 1} dirección(es) IP adicional(es); ver la ficha`}
            >
              +{(s.cantidadDireccionesIp ?? 1) - 1}
            </span>
          )}
        </p>
      </>
    ),
  },
  {
    id: 'descripcion',
    etiqueta: 'Descripción',
    celda: (s) =>
      s.descripcion ? (
        // Se recorta a dos líneas, el texto completo se ve al pasar el cursor y en la ficha
        <p className="line-clamp-2 max-w-xs text-ink-soft" title={s.descripcion}>
          {s.descripcion}
        </p>
      ) : (
        <span className="text-ink-faint">—</span>
      ),
  },
  {
    id: 'vdc',
    etiqueta: 'VDC',
    orden: 'vdc',
    celda: (s, { ve }) => (
      <>
        <p className="text-ink">{s.vdc ?? '—'}</p>
        {/* Si el servidor físico no tiene columna propia, se muestra aquí */}
        {!ve('servidorFisico') && <p className="text-xs text-ink-faint">{s.servidorFisico ?? ''}</p>}
      </>
    ),
  },
  { id: 'servidorFisico', etiqueta: 'Servidor físico', ocultaPorDefecto: true, orden: 'servidorFisico', celda: (s) => texto(s.servidorFisico) },
  { id: 'cluster', etiqueta: 'Clúster', ocultaPorDefecto: true, orden: 'cluster', celda: (s) => texto(s.cluster) },
  { id: 'vlan', etiqueta: 'VLAN', ocultaPorDefecto: true, orden: 'vlan', celda: (s) => texto(s.vlan) },
  {
    id: 'dns',
    etiqueta: 'DNS',
    ocultaPorDefecto: true,
    orden: 'dns',
    celda: (s) => <span className="font-mono text-xs text-ink-soft">{s.dns ?? '—'}</span>,
  },
  { id: 'plataforma', etiqueta: 'Plataforma', ocultaPorDefecto: true, orden: 'plataforma', celda: (s) => texto(s.plataforma) },
  { id: 'sistemaOperativo', etiqueta: 'Sistema operativo', celda: (s) => texto(s.versionSistemaOperativo?.nombre) },
  { id: 'cpu', etiqueta: 'CPU', ocultaPorDefecto: true, orden: 'cantidadCpu', celda: (s) => recurso(s.cantidadCpu) },
  { id: 'ram', etiqueta: 'RAM', ocultaPorDefecto: true, orden: 'ramGb', celda: (s) => recurso(s.ramGb, 'GB') },
  { id: 'disco', etiqueta: 'Disco', ocultaPorDefecto: true, orden: 'hdVirtualGb', celda: (s) => recurso(s.hdVirtualGb, 'GB') },
  { id: 'entorno', etiqueta: 'Entorno', orden: 'entorno.nombre', celda: (s) => texto(s.entorno?.nombre) },
  {
    id: 'criticidad',
    etiqueta: 'Criticidad',
    orden: 'nivelCriticidad.prioridad',
    celda: (s, { tonoCriticidad }) => (
      <StatusPill tone={tonoCriticidad(s.criticidad?.prioridad)} label={s.criticidad?.nombre ?? '—'} />
    ),
  },
  {
    id: 'estado',
    etiqueta: 'Estado',
    orden: 'estado',
    celda: (s) => {
      const estado = s.estado ? ESTADO_SERVIDOR[s.estado] : undefined
      return estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />
    },
  },
  { id: 'responsable', etiqueta: 'Responsable', orden: 'responsable.nombreCompleto', celda: (s) => texto(s.responsable?.nombre) },
  {
    id: 'fechaAlta',
    etiqueta: 'Alta en inventario',
    ocultaPorDefecto: true,
    orden: 'fechaAlta',
    celda: (s) => <span className="whitespace-nowrap text-ink-soft">{formatearFechaHora(s.fechaAlta)}</span>,
  },
  {
    id: 'fechaActualizacion',
    etiqueta: 'Última actualización',
    ocultaPorDefecto: true,
    orden: 'fechaActualizacion',
    celda: (s) => <span className="whitespace-nowrap text-ink-soft">{formatearFechaHora(s.fechaActualizacion)}</span>,
  },
]

/** La columna de acciones es fija: se cuenta en el total, pero no se puede ocultar */
const DEFINICIONES: DefinicionColumna[] = [...COLUMNAS, { id: 'acciones', etiqueta: 'Acciones', fija: true }]

export function ServidoresPage() {
  const tonoCriticidad = useTonoCriticidad()
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()
  const columnas = useColumnasVisibles('servidores', DEFINICIONES)

  const [busqueda, setBusqueda] = useState('')
  const [textoAplicado, setTextoAplicado] = useState('')
  const [filtro, setFiltro] = useState<FiltroServidores>({ estado: '', pagina: 0, tamano: 20 })
  const [orden, setOrden] = useState<{ propiedad: string; asc: boolean }>({ propiedad: 'hostname', asc: true })
  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [dialogo, setDialogo] = useState<'importar' | 'exportar' | null>(null)

  // La búsqueda se aplica al dejar de escribir, para no consultar por cada tecla
  useEffect(() => {
    const t = window.setTimeout(() => {
      setTextoAplicado(busqueda)
      setFiltro((f) => ({ ...f, pagina: 0 }))
    }, 350)
    return () => window.clearTimeout(t)
  }, [busqueda])

  const catalogos = useCatalogos()
  const responsables = useResponsables(esAdmin)
  const servidores = useConsulta(
    () =>
      servidoresApi.listar({
        ...filtro,
        texto: textoAplicado,
        orden: `${orden.propiedad},${orden.asc ? 'asc' : 'desc'}`,
      }),
    [filtro, textoAplicado, orden],
  )

  function actualizarFiltro(cambios: Partial<FiltroServidores>) {
    setFiltro((f) => ({ ...f, ...cambios, pagina: 0 }))
  }

  function ordenarPor(propiedad: string) {
    setOrden((o) => ({ propiedad, asc: o.propiedad === propiedad ? !o.asc : true }))
  }

  function alGuardar(servidor: FichaServidorRespuesta) {
    setFormularioAbierto(false)
    avisar(`Servidor ${servidor.hostname} registrado. Complete su configuración de mantenimiento.`)
    navigate(`/servidores/${servidor.id}`)
  }

  const encabezado = (c: ColumnaServidor) => {
    if (!c.orden) {
      return (
        <th key={c.id} className="px-4 py-3 font-medium">
          {c.etiqueta}
        </th>
      )
    }
    const activa = orden.propiedad === c.orden
    const Icono = !activa ? ArrowUpDown : orden.asc ? ArrowUp : ArrowDown
    return (
      <th key={c.id} className="px-4 py-3 font-medium" aria-sort={activa ? (orden.asc ? 'ascending' : 'descending') : undefined}>
        <button type="button" onClick={() => ordenarPor(c.orden!)} className="inline-flex items-center gap-1 whitespace-nowrap uppercase hover:text-ink">
          {c.etiqueta}
          <Icono className={`h-3 w-3 ${activa ? 'text-accent' : ''}`} aria-hidden="true" />
        </button>
      </th>
    )
  }

  const visibles = COLUMNAS.filter((c) => columnas.ve(c.id))
  const contexto: Contexto = { tonoCriticidad, ve: columnas.ve }
  const totalColumnas = visibles.length + 1
  const pagina = servidores.datos
  const total = pagina?.totalElementos ?? 0

  return (
    <>
      <PageHeader
        title="Inventario de servidores"
        description={`${total} servidores virtuales bajo gestión`}
        actions={
          <>
            <Boton icono={Download} onClick={() => setDialogo('exportar')}>
              Exportar
            </Boton>
            {esAdmin && (
              <>
                <Boton icono={Upload} onClick={() => setDialogo('importar')}>
                  Importar
                </Boton>
                <Boton variante="primario" icono={Plus} onClick={() => setFormularioAbierto(true)} disabled={!catalogos.datos}>
                  Registrar servidor
                </Boton>
              </>
            )}
          </>
        }
      />

      <div className="mb-4 flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex min-w-64 flex-1 items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2 sm:max-w-md">
            <Search className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
            <input
              type="search"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              placeholder="Buscar por host, IP, DNS, VDC o responsable"
              aria-label="Buscar servidores"
              className="w-full bg-transparent text-sm text-ink placeholder:text-ink-faint focus:outline-none"
            />
          </div>
          <Selector
            aria-label="Filtrar por entorno"
            className="!w-auto"
            value={filtro.idEntorno ?? ''}
            onChange={(e) => actualizarFiltro({ idEntorno: e.target.value ? Number(e.target.value) : '' })}
          >
            <option value="">Todos los entornos</option>
            {catalogos.datos?.entornos.map((e) => (
              <option key={e.id} value={e.id}>
                {e.nombre}
              </option>
            ))}
          </Selector>
          <Selector
            aria-label="Filtrar por criticidad"
            className="!w-auto"
            value={filtro.idNivelCriticidad ?? ''}
            onChange={(e) => actualizarFiltro({ idNivelCriticidad: e.target.value ? Number(e.target.value) : '' })}
          >
            <option value="">Todas las criticidades</option>
            {catalogos.datos?.criticidades.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nombre}
              </option>
            ))}
          </Selector>
          <Selector
            aria-label="Filtrar por sistema operativo"
            className="!w-auto"
            value={filtro.idSistemaOperativo ?? ''}
            onChange={(e) => actualizarFiltro({ idSistemaOperativo: e.target.value ? Number(e.target.value) : '' })}
          >
            <option value="">Todos los sistemas</option>
            {catalogos.datos?.sistemasOperativos.map((so) => (
              <option key={so.id} value={so.id}>
                {so.nombre}
              </option>
            ))}
          </Selector>
          <div className="ml-auto">
            <SelectorColumnas {...columnas} />
          </div>
        </div>

        {/* HU07 CA5: distingue activos, pendientes de configuración y dados de baja */}
        <div className="flex flex-wrap gap-2" role="group" aria-label="Filtrar por estado">
          {FILTROS_ESTADO.map((f) => (
            <button
              key={f.etiqueta}
              type="button"
              aria-pressed={filtro.estado === f.valor}
              onClick={() => actualizarFiltro({ estado: f.valor })}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                filtro.estado === f.valor
                  ? 'bg-accent text-accent-ink'
                  : 'border border-line bg-panel text-ink-soft hover:bg-panel-muted hover:text-ink'
              }`}
            >
              {f.etiqueta}
            </button>
          ))}
        </div>
      </div>

      <MensajeError error={servidores.error} onReintentar={servidores.recargar} />

      <div className="overflow-hidden rounded-xl border border-line bg-panel">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm" style={{ minWidth: `${Math.max(640, totalColumnas * 125)}px` }}>
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                {visibles.map(encabezado)}
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {servidores.cargando && !pagina ? (
                <tr>
                  <td colSpan={totalColumnas}>
                    <Cargando texto="Cargando inventario…" />
                  </td>
                </tr>
              ) : (
                pagina?.contenido?.map((s) => (
                  <tr key={s.id} className="transition-colors hover:bg-panel-muted">
                    {visibles.map((c) => (
                      <td key={c.id} className="px-4 py-3">
                        {c.celda(s, contexto)}
                      </td>
                    ))}
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1">
                        <BotonIcono icono={Eye} etiqueta="Ver ficha" onClick={() => navigate(`/servidores/${s.id}`)} />
                        {esAdmin && s.estado !== 'DADO_DE_BAJA' && (
                          <BotonIcono icono={Pencil} etiqueta="Editar o configurar" onClick={() => navigate(`/servidores/${s.id}`)} />
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
              {!servidores.cargando && pagina?.contenido?.length === 0 && (
                <tr>
                  <td colSpan={totalColumnas} className="px-4 py-10 text-center text-sm text-ink-soft">
                    {textoAplicado || filtro.estado || filtro.idEntorno || filtro.idNivelCriticidad || filtro.idSistemaOperativo
                      ? 'No se encontraron servidores con los filtros aplicados.'
                      : 'Aún no hay servidores registrados en el inventario.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <Paginacion
          pagina={pagina?.pagina ?? 0}
          totalPaginas={pagina?.totalPaginas ?? 0}
          totalElementos={total}
          onCambiar={(p) => setFiltro((f) => ({ ...f, pagina: p }))}
        />
      </div>

      {formularioAbierto && catalogos.datos && (
        <ServidorFormulario
          abierto
          onCerrar={() => setFormularioAbierto(false)}
          onGuardado={alGuardar}
          catalogos={catalogos.datos}
          responsables={responsables.datos ?? []}
        />
      )}
      {dialogo === 'exportar' && (
        <ExportarModal abierto filtro={{ ...filtro, texto: textoAplicado }} total={total} onCerrar={() => setDialogo(null)} />
      )}
      {dialogo === 'importar' && (
        <ImportarModal abierto onCerrar={() => setDialogo(null)} onImportado={servidores.recargar} />
      )}
    </>
  )
}
