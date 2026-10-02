import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { ServidorResumenRespuesta } from '@/api/types'
import { StatusPill, type StatusTone } from '@/components/ui/StatusPill'
import type { DefinicionColumna } from '@/hooks/useColumnasVisibles'
import { ESTADO_SERVIDOR } from '@/utils/etiquetas'
import { formatearFechaHora } from '@/utils/formato'

export interface ContextoColumna {
  tonoCriticidad: (prioridad?: number) => StatusTone
  ve: (id: string) => boolean
}

export interface ColumnaServidor extends DefinicionColumna {
  /** Propiedad del backend para el parámetro sort, sin ella la columna no se ordena */
  orden?: string
  celda: (s: ServidorResumenRespuesta, ctx: ContextoColumna) => ReactNode
}

const texto = (valor?: string) => <span className="text-ink-soft">{valor ?? '—'}</span>

const recurso = (valor?: number, unidad?: string) => (
  <span className="whitespace-nowrap font-mono text-xs tabular-nums text-ink-soft">
    {valor === undefined || valor === null ? '—' : `${valor.toLocaleString('es-PE')}${unidad ? ` ${unidad}` : ''}`}
  </span>
)

const fecha = (valor?: string) => <span className="whitespace-nowrap text-ink-soft">{formatearFechaHora(valor)}</span>

/** Las ocultas por defecto se agregan desde «Columnas», servidor y acciones siempre se muestran (DEC-32) */
export const COLUMNAS_SERVIDORES: ColumnaServidor[] = [
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
  { id: 'fechaAlta', etiqueta: 'Alta en inventario', ocultaPorDefecto: true, orden: 'fechaAlta', celda: (s) => fecha(s.fechaAlta) },
  {
    id: 'fechaActualizacion',
    etiqueta: 'Última actualización',
    ocultaPorDefecto: true,
    orden: 'fechaActualizacion',
    celda: (s) => fecha(s.fechaActualizacion),
  },
]

/** La columna de acciones es fija: se cuenta en el total, pero no se puede ocultar */
export const DEFINICIONES_SERVIDORES: DefinicionColumna[] = [
  ...COLUMNAS_SERVIDORES,
  { id: 'acciones', etiqueta: 'Acciones', fija: true },
]
