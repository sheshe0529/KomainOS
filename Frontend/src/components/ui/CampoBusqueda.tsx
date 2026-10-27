import { Search } from 'lucide-react'

interface CampoBusquedaProps {
  valor: string
  onCambiar: (texto: string) => void
  placeholder: string
  'aria-label': string
  className?: string
}

export function CampoBusqueda({ valor, onCambiar, placeholder, 'aria-label': ariaLabel, className = '' }: CampoBusquedaProps) {
  return (
    <div
      className={`flex min-w-64 flex-1 items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2 focus-within:border-accent sm:max-w-md ${className}`}
    >
      <Search className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
      {/* Sin autocompletar: el navegador no debe tomarlo por un campo de usuario */}
      <input
        type="search"
        autoComplete="off"
        value={valor}
        onChange={(e) => onCambiar(e.target.value)}
        placeholder={placeholder}
        aria-label={ariaLabel}
        className="w-full bg-transparent text-sm text-ink placeholder:text-ink-faint focus:outline-none"
      />
    </div>
  )
}
