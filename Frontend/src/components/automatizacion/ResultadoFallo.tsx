import { CircleCheck, CircleX, TriangleAlert } from 'lucide-react'
import type { TipoFallo } from '@/api/dominio'
import { detalleFallo, diagnosticarResultado } from '@/utils/automatizacion'

interface ResultadoFalloProps {
  correcto: boolean
  fallo?: TipoFallo
  mensaje?: string
  textoCorrecto: string
  /** Qué se probó, junto al título del fallo (por ejemplo, la cuenta) */
  textoFallido?: string
  /** Para qué se usa lo probado (por ejemplo, «Evaluación y mantenimiento») */
  etiqueta?: string
  /** Correcto, pero con algo que conviene revisar: se muestra en ámbar en lugar de verde */
  advertencia?: string
}

/**
 * RF71: el resultado distingue comunicación, autenticación y permisos, y sugiere qué revisar. Cuando el mensaje revela
 * la causa precisa (por ejemplo, un certificado WinRM no confiable), la explica y el mensaje técnico queda plegado
 */
export function ResultadoFallo({ correcto, fallo, mensaje, textoCorrecto, textoFallido, etiqueta, advertencia }: ResultadoFalloProps) {
  const tono = !correcto ? 'bg-danger-soft text-danger' : advertencia ? 'bg-warning-soft text-warning' : 'bg-success-soft text-success'
  const Icono = !correcto ? CircleX : advertencia ? TriangleAlert : CircleCheck
  const diagnostico = correcto ? undefined : diagnosticarResultado(fallo, mensaje)
  const tecnico = detalleFallo(fallo, mensaje)
  const detalle = diagnostico ? diagnostico.detalle : tecnico
  const titulo = diagnostico?.titulo ?? 'No se completó'
  return (
    <div role="status" className={`flex items-start gap-3 rounded-lg px-4 py-3 text-sm ${tono}`}>
      <Icono className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
          <p className="font-medium [overflow-wrap:anywhere]">
            {correcto ? textoCorrecto : textoFallido ? `${titulo} con ${textoFallido}` : titulo}
          </p>
          {etiqueta && <span className="text-xs font-medium text-ink-soft">{etiqueta}</span>}
        </div>
        {detalle && <p className="whitespace-pre-wrap break-words text-ink">{detalle}</p>}
        {correcto && advertencia && <p className="text-ink-soft">{advertencia}</p>}
        {diagnostico?.sugerencia && <p className="text-ink-soft">{diagnostico.sugerencia}</p>}
        {!correcto && tecnico && tecnico !== detalle && (
          <details className="text-xs">
            <summary className="w-fit cursor-pointer rounded font-medium text-ink-soft hover:text-ink">Mensaje técnico</summary>
            <p className="mt-1 whitespace-pre-wrap break-words font-mono text-ink-soft">{tecnico}</p>
          </details>
        )}
      </div>
    </div>
  )
}
