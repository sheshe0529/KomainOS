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
import { usePaginacion } from '@/hooks/usePaginacion'
import { PreparacionServidor } from './PreparacionServidor'
import { PruebaAcceso } from './PruebaAcceso'
import { TablaAnalisis } from './TablaAnalisis'

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

/** Historial de análisis del servidor (RF37): cada revisión es una fila que abre su detalle */
export function AnalisisServidor({ idServidor }: { idServidor: number }) {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const { avisar } = useAvisos()

  const { pagina, tamano, setPagina, setTamano } = usePaginacion('analisis-servidor')
  const historial = useConsulta(() => analisisApi.historial(idServidor, pagina, tamano), [idServidor, pagina, tamano])
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
        <div className="flex flex-col gap-3">
          <TablaAnalisis analisis={filas} conServidor={false} />
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
    </Tarjeta>
  )
}
