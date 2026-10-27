import { useState } from 'react'
import { Play } from 'lucide-react'
import { scriptsApi } from '@/api/automatizacion'
import { servidoresApi } from '@/api/inventario'
import type { PruebaAnalisisRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { CampoBusqueda } from '@/components/ui/CampoBusqueda'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'
import { useTextoDiferido } from '@/hooks/useTextoDiferido'
import { ResultadoFallo } from './ResultadoFallo'
import { RevisionSalida } from './RevisionSalida'
import { SalidaTerminal } from './SalidaTerminal'

interface ProbarAnalisisModalProps {
  idVersion: number
  numeroVersion?: number
  idVersionSistemaOperativo?: number
  sistemaOperativo?: string
  onCerrar: () => void
}

/** Ejecuta una versión de análisis en un servidor y revisa su salida, sin registrar una evaluación */
export function ProbarAnalisisModal({
  idVersion,
  numeroVersion,
  idVersionSistemaOperativo,
  sistemaOperativo,
  onCerrar,
}: ProbarAnalisisModalProps) {
  const [busqueda, setBusqueda] = useState('')
  const texto = useTextoDiferido(busqueda)
  const [idServidor, setIdServidor] = useState<number>()
  const [resultado, setResultado] = useState<PruebaAnalisisRespuesta>()
  const [error, setError] = useState<unknown>()
  const [probando, setProbando] = useState(false)

  // Solo sirven los servidores activos con la misma versión de sistema operativo que la implementación
  const servidores = useConsulta(
    () => servidoresApi.listar({ texto, estado: 'ACTIVO', tamano: 100, orden: 'hostname,asc' }),
    [texto],
  )
  const compatibles = (servidores.datos?.contenido ?? []).filter(
    (s) => s.versionSistemaOperativo?.id === idVersionSistemaOperativo,
  )

  async function probar() {
    if (!idServidor) return
    setProbando(true)
    setError(undefined)
    setResultado(undefined)
    try {
      setResultado(await scriptsApi.probarAnalisis(idVersion, idServidor))
    } catch (e) {
      setError(e)
    } finally {
      setProbando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho="xl"
      titulo={`Probar el análisis v${numeroVersion ?? ''}`}
      descripcion="Se ejecuta en el servidor con su cuenta de servicio y no registra una evaluación."
      onCerrar={onCerrar}
      pie={
        <>
          <Boton onClick={onCerrar}>Cerrar</Boton>
          <Boton variante="primario" icono={Play} cargando={probando} disabled={!idServidor} onClick={probar}>
            Ejecutar prueba
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <MensajeError error={error ?? servidores.error} />

        <div className="flex flex-col gap-2">
          <CampoBusqueda
            valor={busqueda}
            onCambiar={setBusqueda}
            placeholder="Buscar servidor por host o IP"
            aria-label="Buscar servidor"
            className="sm:max-w-none"
          />
          {servidores.cargando && !servidores.datos ? (
            <Cargando />
          ) : compatibles.length === 0 ? (
            <p className="rounded-lg border border-dashed border-line px-4 py-4 text-center text-sm text-ink-soft">
              Ningún servidor activo con {sistemaOperativo ?? 'este sistema operativo'}
              {texto ? ' coincide con la búsqueda' : ''}.
            </p>
          ) : (
            <div role="radiogroup" aria-label="Servidor" className="max-h-56 overflow-y-auto rounded-lg border border-line">
              {compatibles.map((s) => (
                <label
                  key={s.id}
                  className={`flex cursor-pointer items-center gap-3 border-b border-line px-3 py-2 text-sm last:border-b-0 ${
                    idServidor === s.id ? 'bg-accent-soft' : 'hover:bg-panel-muted'
                  }`}
                >
                  <input
                    type="radio"
                    name="servidor-prueba"
                    checked={idServidor === s.id}
                    onChange={() => setIdServidor(s.id)}
                    className="h-4 w-4 accent-[var(--color-accent)]"
                  />
                  <span className="font-mono text-ink">{s.hostname}</span>
                  <span className="font-mono text-xs text-ink-faint">{s.direccionIp}</span>
                  <span className="ml-auto text-xs text-ink-soft">{s.entorno?.nombre}</span>
                </label>
              ))}
            </div>
          )}
        </div>

        {probando && <Cargando texto="Ejecutando el análisis en el servidor…" />}

        {resultado && (
          <div className="flex flex-col gap-4">
            <ResultadoFallo
              correcto={!!resultado.correcto}
              fallo={resultado.fallo}
              mensaje={resultado.error}
              simulado={resultado.simulado}
              textoCorrecto="El análisis se ejecutó correctamente"
            />
            {resultado.advertencia && (
              <p className="rounded-lg bg-warning-soft px-3 py-2 text-sm text-warning">{resultado.advertencia}</p>
            )}
            {resultado.revision && <RevisionSalida revision={resultado.revision} />}
            <div>
              <h4 className="mb-2 text-sm font-medium text-ink">Salida del script</h4>
              <SalidaTerminal salida={resultado.salida} nombre={`prueba_analisis_v${numeroVersion ?? idVersion}`} />
            </div>
          </div>
        )}
      </div>
    </Modal>
  )
}
