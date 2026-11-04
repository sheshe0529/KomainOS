import { Check, Monitor, Moon, Sun } from 'lucide-react'
import { useTheme, type PreferenciaTema } from '@/theme/theme-context'

const TEMAS: { id: PreferenciaTema; titulo: string; descripcion: string; icono: typeof Sun }[] = [
  { id: 'light', titulo: 'Claro', descripcion: 'Fondo claro, alto contraste para ambientes iluminados.', icono: Sun },
  { id: 'dark', titulo: 'Oscuro', descripcion: 'Fondo oscuro, ideal para sala de operaciones y turnos nocturnos.', icono: Moon },
  {
    id: 'system',
    titulo: 'Sistema',
    descripcion: 'Sigue el tema del sistema operativo y cambia junto con él.',
    icono: Monitor,
  },
]

/** Tema de la interfaz: se guarda para la cuenta en este equipo */
export function SelectorTema() {
  const { theme, preferencia, setPreferencia } = useTheme()
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      {TEMAS.map(({ id, titulo, descripcion, icono: Icono }) => {
        const activo = preferencia === id
        return (
          <button
            key={id}
            type="button"
            aria-pressed={activo}
            onClick={() => setPreferencia(id)}
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
              {id === 'system' && activo && (
                <span className="mt-1 block text-xs text-ink-faint">Ahora: {theme === 'dark' ? 'oscuro' : 'claro'}</span>
              )}
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
  )
}
