import { Link } from 'react-router-dom'

export function NoEncontradoPage() {
  return (
    <div className="flex flex-col items-center gap-3 py-20 text-center">
      <p className="font-mono text-5xl font-semibold text-ink-faint">404</p>
      <h2 className="text-lg font-semibold text-ink">Página no encontrada</h2>
      <p className="text-sm text-ink-soft">La dirección no existe o no está disponible para su rol.</p>
      <Link to="/" className="mt-2 text-sm font-medium text-accent hover:underline">
        Ir al inicio
      </Link>
    </div>
  )
}
