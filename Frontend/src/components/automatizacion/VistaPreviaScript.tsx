import { Download, FileCode2, TriangleAlert } from 'lucide-react'
import type { ScriptPreparadoRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { descargarTexto } from '@/utils/automatizacion'
import type { Lenguaje } from '@/utils/parametrosScript'
import { EditorCodigo } from './EditorCodigo'

interface VistaPreviaScriptProps {
  preparado: ScriptPreparadoRespuesta
  /** Nombre del archivo descargado, sin extensión */
  nombreDescarga?: string
}

/** El archivo exacto que recibe el servidor: el bloque de parámetros destacado y después el código de la versión */
export function VistaPreviaScript({ preparado, nombreDescarga }: VistaPreviaScriptProps) {
  const lenguaje: Lenguaje = preparado.lenguaje === 'powershell' ? 'powershell' : 'bash'
  const archivo = preparado.nombreArchivo ?? (lenguaje === 'powershell' ? 'script.ps1' : 'script.sh')
  const extension = archivo.slice(archivo.lastIndexOf('.'))
  const problemas = preparado.problemas ?? []
  const tieneBloque = !!preparado.lineaInicioParametros && !!preparado.lineaFinParametros

  return (
    <div className="flex flex-col gap-3">
      {problemas.length > 0 && (
        <div role="alert" className="flex items-start gap-2 rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">
          <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <ul className="flex flex-col gap-0.5">
            {problemas.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="flex items-center gap-2 text-sm text-ink-soft">
          <FileCode2 className="h-4 w-4" aria-hidden="true" />
          <span className="font-mono text-ink">{archivo}</span>
          {tieneBloque && (
            <span className="inline-flex items-center gap-1.5 text-xs">
              <span className="h-3 w-1 rounded-sm bg-accent" aria-hidden="true" />
              líneas {preparado.lineaInicioParametros}–{preparado.lineaFinParametros}: parámetros que agrega KomainOS
            </span>
          )}
        </p>
        <Boton
          icono={Download}
          onClick={() => descargarTexto(`${nombreDescarga ?? archivo.replace(extension, '')}${extension}`, preparado.contenido ?? '')}
        >
          Descargar para probar a mano
        </Boton>
      </div>

      <EditorCodigo
        valor={preparado.contenido ?? ''}
        lenguaje={lenguaje}
        soloLectura
        resaltar={
          tieneBloque
            ? { desde: preparado.lineaInicioParametros!, hasta: preparado.lineaFinParametros! }
            : undefined
        }
        aria-label="Vista previa del script preparado"
      />
    </div>
  )
}
