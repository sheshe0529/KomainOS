import { useMemo, useState } from 'react'
import { Pencil, Plus, Power, PowerOff } from 'lucide-react'
import { umbralesApi } from '@/api/umbrales'
import type { MetricaRespuesta } from '@/api/types'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacionLocal } from '@/hooks/usePaginacion'
import { textoDeError } from '@/utils/errores'
import { describirMetrica, filtrarMetricas, sistemasDeMetrica } from '@/utils/umbrales'
import { MetricaFormulario } from './MetricaFormulario'

interface MetricasTabProps {
  esAdmin: boolean
  visita: number
}

/**
 * Métricas que informan los scripts de análisis (las variables de salud del R3.1); una métrica de lista se evalúa con
 * una lista de referencia
 */
export function MetricasTab({ esAdmin, visita }: MetricasTabProps) {
  const { avisar } = useAvisos()
  const metricas = useConsulta(() => umbralesApi.metricas(), [visita])
  const [busqueda, setBusqueda] = useState('')
  const filtradas = useMemo(() => filtrarMetricas(metricas.datos ?? [], busqueda), [metricas.datos, busqueda])
  const tabla = usePaginacionLocal(filtradas, 'metricas')
  const [editando, setEditando] = useState<MetricaRespuesta | 'nueva' | null>(null)

  function buscar(texto: string) {
    setBusqueda(texto)
    tabla.controles.onCambiar(0)
  }

  async function cambiarEstado(m: MetricaRespuesta) {
    try {
      await umbralesApi.cambiarEstadoMetrica(m.id!, !m.activo)
      metricas.recargar()
      avisar(m.activo ? 'Métrica desactivada: sus umbrales dejan de evaluarse.' : 'Métrica activada.')
    } catch (e) {
      avisar(textoDeError(e), 'error')
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <CampoBusqueda
          valor={busqueda}
          onCambiar={buscar}
          placeholder="Buscar por nombre, código, descripción o unidad"
          aria-label="Buscar métricas"
        />
        {busqueda.trim() && metricas.datos && (
          <span className="text-xs text-ink-soft" aria-live="polite">
            {filtradas.length} de {metricas.datos.length} métricas
          </span>
        )}
        {esAdmin && (
          <Boton variante="primario" icono={Plus} onClick={() => setEditando('nueva')} className="ml-auto">
            Nueva métrica
          </Boton>
        )}
      </div>
      <MensajeError error={metricas.error} onReintentar={metricas.recargar} />
      {metricas.cargando && !metricas.datos ? (
        <Cargando />
      ) : (
        <div className="overflow-hidden rounded-xl border border-line bg-panel">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead>
                <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                  <th className="px-4 py-3 font-medium">Métrica</th>
                  <th className="px-4 py-3 font-medium">Código</th>
                  <th className="px-4 py-3 font-medium">Tipo</th>
                  <th className="px-4 py-3 font-medium">Se mide en</th>
                  <th className="px-4 py-3 font-medium">Estado</th>
                  {esAdmin && <th className="px-4 py-3 text-right font-medium">Acciones</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {tabla.filas.map((m) => (
                  <tr key={m.id} className="transition-colors hover:bg-panel-muted">
                    <td className="max-w-md px-4 py-3">
                      <span className="font-medium text-ink">{m.nombre}</span>
                      {m.descripcion && (
                        <span className="mt-0.5 block truncate text-xs text-ink-faint" title={m.descripcion}>
                          {m.descripcion}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-ink-soft">{m.codigo}</td>
                    <td className="px-4 py-3 text-ink-soft">{describirMetrica(m)}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-ink-soft">{sistemasDeMetrica(m)}</td>
                    <td className="px-4 py-3">
                      <StatusPill tone={m.activo ? 'success' : 'neutral'} label={m.activo ? 'Activa' : 'Inactiva'} />
                    </td>
                    {esAdmin && (
                      <td className="px-4 py-3">
                        <div className="flex justify-end gap-1">
                          <BotonIcono icono={Pencil} etiqueta={`Editar ${m.nombre}`} onClick={() => setEditando(m)} />
                          <BotonIcono
                            icono={m.activo ? PowerOff : Power}
                            etiqueta={m.activo ? 'Desactivar' : 'Activar'}
                            onClick={() => cambiarEstado(m)}
                          />
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
                {metricas.datos && filtradas.length === 0 && (
                  <tr>
                    <td colSpan={esAdmin ? 6 : 5} className="px-4 py-8 text-center text-ink-soft">
                      {metricas.datos.length === 0 ? 'Sin métricas' : 'Ninguna métrica coincide con la búsqueda'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <Paginacion {...tabla.controles} />
        </div>
      )}

      {editando && (
        <MetricaFormulario
          metrica={editando === 'nueva' ? undefined : editando}
          onCerrar={() => setEditando(null)}
          onGuardado={() => {
            setEditando(null)
            metricas.recargar()
            avisar('Métrica guardada.')
          }}
        />
      )}
    </div>
  )
}
