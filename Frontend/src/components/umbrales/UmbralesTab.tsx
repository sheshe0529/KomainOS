import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ChevronRight, Plus } from 'lucide-react'
import { umbralesApi } from '@/api/umbrales'
import type { UmbralRespuesta } from '@/api/types'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { formatearLimite, simbolo } from '@/utils/umbrales'
import { UmbralFormulario } from './UmbralFormulario'

interface UmbralesTabProps {
  esAdmin: boolean
}

function Limite({ umbral, valor }: { umbral: UmbralRespuesta; valor?: number }) {
  if (valor === undefined || valor === null) return <span className="text-ink-faint">—</span>
  return (
    <>
      {simbolo(umbral.operador)} {formatearLimite(valor, umbral.metrica?.unidad)}
    </>
  )
}

/** Vista general: un umbral por fila con su valor global; en los de lista, cuántos elementos tiene. El detalle está en cada fila */
export function UmbralesTab({ esAdmin }: UmbralesTabProps) {
  const { avisar } = useAvisos()
  const navigate = useNavigate()
  const [idMetrica, setIdMetrica] = useState('')
  const [creando, setCreando] = useState(false)
  const metricas = useConsulta(() => umbralesApi.metricas(), [])
  const filtro = idMetrica ? Number(idMetrica) : undefined
  const umbrales = useConsulta(() => umbralesApi.umbrales(filtro), [idMetrica])
  const hayMetricaActiva = metricas.datos?.some((m) => m.activo)
  const ruta = (u: UmbralRespuesta) => `/configuracion/umbrales/${u.id}`

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="w-full max-w-xs">
          <Selector value={idMetrica} onChange={(e) => setIdMetrica(e.target.value)} aria-label="Filtrar por métrica">
            <option value="">Todas las métricas</option>
            {metricas.datos?.map((m) => (
              <option key={m.id} value={m.id}>
                {m.nombre}
              </option>
            ))}
          </Selector>
        </div>
        {esAdmin && (
          <Boton variante="primario" icono={Plus} onClick={() => setCreando(true)} disabled={!hayMetricaActiva}>
            Nuevo umbral
          </Boton>
        )}
      </div>

      <MensajeError error={umbrales.error} onReintentar={umbrales.recargar} />
      {umbrales.cargando && !umbrales.datos ? (
        <Cargando />
      ) : umbrales.datos?.length === 0 ? (
        <p className="rounded-xl border border-line bg-panel px-5 py-8 text-center text-sm text-ink-soft">
          {esAdmin && !hayMetricaActiva ? 'Sin umbrales. Registre primero una métrica.' : 'Sin umbrales'}
        </p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-line bg-panel">
          <table className="w-full min-w-[680px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-5 py-3 font-medium">Umbral</th>
                <th className="px-3 py-3 font-medium">
                  <span className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full bg-warning" aria-hidden="true" /> Advertencia
                  </span>
                </th>
                <th className="px-3 py-3 font-medium">
                  <span className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full bg-danger" aria-hidden="true" /> Crítico
                  </span>
                </th>
                <th className="px-3 py-3 font-medium">Excepciones</th>
                <th className="px-3 py-3 font-medium">Estado</th>
                <th className="w-10 px-3 py-3">
                  <span className="sr-only">Detalle</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {umbrales.datos?.map((u) => (
                <tr
                  key={u.id}
                  onClick={() => navigate(ruta(u))}
                  className={`cursor-pointer hover:bg-panel-muted ${u.activo && u.metrica?.activo ? '' : 'text-ink-faint'}`}
                >
                  <td className="px-5 py-3">
                    <Link to={ruta(u)} onClick={(e) => e.stopPropagation()} className="font-medium text-ink hover:text-accent hover:underline">
                      {u.nombre}
                    </Link>
                    <div className="text-xs text-ink-faint">{u.metrica?.nombre}</div>
                  </td>
                  {u.lista ? (
                    <td colSpan={2} className="px-3 py-3" title={u.lista.elementos?.join(', ')}>
                      <span className="text-ink">{u.lista.nombre}</span>{' '}
                      <span className="font-mono text-xs tabular-nums text-ink-faint">· {u.lista.elementos?.length ?? 0}</span>
                    </td>
                  ) : (
                    <>
                      <td className="px-3 py-3 font-mono tabular-nums">
                        <Limite umbral={u} valor={u.valorAdvertencia} />
                      </td>
                      <td className="px-3 py-3 font-mono tabular-nums">
                        <Limite umbral={u} valor={u.valorCritico} />
                      </td>
                    </>
                  )}
                  <td className="px-3 py-3 tabular-nums text-ink-soft">
                    {u.cantidadExcepciones ? `${u.excepcionesActivas}/${u.cantidadExcepciones}` : '—'}
                  </td>
                  <td className="px-3 py-3">
                    {!u.metrica?.activo ? (
                      <StatusPill tone="warning" label="Métrica inactiva" />
                    ) : (
                      <StatusPill tone={u.activo ? 'success' : 'neutral'} label={u.activo ? 'Activo' : 'Inactivo'} />
                    )}
                  </td>
                  <td className="px-3 py-3 text-ink-faint">
                    <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {creando && (
        <UmbralFormulario
          metricas={metricas.datos ?? []}
          idMetricaInicial={filtro}
          onCerrar={() => setCreando(false)}
          onGuardado={(guardado) => {
            setCreando(false)
            avisar('Umbral creado.')
            navigate(ruta(guardado))
          }}
        />
      )}
    </div>
  )
}
