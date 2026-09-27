import { Check, Moon, Sun } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { useTheme, type Theme } from '@/theme/theme-context'

const TEMAS: { id: Theme; titulo: string; descripcion: string; icono: typeof Sun }[] = [
  { id: 'light', titulo: 'Claro', descripcion: 'Fondo claro, alto contraste para ambientes iluminados.', icono: Sun },
  { id: 'dark', titulo: 'Oscuro', descripcion: 'Fondo oscuro, ideal para sala de operaciones y turnos nocturnos.', icono: Moon },
]

/** Preferencias de la interfaz para la cuenta en este equipo. */
export function PreferenciasPage() {
  const { theme, setTheme } = useTheme()
  return (
    <>
      <PageHeader title="Preferencias" description="Ajustes de la interfaz para su cuenta en este equipo." />
      <h3 className="mb-3 text-sm font-semibold text-ink">Tema de la interfaz</h3>
      <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
        {TEMAS.map(({ id, titulo, descripcion, icono: Icono }) => {
          const activo = theme === id
          return (
            <button
              key={id}
              type="button"
              aria-pressed={activo}
              onClick={() => setTheme(id)}
              className={`relative flex flex-col items-start gap-3 rounded-xl border bg-panel p-5 text-left transition-colors ${
                activo ? 'border-accent bg-accent-soft' : 'border-line hover:bg-panel-muted'
              }`}
            >
              <span className={`flex h-10 w-10 items-center justify-center rounded-lg ${activo ? 'bg-accent text-accent-ink' : 'bg-panel-muted text-ink-soft'}`}>
                <Icono className="h-5 w-5" aria-hidden="true" />
              </span>
              <span>
                <span className="block font-semibold text-ink">{titulo}</span>
                <span className="block text-xs text-ink-soft">{descripcion}</span>
              </span>
              {activo && (
                <span className="absolute right-4 top-4 flex h-6 w-6 items-center justify-center rounded-full bg-accent text-accent-ink">
                  <Check className="h-4 w-4" aria-hidden="true" />
                </span>
              )}
            </button>
          )
        })}
      </div>
    </>
  )
}
