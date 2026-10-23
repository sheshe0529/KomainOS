interface ListaChipsProps {
  elementos?: string[]
  /** Cuántos se muestran; el resto se resume en un "+N" con el detalle en el tooltip */
  maximo?: number
  atenuado?: boolean
}

/** Los elementos de una lista como etiquetas compactas */
export function ListaChips({ elementos, maximo = 8, atenuado }: ListaChipsProps) {
  const lista = elementos ?? []
  return (
    <span className="inline-flex flex-wrap items-center gap-1">
      {lista.slice(0, maximo).map((e) => (
        <span key={e} className={`rounded-md bg-panel-muted px-1.5 py-0.5 font-mono text-xs ${atenuado ? 'text-ink-faint' : 'text-ink'}`}>
          {e}
        </span>
      ))}
      {lista.length > maximo && (
        <span className="text-xs text-ink-faint" title={lista.slice(maximo).join(', ')}>
          +{lista.length - maximo}
        </span>
      )}
    </span>
  )
}
