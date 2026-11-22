import { useState } from 'react'
import type { ReactNode } from 'react'
import { History, ScanSearch } from 'lucide-react'
import { analisisApi } from '@/api/automatizacion'
import type { AnalisisRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { Dato, Tarjeta } from '@/components/common/Tarjeta'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { BotonDetalle, FilaDetalle } from '@/components/ui/FilaDetalle'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { Paginacion } from '@/components/ui/Paginacion'
import { useDetallePanel, VistaDetalle } from '@/components/ui/PanelConsulta'
import { useConsulta } from '@/hooks/useConsulta'
import { usePaginacion } from '@/hooks/usePaginacion'
import { TIPO_EVALUACION } from '@/utils/automatizacion'
import { formatearFechaHora } from '@/utils/formato'
import { CELDA, ENCABEZADO } from './ciclo/estilosTabla'
import { PreparacionServidor } from './PreparacionServidor'
import { PruebaAcceso } from './PruebaAcceso'
import { ContenidoAnalisis, EstadoAnalisis, hallazgosAnalisis } from './TablaAnalisis'

/** Qué le falta al servidor para el ciclo automático; lo ven el administrador y el operador */
export function PreparacionServidorTarjeta({ idServidor }: { idServidor: number }) {
  const preparacion = useConsulta(() => analisisApi.preparacion(idServidor), [idServidor])
  return (
    <Tarjeta titulo="Preparación para el ciclo automático">
      <MensajeError error={preparacion.error} onReintentar={preparacion.recargar} />
      {preparacion.cargando && !preparacion.datos ? (
        <Cargando />
      ) : (
        preparacion.datos && <PreparacionServidor preparacion={preparacion.datos} />
      )}
    </Tarjeta>
  )
}

/** Conexión, autenticación y permisos de la cuenta de servicio, sin ejecutar mantenimientos */
export function AccesoServidorTarjeta({ idServidor }: { idServidor: number }) {
  return (
    <Tarjeta titulo="Acceso de la cuenta de servicio">
      <PruebaAcceso
        idServidor={idServidor}
        descripcion="Comprueba la conexión, la autenticación y los permisos de administrador sin ejecutar mantenimientos."
      />
    </Tarjeta>
  )
}

const nombreAnalisis = (a: AnalisisRespuesta) => (a.tipo ? TIPO_EVALUACION[a.tipo] : 'Análisis')

/** El último análisis en una línea: qué fue y cuándo, su resultado y lo que encontró */
export function UltimoAnalisis({ nombre, fecha, resultado, hallazgos }: {
  nombre: string
  fecha?: string
  resultado: ReactNode
  hallazgos: string
}) {
  return (
    <dl className="grid gap-4 sm:grid-cols-3">
      <Dato etiqueta="Último análisis" valor={`${nombre}, ${formatearFechaHora(fecha)}`} />
      <Dato etiqueta="Resultado" valor={resultado} />
      <Dato etiqueta="Hallazgos" valor={hallazgos} />
    </dl>
  )
}

/**
 * Historial de análisis del servidor (RF37) en un panel lateral, como la actividad de una orden: cada análisis con su
 * resultado y su fecha; al pulsarlo, sus datos arriba y debajo sus métricas o el diagnóstico del fallo
 */
function PanelAnalisisServidor({ idServidor, hostname, version, onCerrar }: {
  idServidor: number
  hostname?: string
  version: number
  onCerrar: () => void
}) {
  const { pagina, tamano, setPagina, setTamano } = usePaginacion('analisis-servidor')
  const historial = useConsulta(() => analisisApi.historial(idServidor, pagina, tamano), [idServidor, pagina, tamano, version])
  const detalle = useDetallePanel<number>()
  const filas = historial.datos?.contenido ?? []
  const abierto = detalle.seleccion == null ? undefined : filas.find((a) => a.id === detalle.seleccion)

  return (
    <Modal
      abierto
      posicion="derecha"
      ancho="xl"
      cerrarAlPulsarFuera
      titulo="Análisis del servidor"
      descripcion={
        <dl className="flex flex-wrap gap-x-5 gap-y-2 [overflow-wrap:anywhere]">
          {hostname && <Dato etiqueta="Servidor" valor={hostname} mono />}
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
              Aún no se analiza este servidor. Las revisiones periódicas corren según su frecuencia de mantenimiento.
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
                  {filas.map((a) => (
                    <FilaDetalle key={a.id} onAbrir={() => detalle.abrir(a.id!)} className="align-middle">
                      <td className={CELDA}>
                        <BotonDetalle onAbrir={() => detalle.abrir(a.id!)} etiqueta={`Ver el detalle de ${nombreAnalisis(a)}`}>
                          {nombreAnalisis(a)}
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
        <VistaDetalle onVolver={detalle.cerrar} refVolver={detalle.refVolver}>
          <ContenidoAnalisis a={abierto} encabezado />
        </VistaDetalle>
      )}
    </Modal>
  )
}

/** Análisis del servidor (RF37): en la ficha, el último; el historial completo, en un panel lateral */
export function AnalisisServidor({ idServidor, hostname }: { idServidor: number; hostname?: string }) {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const { avisar } = useAvisos()

  const ultimo = useConsulta(() => analisisApi.historial(idServidor, 0, 1), [idServidor])
  const [panel, setPanel] = useState(false)
  const [version, setVersion] = useState(0)
  const [revisando, setRevisando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function revisar() {
    setRevisando(true)
    setError(undefined)
    try {
      const r = await analisisApi.revisar(idServidor)
      avisar(
        r.estado === 'FALLIDA'
          ? `La revisión falló: ${r.error ?? 'revise el detalle'}`
          : r.nivel === 'CRITICO'
            ? 'Revisión completada: condición crítica detectada. Siga su atención en la sala de operaciones.'
            : r.nivel === 'ADVERTENCIA'
              ? 'Revisión completada con advertencias: se notificó a los responsables.'
              : 'Revisión completada: el servidor está dentro de sus umbrales.',
        r.estado === 'FALLIDA' ? 'error' : 'exito',
      )
      ultimo.recargar()
      setVersion((v) => v + 1)
    } catch (e) {
      setError(e)
    } finally {
      setRevisando(false)
    }
  }

  const reciente = ultimo.datos?.contenido?.[0]

  return (
    <Tarjeta
      titulo="Análisis del servidor"
      acciones={
        <div className="flex flex-wrap justify-end gap-2">
          {esAdmin && (
            <Boton icono={ScanSearch} cargando={revisando} onClick={revisar}>
              Revisar ahora
            </Boton>
          )}
          <Boton icono={History} aria-haspopup="dialog" onClick={() => setPanel(true)}>
            Ver análisis
          </Boton>
        </div>
      }
    >
      <MensajeError error={error ?? ultimo.error} />
      {ultimo.cargando && !ultimo.datos ? (
        <Cargando />
      ) : reciente ? (
        <UltimoAnalisis
          nombre={nombreAnalisis(reciente)}
          fecha={reciente.fechaInicio}
          resultado={<EstadoAnalisis a={reciente} />}
          hallazgos={hallazgosAnalisis(reciente)}
        />
      ) : (
        <p className="text-sm text-ink-soft">
          Aún no se analiza este servidor. Las revisiones periódicas corren según su frecuencia de mantenimiento.
        </p>
      )}
      {panel && (
        <PanelAnalisisServidor idServidor={idServidor} hostname={hostname} version={version} onCerrar={() => setPanel(false)} />
      )}
    </Tarjeta>
  )
}
