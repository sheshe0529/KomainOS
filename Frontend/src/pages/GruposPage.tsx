import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Eye, Plus } from 'lucide-react'
import { gruposApi } from '@/api/inventario'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { GrupoFormulario } from '@/components/inventario/GrupoFormulario'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'
import { ESTADO_GRUPO, ETIQUETA_MODALIDAD, ETIQUETA_MODO } from '@/utils/etiquetas'

/** Grupos de mantenimiento (RF20, HU15). */
export function GruposPage() {
  const tonoCriticidad = useTonoCriticidad()
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()
  const [creando, setCreando] = useState(false)
  const grupos = useConsulta(() => gruposApi.listar(), [])

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

      <MensajeError error={grupos.error} onReintentar={grupos.recargar} />

      <div className="overflow-hidden rounded-xl border border-line bg-panel">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[860px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-4 py-3 font-medium">Grupo</th>
                <th className="px-4 py-3 font-medium">Integrantes</th>
                <th className="px-4 py-3 font-medium">Criticidad</th>
                <th className="px-4 py-3 font-medium">Entorno</th>
                <th className="px-4 py-3 font-medium">Responsable</th>
                <th className="px-4 py-3 font-medium">Planificación</th>
                <th className="px-4 py-3 font-medium">Estado</th>
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {grupos.cargando && !grupos.datos && (
                <tr>
                  <td colSpan={8}>
                    <Cargando />
                  </td>
                </tr>
              )}
              {grupos.datos?.map((g) => {
                const estado = g.estado ? ESTADO_GRUPO[g.estado] : undefined
                return (
                  <tr key={g.id} className="transition-colors hover:bg-panel-muted">
                    <td className="px-4 py-3">
                      <Link to={`/grupos/${g.id}`} className="font-medium text-ink hover:text-accent">
                        {g.nombre}
                      </Link>
                      {g.descripcion && <p className="line-clamp-1 text-xs text-ink-faint">{g.descripcion}</p>}
                    </td>
                    <td className="px-4 py-3 tabular-nums text-ink-soft">{g.cantidadIntegrantes}</td>
                    <td className="px-4 py-3">
                      {g.criticidad ? <StatusPill tone={tonoCriticidad(g.criticidad.prioridad)} label={g.criticidad.nombre ?? ''} /> : '—'}
                    </td>
                    <td className="px-4 py-3 text-ink-soft">{g.entorno?.nombre ?? '—'}</td>
                    <td className="px-4 py-3 text-ink-soft">{g.responsable?.nombre ?? '—'}</td>
                    <td className="px-4 py-3 text-ink-soft">
                      {g.modalidadPlanificacion
                        ? `${ETIQUETA_MODALIDAD[g.modalidadPlanificacion]} · ${g.modoEjecucion ? ETIQUETA_MODO[g.modoEjecucion] : ''}`
                        : 'Sin configurar'}
                    </td>
                    <td className="px-4 py-3">{estado && <StatusPill tone={estado.tono} label={estado.etiqueta} />}</td>
                    <td className="px-4 py-3 text-right">
                      <BotonIcono icono={Eye} etiqueta="Ver grupo" onClick={() => navigate(`/grupos/${g.id}`)} />
                    </td>
                  </tr>
                )
              })}
              {grupos.datos?.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-10 text-center text-sm text-ink-soft">
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
