import { useState } from 'react'
import { Check, Copy, Download } from 'lucide-react'
import { BotonIcono } from '@/components/ui/Boton'
import { descargarTexto } from '@/utils/automatizacion'

interface SalidaTerminalProps {
  salida?: string
  error?: string
  /** Nombre del archivo al descargar, sin extensión */
  nombre?: string
  altoMaximo?: string
}

/** Salida estándar y de error de una ejecución, ya sin secretos (RNF11), con copia y descarga */
export function SalidaTerminal({ salida, error, nombre = 'salida', altoMaximo = '20rem' }: SalidaTerminalProps) {
  const [copiado, setCopiado] = useState(false)
  const texto = [salida, error ? `--- Error ---\n${error}` : ''].filter(Boolean).join('\n')

  async function copiar() {
    try {
      await navigator.clipboard.writeText(texto)
      setCopiado(true)
      window.setTimeout(() => setCopiado(false), 1500)
    } catch {
      // Sin permiso de portapapeles queda la descarga
    }
  }

  if (!salida && !error) return <p className="text-sm text-ink-faint">Sin salida registrada.</p>

  return (
    <div className="relative rounded-lg border border-line bg-panel-muted">
      <div className="absolute right-1.5 top-1.5 flex gap-0.5 rounded-lg bg-panel-muted/90">
        <BotonIcono icono={copiado ? Check : Copy} etiqueta="Copiar salida" onClick={copiar} />
        <BotonIcono icono={Download} etiqueta="Descargar salida" onClick={() => descargarTexto(`${nombre}.txt`, texto)} />
      </div>
      <pre
        style={{ maxHeight: altoMaximo }}
        className="overflow-auto whitespace-pre-wrap break-words p-3 pr-20 font-mono text-xs leading-relaxed text-ink"
      >
        {salida}
        {error && (
          <span className="mt-2 block border-t border-line pt-2 text-danger">
            {error}
          </span>
        )}
      </pre>
    </div>
  )
}
