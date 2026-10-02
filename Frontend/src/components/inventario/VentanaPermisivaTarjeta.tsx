import { CalendarClock } from 'lucide-react'
import type { VentanaRespuesta } from '@/api/types'
import { Tarjeta } from '@/components/common/Tarjeta'
import { Boton } from '@/components/ui/Boton'
import { textoVentana } from '@/utils/etiquetas'
import { formatearDuracion } from '@/utils/formato'
import { VistaSemanalVentanas } from './VistaSemanalVentanas'

interface VentanaPermisivaTarjetaProps {
  ventanas: VentanaRespuesta[]
  vacio: string
  titulo?: string
  descripcion?: string
  /** Sin él no se muestra el botón de edición */
  onEditar?: () => void
}

/** Vista semanal de la ventana permisiva y la lista de sus intervalos (RF18) */
export function VentanaPermisivaTarjeta({ ventanas, vacio, titulo = 'Ventana permisiva', descripcion, onEditar }: VentanaPermisivaTarjetaProps) {
  return (
    <Tarjeta
      titulo={titulo}
      acciones={
        onEditar && (
          <Boton icono={CalendarClock} variante="fantasma" onClick={onEditar}>
            Editar
          </Boton>
        )
      }
    >
      {descripcion && <p className="mb-3 text-xs text-ink-soft">{descripcion}</p>}
      <VistaSemanalVentanas ventanas={ventanas} vacio={vacio} />
      {ventanas.length > 0 && (
        <ul className="mt-4 flex flex-wrap gap-2">
          {ventanas.map((v, i) => (
            <li key={i} className="flex items-center gap-2 rounded-lg bg-panel-muted px-3 py-1.5 text-sm">
              <span className="text-ink">{textoVentana(v)}</span>
              <span className="font-mono text-xs text-ink-faint">{formatearDuracion(v.duracionMinutos)}</span>
            </li>
          ))}
        </ul>
      )}
    </Tarjeta>
  )
}
