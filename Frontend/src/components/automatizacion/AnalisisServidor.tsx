import { useState } from 'react'
import { ScanSearch } from 'lucide-react'
import { analisisApi } from '@/api/automatizacion'
import { useSesion } from '@/auth/sesion-context'
import { Tarjeta } from '@/components/common/Tarjeta'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Paginacion } from '@/components/ui/Paginacion'
import { useConsulta } from '@/hooks/useConsulta'
import { FilaAnalisis } from './FilaAnalisis'
import { PreparacionServidor } from './PreparacionServidor'
import { PruebaAcceso } from './PruebaAcceso'

/**
 * Automatización desde la ficha del servidor: qué le falta para el ciclo automático, prueba de acceso, revisión
 * inmediata (RF37) e historial de análisis
 */
export function AnalisisServidor({ idServidor }: { idServidor: number }) {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const ve = esAdmin || tieneRol('OPERADOR')
  const { avisar } = useAvisos()

  const [pagina, setPagina] = useState(0)
  const historial = useConsulta(() => analisisApi.historial(idServidor, pagina), [idServidor, pagina])
  const preparacion = useConsulta(
    async () => (ve ? analisisApi.preparacion(idServidor) : undefined),
    [idServidor, ve],
  )
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
      setPagina(0)
      historial.recargar()
    } catch (e) {
      setError(e)
    } finally {
      setRevisando(false)
    }
  }

  const filas = historial.datos?.contenido ?? []

  return (
    <div className="flex flex-col gap-6">
      {ve && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Tarjeta titulo="Preparación para el ciclo automático">
            <MensajeError error={preparacion.error} onReintentar={preparacion.recargar} />
            {preparacion.cargando && !preparacion.datos ? (
              <Cargando />
            ) : (
              preparacion.datos && <PreparacionServidor preparacion={preparacion.datos} />
            )}
          </Tarjeta>
          {esAdmin && (
            <Tarjeta titulo="Acceso de la cuenta de servicio">
              <p className="mb-3 text-sm text-ink-soft">
                Comprueba la conexión, la autenticación y los permisos de administrador sin ejecutar mantenimientos.
              </p>
              <PruebaAcceso idServidor={idServidor} />
            </Tarjeta>
          )}
        </div>
      )}

      <Tarjeta
        titulo="Análisis del servidor"
        acciones={
          esAdmin && (
            <Boton icono={ScanSearch} cargando={revisando} onClick={revisar}>
              Revisar ahora
            </Boton>
          )
        }
      >
        <MensajeError error={error ?? historial.error} />
        {historial.cargando && !historial.datos ? (
          <Cargando />
        ) : filas.length === 0 ? (
          <p className="text-sm text-ink-soft">
            Aún no se analiza este servidor. Las revisiones periódicas corren según su frecuencia de mantenimiento.
          </p>
        ) : (
          <>
            <ol className="overflow-hidden rounded-lg border border-line">
              {filas.map((a) => (
                <FilaAnalisis key={a.id} analisis={a} conServidor={false} />
              ))}
            </ol>
            <Paginacion
              pagina={pagina}
              totalPaginas={historial.datos?.totalPaginas ?? 0}
              totalElementos={historial.datos?.totalElementos ?? 0}
              onCambiar={setPagina}
            />
          </>
        )}
      </Tarjeta>
    </div>
  )
}
