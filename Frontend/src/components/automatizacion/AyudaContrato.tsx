import { useState } from 'react'
import { Check, Copy, Info } from 'lucide-react'
import type { TipoScript } from '@/api/dominio'
import type { Lenguaje } from '@/utils/parametrosScript'
import { EJEMPLO_SALIDA } from '@/utils/plantillasScript'

interface AyudaContratoProps {
  tipo: TipoScript
  lenguaje: Lenguaje
}

/** Reglas que KomainOS aplica a cualquier script, a la vista mientras se escribe */
export function AyudaContrato({ tipo, lenguaje }: AyudaContratoProps) {
  const [copiado, setCopiado] = useState(false)
  const ejemploJson = EJEMPLO_SALIDA.split('\n').at(-1) ?? ''

  async function copiar() {
    try {
      await navigator.clipboard.writeText(ejemploJson)
      setCopiado(true)
      window.setTimeout(() => setCopiado(false), 1500)
    } catch {
      // Sin permiso de portapapeles el ejemplo sigue a la vista para copiarlo a mano
    }
  }

  return (
    <details className="group rounded-lg border border-line bg-panel-muted/50 text-sm" open>
      <summary className="flex cursor-pointer list-none items-center gap-2 px-4 py-3 font-medium text-ink">
        <Info className="h-4 w-4 text-ink-soft" aria-hidden="true" />
        Cómo escribir el script
        <span className="ml-auto text-xs font-normal text-ink-faint group-open:hidden">Mostrar</span>
        <span className="ml-auto hidden text-xs font-normal text-ink-faint group-open:inline">Ocultar</span>
      </summary>
      <div className="flex flex-col gap-3 border-t border-line px-4 py-3 text-ink-soft">
        <ul className="flex list-disc flex-col gap-1.5 pl-5">
          <li>
            Cada parámetro llega como variable ya definida:{' '}
            <code className="font-mono text-ink">{lenguaje === 'bash' ? '"$DIAS_RETENCION"' : '$DIAS_RETENCION'}</code>.
            {lenguaje === 'powershell' && ' No declare un bloque param().'}
          </li>
          <li>
            Termine con código <code className="font-mono text-ink">0</code> si todo salió bien; cualquier otro código
            marca la tarea como fallida y abre una incidencia.
          </li>
          <li>
            La salida estándar se guarda en la bitácora y la de error se muestra como causa del fallo. KomainOS
            oculta contraseñas y llaves antes de guardarlas.
          </li>
          <li>
            Se ejecuta con la cuenta de servicio del servidor y se detiene al superar la duración estimada de la tarea.
            {lenguaje === 'bash' &&
              ' Corre como root si la cuenta tiene clave de su; si no, anteponga sudo a los comandos que lo requieran (la prueba de acceso exige sudo sin contraseña).'}
          </li>
          {lenguaje === 'powershell' && (
            <li>
              Debe funcionar en Windows PowerShell 5.1; el archivo se envía en UTF-8 con BOM para conservar las tildes.
            </li>
          )}
          {tipo === 'ANALISIS' && (
            <li>
              Un análisis solo lee: no modifica el servidor. Su <strong className="text-ink">última línea</strong> es un
              JSON con las métricas, cuyos códigos coinciden con el catálogo de métricas.
            </li>
          )}
        </ul>
        {tipo === 'ANALISIS' && (
          <div className="relative">
            <pre className="overflow-x-auto rounded-lg border border-line bg-panel p-3 pr-12 font-mono text-xs text-ink">
              {ejemploJson}
            </pre>
            <button
              type="button"
              onClick={copiar}
              aria-label="Copiar ejemplo"
              title="Copiar ejemplo"
              className="absolute right-2 top-2 rounded-md p-1.5 text-ink-soft hover:bg-panel-muted hover:text-ink"
            >
              {copiado ? <Check className="h-4 w-4" aria-hidden="true" /> : <Copy className="h-4 w-4" aria-hidden="true" />}
            </button>
            <p className="mt-2 text-xs">
              <code className="font-mono">valor</code> para métricas numéricas, <code className="font-mono">elementos</code>{' '}
              para las de lista e <code className="font-mono">instancia</code> opcional, por ejemplo el disco o la
              interfaz.
            </p>
          </div>
        )}
      </div>
    </details>
  )
}
