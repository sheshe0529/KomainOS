import { Pencil, Power, PowerOff, Trash2 } from 'lucide-react'
import type { AlcanceExcepcion } from '@/api/dominio'
import type { ExcepcionUmbralRespuesta } from '@/api/types'
import { BotonIcono } from '@/components/ui/Boton'

/** A quién se le definen valores propios: un servidor, un grupo, un entorno o una criticidad */
export interface Destino {
  alcance: AlcanceExcepcion
  id: number
  nombre: string
}

export function destinoDe(e: ExcepcionUmbralRespuesta): Destino {
  return { alcance: e.alcance!, id: e.objetivo?.id ?? 0, nombre: e.objetivo?.nombre ?? '' }
}

/** Lo que el administrador puede hacer sobre una excepción */
export interface AccionesExcepcion {
  ocupado: boolean
  definir: (destino: Destino) => void
  editar: (excepcion: ExcepcionUmbralRespuesta) => void
  alternar: (excepcion: ExcepcionUmbralRespuesta) => void
  eliminar: (excepcion: ExcepcionUmbralRespuesta) => void
}

interface BotonesExcepcionProps {
  excepcion: ExcepcionUmbralRespuesta
  acciones: AccionesExcepcion
}

/** El tooltip lleva el nombre del destino: "Editar Producción" dice más que un "Editar" repetido en cada fila */
export function BotonesExcepcion({ excepcion, acciones }: BotonesExcepcionProps) {
  const nombre = excepcion.objetivo?.nombre ?? ''
  return (
    <div className="flex justify-end gap-1">
      <BotonIcono icono={Pencil} etiqueta={`Editar ${nombre}`} onClick={() => acciones.editar(excepcion)} />
      <BotonIcono
        icono={excepcion.activo ? PowerOff : Power}
        etiqueta={`${excepcion.activo ? 'Desactivar' : 'Activar'} ${nombre}`}
        disabled={acciones.ocupado}
        onClick={() => acciones.alternar(excepcion)}
      />
      <BotonIcono icono={Trash2} etiqueta={`Quitar ${nombre}`} onClick={() => acciones.eliminar(excepcion)} />
    </div>
  )
}
