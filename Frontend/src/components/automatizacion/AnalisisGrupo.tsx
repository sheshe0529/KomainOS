import { useState } from 'react'
import { Link } from 'react-router-dom'
import { History } from 'lucide-react'
import { analisisApi } from '@/api/automatizacion'
import type { AnalisisGrupoRespuesta, AnalisisRespuesta } from '@/api/types'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { ENLACE_FILA } from '@/components/ui/estilos'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { useDetallePanel, VistaDetalle } from '@/components/ui/PanelConsulta'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacion } from '@/hooks/usePaginacion'
import { ESTADO_EVALUACION, NIVEL, TIPO_EVALUACION } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'
import { UltimoAnalisis } from './AnalisisServidor'
import { CELDA, ENCABEZADO } from './ciclo/estilosTabla'
import { ContenidoAnalisis, EstadoAnalisis } from './TablaAnalisis'

const GRAVEDAD = { NORMAL: 0, ADVERTENCIA: 1, CRITICO: 2 } as const

/** El evento no tiene identificador propio: lo distinguen su tipo, su orden y su fecha */
const clave = (e: AnalisisGrupoRespuesta) => `${e.tipo}-${e.idOrden ?? ''}-${e.fecha}`

const nombreEvento = (e: AnalisisGrupoRespuesta) => (e.tipo ? TIPO_EVALUACION[e.tipo] : 'Análisis')

/** El peor nivel entre los integrantes analizados; si ninguno terminó, el estado de sus análisis */
function Resultado({ analisis }: { analisis: AnalisisRespuesta[] }) {
  if (analisis.length === 0) return <span className="text-ink-faint">—</span>
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
  return partes.join(' | ')
}

/** Una revisión del grupo: sus datos arriba y debajo el análisis de cada integrante, que se abre al pulsarlo */
function DetalleEvento({ e, onAbrirIntegrante }: { e: AnalisisGrupoRespuesta; onAbrirIntegrante: (id: number) => void }) {
  const analisis = e.analisis ?? []
  return (
    <div className="flex flex-col gap-4">
      <dl className="grid gap-4 sm:grid-cols-3">
        <Dato etiqueta="Análisis" valor={nombreEvento(e)} />
        <Dato etiqueta="Fecha" valor={formatearFechaHora(e.fecha)} />
        <Dato
          etiqueta="Orden"
          valor={
            e.idOrden ? (
              <Link to={`/ordenes/${e.idOrden}`} className={`font-mono ${ENLACE_FILA}`}>
                {e.orden}
              </Link>
            ) : (
              'Revisión periódica, sin orden'
            )
          }
        />
        <Dato etiqueta="Resultado" valor={<Resultado analisis={analisis} />} />
        <Dato etiqueta="Hallazgos" valor={hallazgos(analisis) || '—'} />
      </dl>
      <div className="overflow-x-auto rounded-lg border border-line">
        <table className="w-full min-w-[420px] text-sm">
          <thead>
            <tr className={ENCABEZADO}>
              <th className="px-3 py-2 font-medium">Servidor</th>
              <th className="px-3 py-2 font-medium">Resultado</th>
              <th className="px-3 py-2 font-medium">Fecha</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {analisis.map((a) => (
              <FilaDetalle key={a.id} onAbrir={() => onAbrirIntegrante(a.id!)} className="align-middle">
                <td className={CELDA}>
                  <BotonDetalle onAbrir={() => onAbrirIntegrante(a.id!)} etiqueta={`Ver el análisis de ${a.servidor}`}>
                    <span className="font-mono">{a.servidor}</span>
                  </BotonDetalle>
                </td>
                <td className={CELDA}>
                  <EstadoAnalisis a={a} />
                </td>
                <td className={`${CELDA} whitespace-nowrap text-ink-soft`}>{formatearFechaHora(a.fechaInicio)}</td>
              </FilaDetalle>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/**
 * Historial de análisis del grupo (RF37, RF38, RF55) en un panel lateral: cada revisión periódica del grupo y cada
 * evaluación previa o validación técnica de sus órdenes. Al pulsar una, sus datos y sus integrantes; al pulsar un
 * integrante, el detalle de su análisis
 */
function PanelAnalisisGrupo({ idGrupo, nombre, onCerrar }: { idGrupo: number; nombre?: string; onCerrar: () => void }) {
  const { pagina, tamano, setPagina, setTamano } = usePaginacion('analisis-grupo')
  const historial = useConsulta(() => analisisApi.historialGrupo(idGrupo, pagina, tamano), [idGrupo, pagina, tamano])
  const evento = useDetallePanel<string>()
  const integrante = useDetallePanel<number>()
  const filas = historial.datos?.contenido ?? []
  const abierto = evento.seleccion === undefined ? undefined : filas.find((e) => clave(e) === evento.seleccion)
  const analisis = integrante.seleccion == null ? undefined : abierto?.analisis?.find((a) => a.id === integrante.seleccion)

  function volverALaLista() {
    integrante.cerrar()
    evento.cerrar()
  }

  return (
    <Modal
      abierto
      posicion="derecha"
      ancho="xl"
      cerrarAlPulsarFuera
      titulo="Análisis del grupo"
      descripcion={
        <dl className="flex flex-wrap gap-x-5 gap-y-2 [overflow-wrap:anywhere]">
          {nombre && <Dato etiqueta="Grupo" valor={nombre} />}
          <Dato etiqueta="Análisis registrados" valor={historial.datos?.totalElementos ?? '—'} />
        </dl>
      }
      onCerrar={onCerrar}
    >
      <div hidden={abierto !== undefined}>
        <MensajeError error={historial.error} onReintentar={historial.recargar} />
        {historial.cargando && !historial.datos ? (
          <Cargando />
        ) : filas.length === 0 ? (
          !historial.error && (
            <p className="text-sm text-ink-soft">
              Aún no se analiza el grupo. Aquí aparecen sus revisiones periódicas y la evaluación previa y la validación
              técnica de sus órdenes.
            </p>
          )
        ) : (
          <div className="overflow-hidden rounded-lg border border-line">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[420px] text-sm">
                <thead>
                  <tr className={ENCABEZADO}>
                    <th className="px-3 py-2 font-medium">Análisis</th>
                    <th className="px-3 py-2 font-medium">Resultado</th>
                    <th className="px-3 py-2 font-medium">Fecha</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {filas.map((e) => (
                    <FilaDetalle key={clave(e)} onAbrir={() => evento.abrir(clave(e))} className="align-middle">
                      <td className={CELDA}>
                        <BotonDetalle onAbrir={() => evento.abrir(clave(e))} etiqueta={`Ver el detalle de ${nombreEvento(e)}`}>
                          {nombreEvento(e)}
                        </BotonDetalle>
                        {e.orden && <span className="ml-2 font-mono text-xs text-ink-faint">{e.orden}</span>}
                      </td>
                      <td className={CELDA}>
                        <Resultado analisis={e.analisis ?? []} />
                      </td>
                      <td className={`${CELDA} whitespace-nowrap text-ink-soft`}>{formatearFechaHora(e.fecha)}</td>
                    </FilaDetalle>
                  ))}
                </tbody>
              </table>
            </div>
            <Paginacion
              pagina={pagina}
              totalPaginas={historial.datos?.totalPaginas ?? 0}
              totalElementos={historial.datos?.totalElementos ?? 0}
              onCambiar={setPagina}
              tamano={tamano}
              onTamano={setTamano}
            />
          </div>
        )}
      </div>

      {abierto && (
        <div hidden={analisis !== undefined}>
          <VistaDetalle onVolver={volverALaLista} refVolver={evento.refVolver}>
            <DetalleEvento e={abierto} onAbrirIntegrante={integrante.abrir} />
          </VistaDetalle>
        </div>
      )}

      {analisis && (
        <VistaDetalle onVolver={integrante.cerrar} refVolver={integrante.refVolver}>
          <ContenidoAnalisis a={analisis} encabezado />
        </VistaDetalle>
      )}
    </Modal>
  )
}

/** Análisis del grupo: en la ficha, el último; el historial completo, en un panel lateral */
export function AnalisisGrupo({ idGrupo, nombre }: { idGrupo: number; nombre?: string }) {
  const ultimo = useConsulta(() => analisisApi.historialGrupo(idGrupo, 0, 1), [idGrupo])
  const [panel, setPanel] = useState(false)
  const reciente = ultimo.datos?.contenido?.[0]

  return (
    <Tarjeta
      titulo="Análisis del grupo"
      acciones={
        <Boton icono={History} aria-haspopup="dialog" onClick={() => setPanel(true)}>
          Ver análisis
        </Boton>
      }
    >
      <MensajeError error={ultimo.error} onReintentar={ultimo.recargar} />
      {ultimo.cargando && !ultimo.datos ? (
        <Cargando />
      ) : reciente ? (
        <UltimoAnalisis
          nombre={nombreEvento(reciente)}
          fecha={reciente.fecha}
          resultado={<Resultado analisis={reciente.analisis ?? []} />}
          hallazgos={hallazgos(reciente.analisis ?? []) || '—'}
        />
      ) : (
        <p className="text-sm text-ink-soft">
          Aún no se analiza el grupo. Aquí aparecen sus revisiones periódicas y la evaluación previa y la validación técnica
          de sus órdenes.
        </p>
      )}
      {panel && <PanelAnalisisGrupo idGrupo={idGrupo} nombre={nombre} onCerrar={() => setPanel(false)} />}
    </Tarjeta>
  )
}
