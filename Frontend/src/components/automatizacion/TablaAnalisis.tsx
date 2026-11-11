import { useState } from 'react'
import { CircleX } from 'lucide-react'
import type { AnalisisRespuesta } from '@/api/types'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { diagnosticarFallo, ESTADO_EVALUACION, NIVEL, TIPO_EVALUACION } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'
import { CELDA, ENCABEZADO } from './ciclo/estilosTabla'
import { MedicionesAnalisis } from './MedicionesAnalisis'
import { DiagnosticoActividad } from './RegistroActividad'
import { SalidaTerminal } from './SalidaTerminal'

interface TablaAnalisisProps {
  analisis: AnalisisRespuesta[]
  /** En la ficha del servidor el nombre sobra: cada fila dice qué análisis fue */
  conServidor?: boolean
  /** Sobre la tabla, un resumen de cada análisis que falló con acceso a su detalle (la etapa de evaluación) */
  resumirFallos?: boolean
}

/** El fallo se diagnostica con la salida del script: así se reconoce un error de sintaxis o un código distinto */
function diagnostico(a: AnalisisRespuesta) {
  return diagnosticarFallo(a.error, a.salida)
}

/** Lo que el usuario necesita de un vistazo: cuántas métricas salieron de sus umbrales, o qué falló */
function hallazgos(a: AnalisisRespuesta): string {
  if (a.estado === 'FALLIDA') return diagnostico(a).titulo
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

/**
 * Detalle de un análisis: con qué se ejecutó y el valor y nivel de cada métrica. Si falló, el diagnóstico, dónde buscar
 * la causa y la salida del script cuando llegó a ejecutarse
 */
function DetalleAnalisis({ a, onCerrar }: { a: AnalisisRespuesta; onCerrar: () => void }) {
  const titulo = `${a.tipo ? TIPO_EVALUACION[a.tipo] : 'Análisis'}${a.servidor ? ` · ${a.servidor}` : ''}`
  const fallido = a.estado === 'FALLIDA'
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
        {fallido && <DiagnosticoActividad fallo={diagnostico(a)} conCausas />}
        {fallido && (
          <section className="flex flex-col gap-2">
            <h4 className="text-sm font-semibold text-ink">Salida del script</h4>
            {a.salida ? (
              <SalidaTerminal salida={a.salida} nombre={`analisis_${a.id}_${a.servidor ?? 'salida'}`} altoMaximo="22rem" />
            ) : (
              <p className="text-sm text-ink-soft">
                El script no dejó salida: no llegó a ejecutarse (por ejemplo, por la conexión, el inicio de sesión o la
                elevación) o el análisis es anterior a que KomainOS la guardara.
              </p>
            )}
          </section>
        )}
        {!fallido && <MedicionesAnalisis mediciones={a.mediciones ?? []} />}
      </div>
    </Modal>
  )
}

/** Resumen de los análisis fallidos: qué falló en cada servidor, en una línea, y el detalle a un clic */
function ResumenFallos({ fallidos, onAbrir }: { fallidos: AnalisisRespuesta[]; onAbrir: (id: number) => void }) {
  return (
    <ul aria-label="Análisis que no se completaron" className="flex flex-col gap-2">
      {fallidos.map((a) => {
        const d = diagnostico(a)
        return (
          <li key={a.id} className="flex flex-wrap items-start gap-x-3 gap-y-2 rounded-lg border border-danger/25 bg-danger-soft/40 px-4 py-3">
            <CircleX className="mt-0.5 h-4 w-4 shrink-0 text-danger" aria-hidden="true" />
            <div className="min-w-0 flex-1 basis-60">
              <p className="text-sm font-medium text-danger">
                {d.titulo}
                {a.servidor && <span className="font-mono font-normal text-ink-soft"> · {a.servidor}</span>}
              </p>
              {d.detalle && <p className="mt-0.5 line-clamp-2 break-words font-mono text-xs text-ink">{d.detalle}</p>}
            </div>
            <button
              type="button"
              onClick={() => onAbrir(a.id!)}
              className="shrink-0 rounded-lg px-2 py-1 text-sm font-medium text-accent hover:bg-panel-muted focus-visible:outline-2 focus-visible:outline-accent"
            >
              Ver detalle
            </button>
          </li>
        )
      })}
    </ul>
  )
}

/**
 * Análisis (revisiones periódicas, evaluaciones previas o validaciones técnicas) en una tabla: cada fila dice el
 * resultado y lo encontrado, y al pulsarla se abre su detalle
 */
export function TablaAnalisis({ analisis, conServidor = true, resumirFallos = false }: TablaAnalisisProps) {
  // Se guarda el análisis y no la fila: si la página se refresca mientras está abierto, el detalle se actualiza
  const [idAbierto, setIdAbierto] = useState<number>()
  const abierto = idAbierto == null ? undefined : analisis.find((a) => a.id === idAbierto)
  const fallidos = resumirFallos ? analisis.filter((a) => a.estado === 'FALLIDA' && a.id != null) : []

  return (
    <>
      {fallidos.length > 0 && <ResumenFallos fallidos={fallidos} onAbrir={setIdAbierto} />}
      <div className={`overflow-x-auto rounded-lg border border-line ${fallidos.length > 0 ? 'mt-3' : ''}`}>
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
