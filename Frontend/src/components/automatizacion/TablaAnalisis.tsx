import { useState } from 'react'
import type { AnalisisRespuesta } from '@/api/types'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { ESTADO_EVALUACION, NIVEL, TIPO_EVALUACION } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'
import { CELDA, ENCABEZADO } from './ciclo/estilosTabla'
import { MedicionesAnalisis } from './MedicionesAnalisis'

interface TablaAnalisisProps {
  analisis: AnalisisRespuesta[]
  /** En la ficha del servidor el nombre sobra: cada fila dice qué análisis fue */
  conServidor?: boolean
}

/** Lo que el usuario necesita de un vistazo: cuántas métricas salieron de sus umbrales */
function hallazgos(a: AnalisisRespuesta): string {
  if (a.estado === 'FALLIDA') return 'No se pudo analizar el servidor'
  if (a.estado === 'EN_CURSO') return 'Analizando…'
  const mediciones = a.mediciones ?? []
  const criticas = mediciones.filter((m) => m.nivel === 'CRITICO').length
  const advertencias = mediciones.filter((m) => m.nivel === 'ADVERTENCIA').length
  if (criticas + advertencias === 0) return `${mediciones.length} métricas dentro de sus umbrales`
  return [criticas > 0 && `${criticas} en crítico`, advertencias > 0 && `${advertencias} en advertencia`]
    .filter(Boolean)
    .join(' · ')
}

/** El nivel resume el análisis; si falló o sigue en curso, lo dice su estado */
function Estado({ a }: { a: AnalisisRespuesta }) {
  if (a.estado === 'FINALIZADA' && a.nivel) return <StatusPill tone={NIVEL[a.nivel].tono} label={NIVEL[a.nivel].etiqueta} />
  const estado = a.estado ? ESTADO_EVALUACION[a.estado] : undefined
  return estado ? <StatusPill tone={estado.tono} label={estado.etiqueta} /> : null
}

/** Detalle de un análisis: con qué se ejecutó y el valor y nivel de cada métrica */
function DetalleAnalisis({ a, onCerrar }: { a: AnalisisRespuesta; onCerrar: () => void }) {
  const titulo = `${a.tipo ? TIPO_EVALUACION[a.tipo] : 'Análisis'}${a.servidor ? ` · ${a.servidor}` : ''}`
  return (
    <Modal abierto titulo={titulo} descripcion={formatearFechaHora(a.fechaInicio)} onCerrar={onCerrar} ancho="2xl">
      <div className="flex flex-col gap-4">
        <dl className="grid gap-4 rounded-lg bg-panel-muted px-4 py-3 sm:grid-cols-3">
          <div>
            <dt className="text-xs text-ink-faint">Resultado</dt>
            <dd className="mt-1">
              <Estado a={a} />
            </dd>
          </div>
          <div>
            <dt className="text-xs text-ink-faint">Script</dt>
            <dd className="mt-0.5 text-sm text-ink">
              {a.script} <span className="font-mono text-xs text-ink-faint">v{a.numeroVersion}</span>
            </dd>
          </div>
          <div>
            <dt className="text-xs text-ink-faint">Cuenta de servicio</dt>
            <dd className="mt-0.5 break-words text-sm text-ink">{a.cuenta ?? '—'}</dd>
          </div>
        </dl>
        {a.error && <p className="rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">{a.error}</p>}
        {a.estado !== 'FALLIDA' && <MedicionesAnalisis mediciones={a.mediciones ?? []} />}
      </div>
    </Modal>
  )
}

/**
 * Análisis (revisiones periódicas, evaluaciones previas o validaciones técnicas) en una tabla: cada fila dice el
 * resultado y lo encontrado, y al pulsarla se abre su detalle
 */
export function TablaAnalisis({ analisis, conServidor = true }: TablaAnalisisProps) {
  // Se guarda el análisis y no la fila: si la página se refresca mientras está abierto, el detalle se actualiza
  const [idAbierto, setIdAbierto] = useState<number>()
  const abierto = analisis.find((a) => a.id === idAbierto)

  return (
    <>
      <div className="overflow-x-auto rounded-lg border border-line">
        <table className="w-full min-w-[680px] text-sm">
          <thead>
            <tr className={ENCABEZADO}>
              <th className="px-3 py-2 font-medium">{conServidor ? 'Servidor' : 'Análisis'}</th>
              <th className="px-3 py-2 font-medium">Resultado</th>
              <th className="px-3 py-2 font-medium">Hallazgos</th>
              <th className="px-3 py-2 font-medium">Cuenta de servicio</th>
              <th className="px-3 py-2 font-medium">Fecha</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {analisis.map((a) => {
              const nombre = conServidor && a.servidor ? a.servidor : a.tipo ? TIPO_EVALUACION[a.tipo] : 'Análisis'
              return (
                <FilaDetalle key={a.id} onAbrir={() => setIdAbierto(a.id)} className="align-middle">
                  <td className={CELDA}>
                    <BotonDetalle onAbrir={() => setIdAbierto(a.id)} etiqueta={`Ver el detalle de ${nombre}`}>
                      <span className={conServidor ? 'font-mono' : ''}>{nombre}</span>
                    </BotonDetalle>
                  </td>
                  <td className={CELDA}>
                    <Estado a={a} />
                  </td>
                  <td className={`${CELDA} ${a.estado === 'FALLIDA' ? 'text-danger' : 'text-ink-soft'}`}>{hallazgos(a)}</td>
                  <td className={`${CELDA} break-words text-ink-soft`}>{a.cuenta ?? '—'}</td>
                  <td className={`${CELDA} whitespace-nowrap text-ink-soft`}>{formatearFechaHora(a.fechaInicio)}</td>
                </FilaDetalle>
              )
            })}
          </tbody>
        </table>
      </div>
      {abierto && <DetalleAnalisis a={abierto} onCerrar={() => setIdAbierto(undefined)} />}
    </>
  )
}
