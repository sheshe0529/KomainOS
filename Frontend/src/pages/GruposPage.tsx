import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Eye, Plus } from 'lucide-react'
import { gruposApi } from '@/api/inventario'
import type { GrupoResumenRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { GrupoFormulario } from '@/components/inventario/GrupoFormulario'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { SelectorColumnas } from '@/components/ui/SelectorColumnas'
import { StatusPill, type StatusTone } from '@/components/ui/StatusPill'
import { useColumnasVisibles, type DefinicionColumna } from '@/hooks/useColumnasVisibles'
import { useConsulta } from '@/hooks/useConsulta'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'
import { ESTADO_GRUPO, ETIQUETA_MODALIDAD, ETIQUETA_MODO } from '@/utils/etiquetas'

interface Contexto {
  tonoCriticidad: (prioridad?: number) => StatusTone
  ve: (id: string) => boolean
}

interface ColumnaGrupo extends DefinicionColumna {
  celda: (g: GrupoResumenRespuesta, ctx: Contexto) => ReactNode
}

/** Columnas de la lista de grupos (RF20, DEC-32); el nombre y las acciones siempre se muestran. */
const COLUMNAS: ColumnaGrupo[] = [
  {
    id: 'grupo',
    etiqueta: 'Grupo',
    fija: true,
    celda: (g, { ve }) => (
      <>
        <Link to={`/grupos/${g.id}`} className="font-medium text-ink hover:text-accent">
          {g.nombre}
        </Link>
        {/* Si la descripción no tiene columna propia, se resume bajo el nombre. */}
        {!ve('descripcion') && g.descripcion && <p className="line-clamp-1 text-xs text-ink-faint">{g.descripcion}</p>}
      </>
    ),
  },
  {
    id: 'descripcion',
    etiqueta: 'Descripción',
    ocultaPorDefecto: true,
    celda: (g) => <span className="line-clamp-2 text-ink-soft">{g.descripcion ?? '—'}</span>,
  },
  {
    id: 'integrantes',
    etiqueta: 'Integrantes',
    celda: (g) => <span className="tabular-nums text-ink-soft">{g.cantidadIntegrantes}</span>,
  },
  {
    id: 'criticidad',
    etiqueta: 'Criticidad',
    celda: (g, { tonoCriticidad }) =>
      g.criticidad ? <StatusPill tone={tonoCriticidad(g.criticidad.prioridad)} label={g.criticidad.nombre ?? ''} /> : '—',
  },
  { id: 'entorno', etiqueta: 'Entorno', celda: (g) => <span className="text-ink-soft">{g.entorno?.nombre ?? '—'}</span> },
  {
    id: 'sistemaOperativo',
    etiqueta: 'Sistema operativo',
    ocultaPorDefecto: true,
    celda: (g) => <span className="text-ink-soft">{g.sistemaOperativo?.nombre ?? '—'}</span>,
  },
  { id: 'responsable', etiqueta: 'Responsable', celda: (g) => <span className="text-ink-soft">{g.responsable?.nombre ?? '—'}</span> },
  {
    id: 'planificacion',
    etiqueta: 'Planificación',
    celda: (g) => (
      <span className="text-ink-soft">
        {g.modalidadPlanificacion
          ? `${ETIQUETA_MODALIDAD[g.modalidadPlanificacion]} · ${g.modoEjecucion ? ETIQUETA_MODO[g.modoEjecucion] : ''}`
          : 'Sin configurar'}
      </span>
    ),
  },
  {
    id: 'estado',
    etiqueta: 'Estado',
    celda: (g) => {
      const estado = g.estado ? ESTADO_GRUPO[g.estado] : undefined
      return estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />
    },
  },
]

const DEFINICIONES: DefinicionColumna[] = [...COLUMNAS, { id: 'acciones', etiqueta: 'Acciones', fija: true }]

/** Grupos de mantenimiento (RF20, HU15). */
export function GruposPage() {
  const tonoCriticidad = useTonoCriticidad()
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()
  const [creando, setCreando] = useState(false)
  const grupos = useConsulta(() => gruposApi.listar(), [])
  const columnas = useColumnasVisibles('grupos', DEFINICIONES)

  const visibles = COLUMNAS.filter((c) => columnas.ve(c.id))
  const contexto: Contexto = { tonoCriticidad, ve: columnas.ve }
  const totalColumnas = visibles.length + 1

  return (
    <>
      <PageHeader
        title="Grupos de mantenimiento"
        description="Conjuntos de servidores con el mismo responsable, entorno y sistema operativo que se mantienen de forma coordinada."
        actions={
          esAdmin && (
            <Boton variante="primario" icono={Plus} onClick={() => setCreando(true)}>
              Nuevo grupo
            </Boton>
          )
        }
      />

      <div className="mb-4 flex justify-end">
        <SelectorColumnas {...columnas} />
      </div>

      <MensajeError error={grupos.error} onReintentar={grupos.recargar} />

      <div className="overflow-hidden rounded-xl border border-line bg-panel">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm" style={{ minWidth: `${Math.max(640, totalColumnas * 115)}px` }}>
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                {visibles.map((c) => (
                  <th key={c.id} className="px-4 py-3 font-medium">
                    {c.etiqueta}
                  </th>
                ))}
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {grupos.cargando && !grupos.datos && (
                <tr>
                  <td colSpan={totalColumnas}>
                    <Cargando />
                  </td>
                </tr>
              )}
              {grupos.datos?.map((g) => (
                <tr key={g.id} className="transition-colors hover:bg-panel-muted">
                  {visibles.map((c) => (
                    <td key={c.id} className="px-4 py-3">
                      {c.celda(g, contexto)}
                    </td>
                  ))}
                  <td className="px-4 py-3 text-right">
                    <BotonIcono icono={Eye} etiqueta="Ver grupo" onClick={() => navigate(`/grupos/${g.id}`)} />
                  </td>
                </tr>
              ))}
              {grupos.datos?.length === 0 && (
                <tr>
                  <td colSpan={totalColumnas} className="px-4 py-10 text-center text-sm text-ink-soft">
                    No hay grupos de mantenimiento registrados.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {creando && (
        <GrupoFormulario
          abierto
          onCerrar={() => setCreando(false)}
          onGuardado={(g) => {
            setCreando(false)
            avisar(`Grupo ${g.nombre} creado. Agregue sus integrantes.`)
            navigate(`/grupos/${g.id}`)
          }}
        />
      )}
    </>
  )
}
