import { Search } from 'lucide-react'
import type { EstadoServidor } from '@/api/dominio'
import type { FiltroServidores as Filtro } from '@/api/inventario'
import { Selector } from '@/components/ui/Campo'
import { SelectorColumnas } from '@/components/ui/SelectorColumnas'
import type { Catalogos } from '@/hooks/useCatalogos'
import type { ColumnasVisibles } from '@/hooks/useColumnasVisibles'

const FILTROS_ESTADO: { valor: EstadoServidor | ''; etiqueta: string }[] = [
  { valor: '', etiqueta: 'Todos' },
  { valor: 'ACTIVO', etiqueta: 'Activos' },
  { valor: 'PENDIENTE_DE_CONFIGURACION', etiqueta: 'Pendientes de configuración' },
  { valor: 'DADO_DE_BAJA', etiqueta: 'Dados de baja' },
]

interface FiltrosServidoresProps {
  busqueda: string
  onBuscar: (texto: string) => void
  filtro: Filtro
  onFiltrar: (cambios: Partial<Filtro>) => void
  catalogos?: Catalogos
  columnas: ColumnasVisibles
}

export function FiltrosServidores({ busqueda, onBuscar, filtro, onFiltrar, catalogos, columnas }: FiltrosServidoresProps) {
  const numero = (valor: string) => (valor ? Number(valor) : '')

  return (
    <div className="mb-4 flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="flex min-w-64 flex-1 items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2 sm:max-w-md">
          <Search className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
          {/* Sin autocompletar: el navegador no debe tomarlo por un campo de usuario */}
          <input
            type="search"
            name="busqueda-inventario"
            autoComplete="off"
            value={busqueda}
            onChange={(e) => onBuscar(e.target.value)}
            placeholder="Buscar por host, IP, DNS, VDC o responsable"
            aria-label="Buscar servidores"
            className="w-full bg-transparent text-sm text-ink placeholder:text-ink-faint focus:outline-none"
          />
        </div>
        <Selector
          aria-label="Filtrar por entorno"
          className="!w-auto"
          value={filtro.idEntorno ?? ''}
          onChange={(e) => onFiltrar({ idEntorno: numero(e.target.value) })}
        >
          <option value="">Todos los entornos</option>
          {catalogos?.entornos.map((e) => (
            <option key={e.id} value={e.id}>
              {e.nombre}
            </option>
          ))}
        </Selector>
        <Selector
          aria-label="Filtrar por criticidad"
          className="!w-auto"
          value={filtro.idNivelCriticidad ?? ''}
          onChange={(e) => onFiltrar({ idNivelCriticidad: numero(e.target.value) })}
        >
          <option value="">Todas las criticidades</option>
          {catalogos?.criticidades.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nombre}
            </option>
          ))}
        </Selector>
        <Selector
          aria-label="Filtrar por sistema operativo"
          className="!w-auto"
          value={filtro.idSistemaOperativo ?? ''}
          onChange={(e) => onFiltrar({ idSistemaOperativo: numero(e.target.value) })}
        >
          <option value="">Todos los sistemas</option>
          {catalogos?.sistemasOperativos.map((so) => (
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
            onClick={() => onFiltrar({ estado: f.valor })}
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
  )
}
