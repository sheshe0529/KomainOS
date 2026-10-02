import { LoaderCircle } from 'lucide-react'

interface CargandoProps {
  texto?: string
  pantallaCompleta?: boolean
}

export function Cargando({ texto = 'Cargando…', pantallaCompleta = false }: CargandoProps) {
  return (
    <div
      role="status"
      className={`flex items-center justify-center gap-2 text-sm text-ink-soft ${
        pantallaCompleta ? 'min-h-dvh' : 'py-12'
      }`}
    >
      <LoaderCircle className="h-5 w-5 animate-spin text-accent" aria-hidden="true" />
      {texto}
    </div>
  )
}
