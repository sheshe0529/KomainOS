import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'
import type { EstadoOrden } from '@/api/dominio'
import { planificacionApi, type FiltroOrdenes } from '@/api/planificacion'
import { PageHeader } from '@/components/common/PageHeader'
import { OrdenesTabla } from '@/components/planificacion/OrdenesTabla'
import { Entrada, Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { ESTADO_ORDEN } from '@/utils/etiquetas'

/** Consulta de órdenes y su detalle (RF36, HU23). */
export function OrdenesPage() {
  const [codigo, setCodigo] = useState('')
  const [filtro, setFiltro] = useState<FiltroOrdenes>({ estado: '', pagina: 0, tamano: 20 })
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const catalogos = useCatalogos()

  useEffect(() => {
    const t = window.setTimeout(() => setFiltro((f) => ({ ...f, codigo: codigo.trim() || undefined, pagina: 0 })), 350)
    return () => window.clearTimeout(t)
  }, [codigo])

  const ordenes = useConsulta(
    () =>
      planificacionApi.ordenes({
        ...filtro,
        desde: desde ? new Date(`${desde}T00:00`).toISOString() : undefined,
        hasta: hasta ? new Date(`${hasta}T23:59:59`).toISOString() : undefined,
      }),
    [filtro, desde, hasta],
  )

  const pagina = ordenes.datos

  return (
    <>
      <PageHeader title="Órdenes de mantenimiento" description="Consulte órdenes por código, estado, criticidad y fechas programadas." />

      <div className="mb-4 flex flex-wrap items-end gap-2">
        <div className="flex min-w-56 flex-1 items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2 sm:max-w-xs">
          <Search className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
          <input
            type="search"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value)}
            placeholder="Código (OM-2026-…)"
            aria-label="Buscar por código"
            className="w-full bg-transparent text-sm text-ink placeholder:text-ink-faint focus:outline-none"
          />
        </div>
        <Selector
          aria-label="Estado"
          className="!w-auto"
          value={filtro.estado ?? ''}
          onChange={(e) => setFiltro((f) => ({ ...f, estado: e.target.value as EstadoOrden | '', pagina: 0 }))}
        >
          <option value="">Todos los estados</option>
          {(Object.keys(ESTADO_ORDEN) as EstadoOrden[]).map((e) => (
            <option key={e} value={e}>
              {ESTADO_ORDEN[e].etiqueta}
            </option>
          ))}
        </Selector>
        <Selector
          aria-label="Criticidad"
          className="!w-auto"
          value={filtro.idNivelCriticidad ?? ''}
          onChange={(e) => setFiltro((f) => ({ ...f, idNivelCriticidad: e.target.value ? Number(e.target.value) : '', pagina: 0 }))}
        >
          <option value="">Todas las criticidades</option>
          {catalogos.datos?.criticidades.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nombre}
            </option>
          ))}
        </Selector>
        <label className="flex items-center gap-2 text-sm text-ink-soft">
          Desde
          <Entrada type="date" className="!w-auto" value={desde} onChange={(e) => setDesde(e.target.value)} />
        </label>
        <label className="flex items-center gap-2 text-sm text-ink-soft">
          Hasta
          <Entrada type="date" className="!w-auto" value={hasta} onChange={(e) => setHasta(e.target.value)} />
        </label>
      </div>

      <MensajeError error={ordenes.error} onReintentar={ordenes.recargar} />

      <div className="overflow-hidden rounded-xl border border-line bg-panel">
        {ordenes.cargando && !pagina ? (
          <Cargando />
        ) : (
          <OrdenesTabla ordenes={pagina?.contenido ?? []} vacio="No se encontraron órdenes con los filtros aplicados." />
        )}
        <Paginacion
          pagina={pagina?.pagina ?? 0}
          totalPaginas={pagina?.totalPaginas ?? 0}
          totalElementos={pagina?.totalElementos ?? 0}
          onCambiar={(p) => setFiltro((f) => ({ ...f, pagina: p }))}
        />
      </div>
    </>
  )
}
