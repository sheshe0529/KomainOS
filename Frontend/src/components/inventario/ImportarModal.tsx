import { useState } from 'react'
import { ArrowLeft, ArrowRight, Check, ScanSearch } from 'lucide-react'
import { TAMANO_MAXIMO_BYTES, intercambioApi, type FormatoArchivo } from '@/api/intercambio'
import { servidoresApi } from '@/api/inventario'
import type { AnalisisImportacionRespuesta, ResultadoImportacionRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { PasoArchivo } from './importacion/PasoArchivo'
import { ResultadoImportacionVista } from './importacion/ResultadoImportacionVista'
import { VistaPreviaImportacion } from './importacion/VistaPreviaImportacion'
import { MantenimientoPaso } from './registro/MantenimientoPaso'
import { PasosRegistro } from './registro/PasosRegistro'
import { PlanificacionPaso } from './registro/PlanificacionPaso'
import { filasSeleccionadas, prepararServidor, problemaMantenimiento, type ServidorPreparado } from './registro/preparacion'
import { ResumenRegistro } from './registro/ResumenRegistro'

interface ImportarModalProps {
  abierto: boolean
  onCerrar: () => void
  onImportado: () => void
}

/** RF12, HU08: cinco etapas de preparación; solo la confirmación final modifica el inventario. */
export function ImportarModal({ abierto, onCerrar, onImportado }: ImportarModalProps) {
  const columnas = useConsulta(() => intercambioApi.columnas(), [])
  const catalogos = useCatalogos()
  const [paso, setPaso] = useState(0)
  const [archivo, setArchivo] = useState<File | null>(null)
  const [formatoPlantilla, setFormatoPlantilla] = useState<FormatoArchivo>('XLSX')
  const [analisis, setAnalisis] = useState<AnalisisImportacionRespuesta | null>(null)
  const [resultado, setResultado] = useState<ResultadoImportacionRespuesta | null>(null)
  const [sobrescribir, setSobrescribir] = useState<number[]>([])
  const [servidores, setServidores] = useState<ServidorPreparado[]>([])
  const [revisado, setRevisado] = useState(false)
  const [trabajando, setTrabajando] = useState(false)
  const [error, setError] = useState<unknown>()
  const elegidas = filasSeleccionadas(analisis?.filas ?? [], sobrescribir)
  const omitidos = (analisis?.registros ?? 0) - servidores.length

  function elegir(nuevo: File | undefined) {
    setError(undefined)
    if (!nuevo) return
    setAnalisis(null)
    setServidores([])
    setRevisado(false)
    if (nuevo.size > TAMANO_MAXIMO_BYTES) {
      setArchivo(null)
      setError(new Error('El archivo supera el tamaño máximo permitido de 5 MB'))
      return
    }
    setArchivo(nuevo)
  }

  async function ejecutar(accion: () => Promise<void>) {
    setTrabajando(true)
    setError(undefined)
    try { await accion() } catch (e) { setError(e) } finally { setTrabajando(false) }
  }

  const analizar = () => ejecutar(async () => {
    setAnalisis(await intercambioApi.analizar(archivo!))
    setSobrescribir([])
    setServidores([])
    setRevisado(false)
    setPaso(1)
  })

  const continuar = () => ejecutar(async () => {
    if (paso === 1) {
      if (!catalogos.datos) throw new Error('Espere a que se carguen los catálogos.')
      const anteriores = new Map(servidores.map((s) => [s.clave, s]))
      const prepararFila = async (f: (typeof elegidas)[number]) => {
        if (anteriores.has(f.fila!)) return anteriores.get(f.fila!)!
        const existente = f.servidorExistente?.id ? await servidoresApi.ficha(f.servidorExistente.id) : undefined
        const preparado = prepararServidor({ hostname: f.hostname!, direccionIp: f.direccionIp!,
          idNivelCriticidad: f.idNivelCriticidad!, idVersionSistemaOperativo: f.idVersionSistemaOperativo! }, catalogos.datos!, f.fila!, existente)
        // La versión corresponde a los atributos analizados en el archivo, no a una lectura posterior.
        return { ...preparado, versionServidor: f.versionServidor }
      }
      const preparados: ServidorPreparado[] = []
      // Limita las lecturas simultáneas de fichas en importaciones grandes.
      for (let i = 0; i < elegidas.length; i += 8) {
        preparados.push(...await Promise.all(elegidas.slice(i, i + 8).map(prepararFila)))
      }
      setServidores(preparados)
    }
    if (paso === 2) {
      const problema = servidores.map(problemaMantenimiento).find(Boolean)
      if (problema) throw new Error(problema)
    }
    setPaso(paso + 1)
  })

  const importar = () => ejecutar(async () => {
    setResultado(await intercambioApi.importar(archivo!, sobrescribir, servidores.map((s) => ({
      fila: s.clave, idServidorExistente: s.idServidorExistente, versionServidor: s.versionServidor, mantenimiento: s.mantenimiento,
    }))))
    onImportado()
  })

  function cerrar() { if (!trabajando) onCerrar() }

  return <Modal abierto={abierto} onCerrar={cerrar} titulo={resultado ? 'Resultado de la importación' : 'Importar servidores'} ancho="xl"
    descripcion={resultado ? undefined : 'Revise los servidores y prepare su mantenimiento antes de confirmar.'}
    pie={resultado ? <Boton variante="primario" onClick={cerrar}>Cerrar</Boton> : <>
      {paso > 0 && <Boton variante="fantasma" icono={ArrowLeft} disabled={trabajando} onClick={() => { setError(undefined); setPaso(paso - 1) }} className="mr-auto">{paso === 1 ? 'Volver al archivo' : 'Atrás'}</Boton>}
      <Boton onClick={cerrar} disabled={trabajando}>Cancelar</Boton>
      {paso === 0 ? <Boton variante="primario" icono={ScanSearch} cargando={trabajando} disabled={!archivo} onClick={analizar}>Analizar archivo</Boton>
        : paso === 4 ? <Boton variante="primario" icono={Check} cargando={trabajando} onClick={importar}>Confirmar importación</Boton>
          : <Boton variante="primario" icono={ArrowRight} cargando={trabajando} disabled={paso === 1 && (!revisado || !elegidas.length || !!analisis?.erroneos || !catalogos.datos)} onClick={continuar}>Continuar</Boton>}
    </>}>
    <div className="flex flex-col gap-5">
      {!resultado && <PasosRegistro paso={paso} archivo />}
      <MensajeError error={error} />
      {!resultado && <MensajeError error={catalogos.error} onReintentar={catalogos.recargar} />}
      {resultado ? <ResultadoImportacionVista resultado={resultado} /> : <>
        {paso === 0 && <PasoArchivo archivo={archivo} onElegir={elegir} columnas={columnas.datos ?? []} formatoPlantilla={formatoPlantilla} onFormatoPlantilla={setFormatoPlantilla} onDescargarPlantilla={() => ejecutar(async () => void (await intercambioApi.plantilla(formatoPlantilla)))} trabajando={trabajando} />}
        {paso === 1 && analisis && <>
          <VistaPreviaImportacion analisis={analisis} sobrescribir={sobrescribir} onSobrescribir={(filas) => { setSobrescribir(filas); setRevisado(false) }} />
          {!!analisis.erroneos && <p role="alert" className="rounded-lg bg-danger-soft p-3 text-sm text-danger">Corrija los registros erróneos y vuelva a analizar el archivo para continuar.</p>}
          <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={revisado} disabled={!!analisis.erroneos || !elegidas.length} onChange={(e) => setRevisado(e.target.checked)} className="mt-0.5 accent-[var(--color-accent)]" /><span>He revisado el análisis. Confirmo {elegidas.length} servidores para configurar y {(analisis.registros ?? 0) - elegidas.length} registros que se omitirán.</span></label>
        </>}
        {paso === 2 && <MantenimientoPaso servidores={servidores} onCambiar={setServidores} />}
        {paso === 3 && <PlanificacionPaso servidores={servidores} onCambiar={setServidores} />}
        {paso === 4 && <ResumenRegistro servidores={servidores} omitidos={omitidos} />}
      </>}
    </div>
  </Modal>
}
