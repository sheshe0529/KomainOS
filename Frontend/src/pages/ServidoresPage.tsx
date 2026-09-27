import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowDown, ArrowUp, ArrowUpDown, Download, Eye, Pencil, Plus, Search, Upload } from 'lucide-react'
import { servidoresApi, type FiltroServidores } from '@/api/inventario'
import type { EstadoServidor } from '@/api/dominio'
import type { FichaServidorRespuesta } from '@/api/types'
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
import { StatusPill } from '@/components/ui/StatusPill'
import { useCatalogos, useResponsables } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'
import { ESTADO_SERVIDOR } from '@/utils/etiquetas'

const FILTROS_ESTADO: { valor: EstadoServidor | ''; etiqueta: string }[] = [
  { valor: '', etiqueta: 'Todos' },
  { valor: 'ACTIVO', etiqueta: 'Activos' },
  { valor: 'PENDIENTE_DE_CONFIGURACION', etiqueta: 'Pendientes de configuración' },
  { valor: 'DADO_DE_BAJA', etiqueta: 'Dados de baja' },
]

/** Columnas ordenables: propiedad del backend para el parámetro sort. */
const ORDEN = {
  hostname: 'hostname',
  datacenter: 'datacenter',
  entorno: 'entorno.nombre',
  criticidad: 'nivelCriticidad.prioridad',
  estado: 'estado',
  responsable: 'responsable.nombreCompleto',
} as const

type ColumnaOrden = keyof typeof ORDEN

/** Inventario de servidores (RF11, HU07). */
export function ServidoresPage() {
  const tonoCriticidad = useTonoCriticidad()
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()

  const [texto, setTexto] = useState('')
  const [textoAplicado, setTextoAplicado] = useState('')
  const [filtro, setFiltro] = useState<FiltroServidores>({ estado: '', pagina: 0, tamano: 20 })
  const [orden, setOrden] = useState<{ columna: ColumnaOrden; asc: boolean }>({ columna: 'hostname', asc: true })
  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [dialogo, setDialogo] = useState<'importar' | 'exportar' | null>(null)

  // La búsqueda se aplica al dejar de escribir, para no consultar por cada tecla.
  useEffect(() => {
    const t = window.setTimeout(() => {
      setTextoAplicado(texto)
      setFiltro((f) => ({ ...f, pagina: 0 }))
    }, 350)
    return () => window.clearTimeout(t)
  }, [texto])

  const catalogos = useCatalogos()
  const responsables = useResponsables(esAdmin)
  const servidores = useConsulta(
    () =>
      servidoresApi.listar({
        ...filtro,
        texto: textoAplicado,
        orden: `${ORDEN[orden.columna]},${orden.asc ? 'asc' : 'desc'}`,
      }),
    [filtro, textoAplicado, orden],
  )

  function actualizarFiltro(cambios: Partial<FiltroServidores>) {
    setFiltro((f) => ({ ...f, ...cambios, pagina: 0 }))
  }

  function ordenarPor(columna: ColumnaOrden) {
    setOrden((o) => ({ columna, asc: o.columna === columna ? !o.asc : true }))
  }

  function alGuardar(servidor: FichaServidorRespuesta) {
    setFormularioAbierto(false)
    avisar(`Servidor ${servidor.hostname} registrado. Complete su configuración de mantenimiento.`)
    navigate(`/servidores/${servidor.id}`)
  }

  const encabezado = (columna: ColumnaOrden, etiqueta: string) => {
    const activa = orden.columna === columna
    const Icono = !activa ? ArrowUpDown : orden.asc ? ArrowUp : ArrowDown
    return (
      <th className="px-4 py-3 font-medium">
        <button type="button" onClick={() => ordenarPor(columna)} className="inline-flex items-center gap-1 uppercase hover:text-ink">
          {etiqueta}
          <Icono className={`h-3 w-3 ${activa ? 'text-accent' : ''}`} aria-hidden="true" />
        </button>
      </th>
    )
  }

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
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder="Buscar por host, IP, DNS, datacenter o responsable"
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
        </div>

        {/* HU07 CA5: distingue activos, pendientes de configuración y dados de baja. */}
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
          <table className="w-full min-w-[960px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                {encabezado('hostname', 'Servidor')}
                {encabezado('datacenter', 'Datacenter')}
                <th className="px-4 py-3 font-medium">Sistema operativo</th>
                {encabezado('entorno', 'Entorno')}
                {encabezado('criticidad', 'Criticidad')}
                {encabezado('estado', 'Estado')}
                {encabezado('responsable', 'Responsable')}
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {servidores.cargando && !pagina ? (
                <tr>
                  <td colSpan={8}>
                    <Cargando texto="Cargando inventario…" />
                  </td>
                </tr>
              ) : (
                pagina?.contenido?.map((s) => {
                  const estado = s.estado ? ESTADO_SERVIDOR[s.estado] : undefined
                  return (
                    <tr key={s.id} className="transition-colors hover:bg-panel-muted">
                      <td className="px-4 py-3">
                        <Link to={`/servidores/${s.id}`} className="font-mono text-sm font-medium text-ink hover:text-accent">
                          {s.hostname}
                        </Link>
                        <p className="font-mono text-xs text-ink-faint">{s.direccionIp}</p>
                      </td>
                      <td className="px-4 py-3">
                        <p className="text-ink">{s.datacenter ?? '—'}</p>
                        <p className="text-xs text-ink-faint">{s.servidorFisico ?? ''}</p>
                      </td>
                      <td className="px-4 py-3 text-ink-soft">{s.versionSistemaOperativo?.nombre}</td>
                      <td className="px-4 py-3 text-ink-soft">{s.entorno?.nombre}</td>
                      <td className="px-4 py-3">
                        <StatusPill tone={tonoCriticidad(s.criticidad?.prioridad)} label={s.criticidad?.nombre ?? '—'} />
                      </td>
                      <td className="px-4 py-3">{estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}</td>
                      <td className="px-4 py-3 text-ink-soft">{s.responsable?.nombre}</td>
                      <td className="px-4 py-3">
                        <div className="flex justify-end gap-1">
                          <BotonIcono icono={Eye} etiqueta="Ver ficha" onClick={() => navigate(`/servidores/${s.id}`)} />
                          {esAdmin && s.estado !== 'DADO_DE_BAJA' && (
                            <BotonIcono
                              icono={Pencil}
                              etiqueta="Editar o configurar"
                              onClick={() => navigate(`/servidores/${s.id}`)}
                            />
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })
              )}
              {!servidores.cargando && pagina?.contenido?.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-10 text-center text-sm text-ink-soft">
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
