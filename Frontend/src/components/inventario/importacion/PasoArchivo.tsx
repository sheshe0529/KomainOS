import { useState } from 'react'
import type { DragEvent } from 'react'
import { Download, FileSpreadsheet } from 'lucide-react'
import { FORMATOS, type FormatoArchivo } from '@/api/intercambio'
import type { ColumnaInventarioRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'

const EXTENSIONES = '.xlsx,.csv,.yaml,.yml,.json'

function tamanoLegible(bytes: number): string {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

interface PasoArchivoProps {
  archivo: File | null
  onElegir: (archivo: File | undefined) => void
  columnas: ColumnaInventarioRespuesta[]
  formatoPlantilla: FormatoArchivo
  onFormatoPlantilla: (formato: FormatoArchivo) => void
  onDescargarPlantilla: () => void
  trabajando: boolean
}

export function PasoArchivo({ archivo, onElegir, columnas, formatoPlantilla, onFormatoPlantilla, onDescargarPlantilla, trabajando }: PasoArchivoProps) {
  const [arrastrando, setArrastrando] = useState(false)
  const importables = columnas.filter((c) => c.importable)
  const obligatorias = importables.filter((c) => c.obligatoria).map((c) => c.etiqueta)
  const opcionales = importables.filter((c) => !c.obligatoria).map((c) => c.etiqueta)

  function alSoltar(evento: DragEvent<HTMLLabelElement>) {
    evento.preventDefault()
    setArrastrando(false)
    onElegir(evento.dataTransfer.files[0])
  }

  return (
    <>
      <label
        onDragOver={(e) => {
          e.preventDefault()
          setArrastrando(true)
        }}
        onDragLeave={() => setArrastrando(false)}
        onDrop={alSoltar}
        className={`flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed px-6 py-8 text-center transition ${
          arrastrando ? 'border-accent bg-accent-soft' : 'border-line hover:bg-panel-muted'
        }`}
      >
        <FileSpreadsheet className="h-8 w-8 text-accent" aria-hidden="true" />
        {archivo ? (
          <>
            <span className="font-mono text-sm font-medium text-ink">{archivo.name}</span>
            <span className="text-xs text-ink-soft">{tamanoLegible(archivo.size)} · haga clic para elegir otro</span>
          </>
        ) : (
          <>
            <span className="text-sm font-medium text-ink">Arrastre un archivo o haga clic para elegirlo</span>
            <span className="text-xs text-ink-soft">XLSX, CSV, YAML o JSON · hasta 5 MB y 5000 registros</span>
          </>
        )}
        <input
          type="file"
          accept={EXTENSIONES}
          className="sr-only"
          onChange={(e) => {
            onElegir(e.target.files?.[0])
            e.target.value = ''
          }}
        />
      </label>

      <div className="grid gap-4 rounded-lg bg-panel-muted p-4 text-sm sm:grid-cols-[1fr_auto]">
        <div className="flex flex-col gap-1.5">
          <p className="text-ink">
            <span className="font-medium">Columnas obligatorias:</span> {obligatorias.join(', ')}.
          </p>
          <p className="text-ink-soft">
            <span className="font-medium">Opcionales:</span> {opcionales.join(', ')}.
          </p>
          <p className="text-xs text-ink-soft">
            El sistema operativo, el entorno y la criticidad se indican por su nombre, y el responsable por su código de usuario.
            Los registros nuevos quedan pendientes de configuración. Al sobrescribir un servidor, las columnas que no estén en el
            archivo conservan su valor.
          </p>
          <p className="text-xs text-ink-soft">
            <span className="font-medium text-ink">Credencial principal (opcional, al final):</span> Mecanismo de acceso, Tipo de
            usuario, Usuario de acceso, Contraseña o llave y Contraseña su (root). Si el usuario ya existe en el servidor se registra
            una versión nueva de su secreto y pasa a ser la principal. Las celdas vacías no borran ninguna credencial y la vista
            previa nunca muestra los secretos.
          </p>
        </div>
        <div className="flex items-end gap-2 sm:flex-col sm:items-stretch">
          <Selector aria-label="Formato de la plantilla" value={formatoPlantilla} onChange={(e) => onFormatoPlantilla(e.target.value as FormatoArchivo)}>
            {FORMATOS.map((f) => (
              <option key={f.valor} value={f.valor}>
                {f.etiqueta}
              </option>
            ))}
          </Selector>
          <Boton icono={Download} onClick={onDescargarPlantilla} disabled={trabajando}>
            Plantilla
          </Boton>
        </div>
      </div>
    </>
  )
}
