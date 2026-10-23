import { useState } from 'react'
import { Pencil, Plus, Power, PowerOff } from 'lucide-react'
import { umbralesApi } from '@/api/umbrales'
import type { MetricaRespuesta } from '@/api/types'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { textoDeError } from '@/utils/errores'
import { describirMetrica } from '@/utils/umbrales'
import { MetricaFormulario } from './MetricaFormulario'

interface MetricasTabProps {
  esAdmin: boolean
}

/** Métricas que informan los scripts de análisis; una métrica de lista se evalúa con una lista de referencia */
export function MetricasTab({ esAdmin }: MetricasTabProps) {
  const { avisar } = useAvisos()
  const metricas = useConsulta(() => umbralesApi.metricas(), [])
  const [editando, setEditando] = useState<MetricaRespuesta | 'nueva' | null>(null)

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
      {esAdmin && (
        <div className="flex justify-end">
          <Boton variante="primario" icono={Plus} onClick={() => setEditando('nueva')}>
            Nueva métrica
          </Boton>
        </div>
      )}
      <MensajeError error={metricas.error} onReintentar={metricas.recargar} />
      {metricas.cargando && !metricas.datos ? (
        <Cargando />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-line bg-panel">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-4 py-3 font-medium">Métrica</th>
                <th className="px-4 py-3 font-medium">Código</th>
                <th className="px-4 py-3 font-medium">Tipo</th>
                <th className="px-4 py-3 font-medium">Estado</th>
                {esAdmin && <th className="px-4 py-3 text-right font-medium">Acciones</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {metricas.datos?.map((m) => (
                <tr key={m.id} className="hover:bg-panel-muted">
                  <td className="px-4 py-3 font-medium text-ink">{m.nombre}</td>
                  <td className="px-4 py-3 font-mono text-xs text-ink-soft">{m.codigo}</td>
                  <td className="px-4 py-3 text-ink-soft">{describirMetrica(m)}</td>
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
              {metricas.datos?.length === 0 && (
                <tr>
                  <td colSpan={esAdmin ? 5 : 4} className="px-4 py-8 text-center text-ink-soft">
                    Sin métricas
                  </td>
                </tr>
              )}
            </tbody>
          </table>
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
