import { useState } from 'react'
import { ArrowLeft, ScanSearch, Upload } from 'lucide-react'
import { TAMANO_MAXIMO_BYTES, intercambioApi, type FormatoArchivo } from '@/api/intercambio'
import type { AnalisisImportacionRespuesta, ResultadoImportacionRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { useConsulta } from '@/hooks/useConsulta'
import { PasoArchivo } from './importacion/PasoArchivo'
import { ResultadoImportacionVista } from './importacion/ResultadoImportacionVista'
import { VistaPreviaImportacion } from './importacion/VistaPreviaImportacion'

interface ImportarModalProps {
  abierto: boolean
  onCerrar: () => void
  /** Se llama al cerrar después de una importación, para recargar el listado */
  onImportado: () => void
}

type Paso = 'archivo' | 'vista' | 'resultado'

const TITULOS: Record<Paso, string> = {
  archivo: 'Importar servidores',
  vista: 'Vista previa de la importación',
  resultado: 'Resultado de la importación',
}

function textoConfirmar(nuevos: number, sobrescritos: number): string {
  const partes = []
  if (nuevos > 0) partes.push(`${nuevos} ${nuevos === 1 ? 'nuevo' : 'nuevos'}`)
  if (sobrescritos > 0) partes.push(`sobrescribir ${sobrescritos}`)
  return partes.length ? `Importar ${partes.join(' y ')}` : 'Nada que importar'
}

/** Tres pasos: elegir el archivo, revisar la vista previa y ver el resultado (RF12, HU08) */
export function ImportarModal({ abierto, onCerrar, onImportado }: ImportarModalProps) {
  const columnas = useConsulta(() => intercambioApi.columnas(), [])
  const [paso, setPaso] = useState<Paso>('archivo')
  const [archivo, setArchivo] = useState<File | null>(null)
  const [formatoPlantilla, setFormatoPlantilla] = useState<FormatoArchivo>('XLSX')
  const [analisis, setAnalisis] = useState<AnalisisImportacionRespuesta | null>(null)
  const [resultado, setResultado] = useState<ResultadoImportacionRespuesta | null>(null)
  const [sobrescribir, setSobrescribir] = useState<number[]>([])
  const [trabajando, setTrabajando] = useState(false)
  const [error, setError] = useState<unknown>()

  function elegir(nuevo: File | undefined) {
    setError(undefined)
    if (!nuevo) return
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
    try {
      await accion()
    } catch (e) {
      setError(e)
    } finally {
      setTrabajando(false)
    }
  }

  const analizar = () =>
    ejecutar(async () => {
      setAnalisis(await intercambioApi.analizar(archivo!))
      setSobrescribir([])
      setPaso('vista')
    })

  const importar = () =>
    ejecutar(async () => {
      setResultado(await intercambioApi.importar(archivo!, sobrescribir))
      setPaso('resultado')
    })

  function cerrar() {
    if (resultado) onImportado()
    onCerrar()
  }

  const nuevos = analisis?.nuevos ?? 0
  const pie = {
    archivo: (
      <>
        <Boton onClick={cerrar}>Cancelar</Boton>
        <Boton variante="primario" icono={ScanSearch} cargando={trabajando} disabled={!archivo} onClick={analizar}>
          Analizar archivo
        </Boton>
      </>
    ),
    vista: (
      <>
        <Boton variante="fantasma" icono={ArrowLeft} onClick={() => setPaso('archivo')} className="mr-auto">
          Elegir otro archivo
        </Boton>
        <Boton onClick={cerrar}>Cancelar</Boton>
        <Boton variante="primario" icono={Upload} cargando={trabajando} disabled={nuevos === 0 && sobrescribir.length === 0} onClick={importar}>
          {textoConfirmar(nuevos, sobrescribir.length)}
        </Boton>
      </>
    ),
    resultado: (
      <Boton variante="primario" onClick={cerrar}>
        Cerrar
      </Boton>
    ),
  }[paso]

  return (
    <Modal abierto={abierto} onCerrar={cerrar} titulo={TITULOS[paso]} ancho="xl" pie={pie}>
      <div className="flex flex-col gap-5">
        <MensajeError error={error} />
        {paso === 'archivo' && (
          <PasoArchivo
            archivo={archivo}
            onElegir={elegir}
            columnas={columnas.datos ?? []}
            formatoPlantilla={formatoPlantilla}
            onFormatoPlantilla={setFormatoPlantilla}
            onDescargarPlantilla={() => ejecutar(async () => void (await intercambioApi.plantilla(formatoPlantilla)))}
            trabajando={trabajando}
          />
        )}
        {paso === 'vista' && analisis && (
          <VistaPreviaImportacion analisis={analisis} sobrescribir={sobrescribir} onSobrescribir={setSobrescribir} />
        )}
        {paso === 'resultado' && resultado && <ResultadoImportacionVista resultado={resultado} />}
      </div>
    </Modal>
  )
}
