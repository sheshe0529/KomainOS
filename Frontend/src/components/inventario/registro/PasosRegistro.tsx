const ETAPAS = ['Datos', 'Análisis', 'Mantenimiento', 'Planificación', 'Resumen']

export function PasosRegistro({ paso, archivo = false }: { paso: number; archivo?: boolean }) {
  return (
    <nav aria-label="Etapas del registro">
      <ol className="grid grid-cols-5 gap-1.5 border-b border-line pb-5">
        {ETAPAS.map((etapa, i) => (
          <li key={etapa} aria-current={paso === i ? 'step' : undefined}
            className={`flex min-w-0 flex-col items-center gap-2 text-center text-xs sm:flex-row sm:text-left ${paso === i ? 'font-semibold text-ink' : 'text-ink-soft'}`}>
            <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${i <= paso ? 'bg-accent text-accent-ink' : 'bg-panel-muted'}`}>{i + 1}</span>
            <span className="sr-only sm:not-sr-only">{i === 0 && archivo ? 'Archivo' : etapa}</span>
          </li>
        ))}
      </ol>
      <p className="mt-3 text-sm font-medium sm:hidden">Paso {paso + 1} de 5 · {paso === 0 && archivo ? 'Archivo' : ETAPAS[paso]}</p>
    </nav>
  )
}
