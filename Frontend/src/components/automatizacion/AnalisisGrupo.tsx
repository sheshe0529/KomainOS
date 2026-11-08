import { useState } from 'react'
import { Link } from 'react-router-dom'
import { analisisApi } from '@/api/automatizacion'
import type { AnalisisGrupoRespuesta, AnalisisRespuesta } from '@/api/types'
import { Tarjeta } from '@/components/common/Tarjeta'
import { Cargando } from '@/components/ui/Cargando'
import { ENLACE_FILA } from '@/components/ui/estilos'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacion } from '@/hooks/usePaginacion'
import { ESTADO_EVALUACION, NIVEL, TIPO_EVALUACION } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'
import { CELDA, ENCABEZADO } from './ciclo/estilosTabla'
import { TablaAnalisis } from './TablaAnalisis'

const GRAVEDAD = { NORMAL: 0, ADVERTENCIA: 1, CRITICO: 2 } as const

/** El evento no tiene identificador propio: lo distinguen su tipo, su orden y su fecha */
const clave = (e: AnalisisGrupoRespuesta) => `${e.tipo}-${e.idOrden ?? ''}-${e.fecha}`

/** El peor nivel entre los integrantes analizados; si ninguno terminó, el estado de sus análisis */
function Resultado({ analisis }: { analisis: AnalisisRespuesta[] }) {
  const niveles = analisis.filter((a) => a.estado === 'FINALIZADA' && a.nivel).map((a) => a.nivel!)
  if (niveles.length > 0) {
    const peor = niveles.reduce((a, b) => (GRAVEDAD[b] > GRAVEDAD[a] ? b : a))
    return <StatusPill tone={NIVEL[peor].tono} label={NIVEL[peor].etiqueta} />
  }
  const estado = analisis.some((a) => a.estado === 'EN_CURSO') ? 'EN_CURSO' : 'FALLIDA'
  return <StatusPill tone={ESTADO_EVALUACION[estado].tono} label={ESTADO_EVALUACION[estado].etiqueta} />
}

/** Cuántos integrantes salieron de sus umbrales y cuántos no se pudieron analizar */
function hallazgos(analisis: AnalisisRespuesta[]): string {
  const total = analisis.length
  const conHallazgos = analisis.filter((a) => a.estado === 'FINALIZADA' && a.nivel && a.nivel !== 'NORMAL').length
  const fallidos = analisis.filter((a) => a.estado === 'FALLIDA').length
  const partes = [
    conHallazgos > 0
      ? `${conHallazgos} de ${total} ${total === 1 ? 'servidor' : 'servidores'} con hallazgos`
      : fallidos < total && `${total - fallidos === 1 ? 'Un servidor' : `${total - fallidos} servidores`} dentro de sus umbrales`,
    fallidos > 0 && `${fallidos} sin analizar`,
  ].filter(Boolean)
  return partes.join(' · ')
}

/**
 * Historial de análisis del grupo (RF37, RF38, RF55): una fila por revisión periódica del grupo y por evaluación previa
 * o validación técnica de sus órdenes; al pulsarla, el análisis de cada integrante
 */
export function AnalisisGrupo({ idGrupo }: { idGrupo: number }) {
  const { pagina, tamano, setPagina, setTamano } = usePaginacion('analisis-grupo')
  const historial = useConsulta(() => analisisApi.historialGrupo(idGrupo, pagina, tamano), [idGrupo, pagina, tamano])
  const [claveAbierta, setClaveAbierta] = useState<string>()

  const filas = historial.datos?.contenido ?? []
  const abierto = filas.find((e) => clave(e) === claveAbierta)

  return (
    <Tarjeta titulo="Análisis del grupo">
      <MensajeError error={historial.error} onReintentar={historial.recargar} />
      {historial.cargando && !historial.datos ? (
        <Cargando />
      ) : filas.length === 0 ? (
        <p className="text-sm text-ink-soft">
          Aún no se analiza el grupo. Aquí aparecen sus revisiones periódicas y la evaluación previa y la validación técnica
          de sus órdenes.
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="overflow-x-auto rounded-lg border border-line">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className={ENCABEZADO}>
                  <th className="px-3 py-2 font-medium">Análisis</th>
                  <th className="px-3 py-2 font-medium">Orden</th>
                  <th className="px-3 py-2 font-medium">Resultado</th>
                  <th className="px-3 py-2 font-medium">Hallazgos</th>
                  <th className="px-3 py-2 font-medium">Fecha</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filas.map((e) => {
                  const analisis = e.analisis ?? []
                  const nombre = e.tipo ? TIPO_EVALUACION[e.tipo] : 'Análisis'
                  return (
                    <FilaDetalle key={clave(e)} onAbrir={() => setClaveAbierta(clave(e))} className="align-middle">
                      <td className={CELDA}>
                        <BotonDetalle onAbrir={() => setClaveAbierta(clave(e))} etiqueta={`Ver el detalle de ${nombre}`}>
                          {nombre}
                        </BotonDetalle>
                      </td>
                      <td className={`${CELDA} text-ink-soft`}>
                        {e.idOrden ? (
                          <Link to={`/ordenes/${e.idOrden}`} className={`font-mono ${ENLACE_FILA}`}>
                            {e.orden}
                          </Link>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className={CELDA}>{analisis.length > 0 && <Resultado analisis={analisis} />}</td>
                      <td className={`${CELDA} text-ink-soft`}>{hallazgos(analisis)}</td>
                      <td className={`${CELDA} whitespace-nowrap text-ink-soft`}>{formatearFechaHora(e.fecha)}</td>
                    </FilaDetalle>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="-mx-5 -mb-5">
            <Paginacion
              pagina={pagina}
              totalPaginas={historial.datos?.totalPaginas ?? 0}
              totalElementos={historial.datos?.totalElementos ?? 0}
              onCambiar={setPagina}
              tamano={tamano}
              onTamano={setTamano}
            />
          </div>
        </div>
      )}

      {abierto && (
        <Modal
          abierto
          titulo={`${abierto.tipo ? TIPO_EVALUACION[abierto.tipo] : 'Análisis'} del grupo`}
          descripcion={[abierto.orden, formatearFechaHora(abierto.fecha)].filter(Boolean).join(' · ')}
          onCerrar={() => setClaveAbierta(undefined)}
          ancho="2xl"
        >
          {/* Cada integrante abre el detalle de sus métricas */}
          <TablaAnalisis analisis={abierto.analisis ?? []} />
        </Modal>
      )}
    </Tarjeta>
  )
}
