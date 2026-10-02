import { Children, Fragment, isValidElement, useCallback, useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent, ReactElement, ReactNode } from 'react'
import { Check, ChevronDown } from 'lucide-react'

interface Opcion {
  valor: string
  etiqueta: ReactNode
  texto: string
  deshabilitada: boolean
}

export interface SelectorProps {
  value?: string | number
  /** Misma forma que el onChange de un select nativo, para no cambiar a quien lo usa */
  onChange?: (evento: { target: { value: string } }) => void
  /** Elementos <option>, como en un select nativo */
  children: ReactNode
  required?: boolean
  disabled?: boolean
  id?: string
  className?: string
  'aria-label'?: string
}

const ALTO_MAXIMO = 288

function textoDe(nodo: ReactNode): string {
  if (typeof nodo === 'string' || typeof nodo === 'number') return String(nodo)
  if (Array.isArray(nodo)) return nodo.map(textoDe).join('')
  if (isValidElement(nodo)) return textoDe((nodo.props as { children?: ReactNode }).children)
  return ''
}

function extraerOpciones(children: ReactNode): Opcion[] {
  const opciones: Opcion[] = []
  const recorrer = (nodos: ReactNode) =>
    Children.forEach(nodos, (nodo) => {
      if (!isValidElement(nodo)) return
      const elemento = nodo as ReactElement<{ value?: string | number; children?: ReactNode; disabled?: boolean }>
      if (elemento.type === Fragment) {
        recorrer(elemento.props.children)
      } else if (elemento.type === 'option') {
        const texto = textoDe(elemento.props.children)
        opciones.push({
          valor: String(elemento.props.value ?? texto),
          etiqueta: elemento.props.children,
          texto,
          deshabilitada: !!elemento.props.disabled,
        })
      }
    })
  recorrer(children)
  return opciones
}

/** Reemplaza al select nativo, que no respeta el tema. La lista es un popover nativo y no la recortan los contenedores con scroll */
export function Selector({ value, onChange, children, required, disabled, id, className = '', 'aria-label': ariaLabel }: SelectorProps) {
  const opciones = extraerOpciones(children)
  const valor = value === undefined || value === null ? '' : String(value)
  const indiceSeleccionado = opciones.findIndex((o) => o.valor === valor)
  const seleccionada = opciones[indiceSeleccionado]
  const esMarcador = valor === '' && (seleccionada?.texto ?? '').endsWith('…')

  const idLista = useId()
  const disparador = useRef<HTMLButtonElement>(null)
  const lista = useRef<HTMLUListElement>(null)
  const [abierta, setAbierta] = useState(false)
  const [activa, setActiva] = useState(-1)

  // El manejador del evento toggle se registra una sola vez y lee la selección vigente por referencia
  const indiceSeleccionadoRef = useRef(indiceSeleccionado)
  useEffect(() => {
    indiceSeleccionadoRef.current = indiceSeleccionado
  }, [indiceSeleccionado])

  /** Se posiciona de forma imperativa antes de mostrarse, para que no parpadee */
  const ubicar = useCallback(() => {
    const el = lista.current
    const r = disparador.current?.getBoundingClientRect()
    if (!el || !r) return
    const espacioAbajo = window.innerHeight - r.bottom - 8
    const espacioArriba = r.top - 8
    const haciaArriba = espacioAbajo < Math.min(el.scrollHeight, 180) && espacioArriba > espacioAbajo
    const alto = Math.max(120, Math.min(ALTO_MAXIMO, haciaArriba ? espacioArriba : espacioAbajo))
    Object.assign(el.style, {
      left: `${Math.max(8, Math.min(r.left, window.innerWidth - Math.max(r.width, el.offsetWidth) - 8))}px`,
      minWidth: `${r.width}px`,
      maxHeight: `${alto}px`,
      top: haciaArriba ? 'auto' : `${r.bottom + 4}px`,
      bottom: haciaArriba ? `${window.innerHeight - r.top + 4}px` : 'auto',
      right: 'auto',
    })
  }, [])

  useEffect(() => {
    const el = lista.current
    if (!el) return
    const antes = (e: Event) => {
      if ((e as Event & { newState?: string }).newState === 'open') ubicar()
    }
    const despues = (e: Event) => {
      const abierto = (e as Event & { newState?: string }).newState === 'open'
      setAbierta(abierto)
      if (abierto) {
        setActiva(Math.max(0, indiceSeleccionadoRef.current))
        el.focus()
      } else if (el.contains(document.activeElement) || document.activeElement === document.body) {
        disparador.current?.focus()
      }
    }
    el.addEventListener('beforetoggle', antes)
    el.addEventListener('toggle', despues)
    return () => {
      el.removeEventListener('beforetoggle', antes)
      el.removeEventListener('toggle', despues)
    }
  }, [ubicar])

  // Mientras está abierta, sigue al control si la página o el diálogo se desplazan
  useEffect(() => {
    if (!abierta) return
    window.addEventListener('resize', ubicar)
    window.addEventListener('scroll', ubicar, true)
    return () => {
      window.removeEventListener('resize', ubicar)
      window.removeEventListener('scroll', ubicar, true)
    }
  }, [abierta, ubicar])

  useEffect(() => {
    if (abierta) lista.current?.querySelector('[data-activa="true"]')?.scrollIntoView({ block: 'nearest' })
  }, [abierta, activa])

  function elegir(indice: number) {
    const opcion = opciones[indice]
    if (!opcion || opcion.deshabilitada) return
    if (opcion.valor !== valor) onChange?.({ target: { value: opcion.valor } })
    lista.current?.hidePopover()
  }

  function mover(desde: number, paso: 1 | -1): number {
    for (let i = desde + paso; i >= 0 && i < opciones.length; i += paso) {
      if (!opciones[i].deshabilitada) return i
    }
    return desde
  }

  function alTeclearEnLista(e: KeyboardEvent<HTMLUListElement>) {
    if (e.key === 'ArrowDown') setActiva((a) => mover(a, 1))
    else if (e.key === 'ArrowUp') setActiva((a) => mover(a, -1))
    else if (e.key === 'Home') setActiva(mover(-1, 1))
    else if (e.key === 'End') setActiva(mover(opciones.length, -1))
    else if (e.key === 'Enter' || e.key === ' ') elegir(activa)
    else if (e.key === 'Tab') lista.current?.hidePopover()
    else if (e.key.length === 1) {
      // Salta a la siguiente opción que empieza con la letra tecleada
      const letra = e.key.toLowerCase()
      const orden = [...opciones.keys()].map((k) => (activa + 1 + k) % opciones.length)
      const encontrada = orden.find((i) => !opciones[i].deshabilitada && opciones[i].texto.toLowerCase().startsWith(letra))
      if (encontrada !== undefined) setActiva(encontrada)
      return
    } else return
    e.preventDefault()
  }

  function alTeclearEnDisparador(e: KeyboardEvent<HTMLButtonElement>) {
    if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && !abierta) {
      e.preventDefault()
      lista.current?.showPopover()
    }
  }

  return (
    <div className={`relative w-full ${className}`}>
      <button
        ref={disparador}
        id={id}
        type="button"
        disabled={disabled}
        popoverTarget={idLista}
        aria-haspopup="listbox"
        aria-expanded={abierta}
        aria-controls={idLista}
        aria-label={ariaLabel}
        onKeyDown={alTeclearEnDisparador}
        className={`flex w-full items-center justify-between gap-2 rounded-lg border bg-panel px-3 py-2 text-left text-sm transition-colors hover:border-ink-faint focus:border-accent focus:outline-none disabled:cursor-not-allowed disabled:bg-panel-muted disabled:text-ink-soft ${
          abierta ? 'border-accent' : 'border-line'
        } ${esMarcador ? 'text-ink-faint' : 'text-ink'}`}
      >
        <span className="truncate">{seleccionada?.etiqueta ?? ''}</span>
        <ChevronDown
          className={`h-4 w-4 shrink-0 text-ink-faint transition-transform ${abierta ? 'rotate-180' : ''}`}
          aria-hidden="true"
        />
      </button>

      {/* Conserva la validación nativa de campo obligatorio del formulario */}
      {required && (
        <input
          tabIndex={-1}
          aria-hidden="true"
          required
          value={valor}
          onChange={() => {}}
          className="pointer-events-none absolute inset-x-0 bottom-0 h-px w-full opacity-0"
        />
      )}

      <ul
        ref={lista}
        id={idLista}
        popover="auto"
        role="listbox"
        tabIndex={-1}
        aria-label={ariaLabel}
        aria-activedescendant={activa >= 0 ? `${idLista}-${activa}` : undefined}
        onKeyDown={alTeclearEnLista}
        // Dentro de un <label> (Campo), un clic en la lista activaría el control y la volvería a abrir
        onClick={(e) => e.preventDefault()}
        className="m-0 overflow-y-auto rounded-lg border border-line bg-panel p-1 text-sm text-ink shadow-lg outline-none"
      >
        {opciones.map((o, i) => {
          const elegida = i === indiceSeleccionado
          return (
            <li
              key={`${o.valor}-${i}`}
              id={`${idLista}-${i}`}
              role="option"
              aria-selected={elegida}
              aria-disabled={o.deshabilitada || undefined}
              data-activa={i === activa}
              onMouseEnter={() => !o.deshabilitada && setActiva(i)}
              onClick={() => elegir(i)}
              className={`flex cursor-pointer items-center justify-between gap-3 whitespace-nowrap rounded-md px-2.5 py-1.5 ${
                i === activa ? 'bg-panel-muted' : ''
              } ${elegida ? 'font-medium text-accent' : ''} ${o.deshabilitada ? 'cursor-not-allowed opacity-50' : ''}`}
            >
              <span>{o.etiqueta}</span>
              {elegida && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
            </li>
          )
        })}
      </ul>
    </div>
  )
}
