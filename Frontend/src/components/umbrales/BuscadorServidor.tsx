import { useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { LoaderCircle, Search } from 'lucide-react'
import { servidoresApi } from '@/api/inventario'
import type { EstadoServidor } from '@/api/dominio'
import type { ServidorResumenRespuesta } from '@/api/types'

const MINIMO_CARACTERES = 2
const RESULTADOS_MAXIMOS = 8
const ESPERA_MS = 250

const NINGUNO: ReadonlySet<number> = new Set()

interface BuscadorServidorProps {
  /** Servidores ya elegidos (por ejemplo, con excepción): se muestran, pero no se pueden volver a elegir */
  yaDefinidos?: ReadonlySet<number>
  /** Solo los servidores en ese estado; sin él, todos menos los dados de baja */
  estado?: EstadoServidor
  onElegir: (servidor: { id: number; nombre: string }) => void
}

/** Caja de búsqueda con lista desplegable: el inventario no se carga completo, solo lo que coincide con lo escrito */
export function BuscadorServidor({ yaDefinidos = NINGUNO, estado, onElegir }: BuscadorServidorProps) {
  const idLista = useId()
  const contenedor = useRef<HTMLDivElement>(null)
  const [texto, setTexto] = useState('')
  const [abierta, setAbierta] = useState(false)
  // Cada resultado recuerda la búsqueda a la que responde: si el texto cambió, todavía no vale
  const [encontrados, setEncontrados] = useState<{ consulta: string; items: ServidorResumenRespuesta[] }>({ consulta: '', items: [] })
  const [fallo, setFallo] = useState(false)
  const [activo, setActivo] = useState(0)

  const consulta = texto.trim()
  const suficiente = consulta.length >= MINIMO_CARACTERES

  useEffect(() => {
    if (!suficiente) return
    let vigente = true
    const temporizador = window.setTimeout(() => {
      servidoresApi
        .listar({ texto: consulta, estado, tamano: RESULTADOS_MAXIMOS, orden: 'hostname,asc' })
        .then((pagina) => {
          if (!vigente) return
          // Un servidor dado de baja no admite excepciones ni mantenimientos
          setEncontrados({ consulta, items: (pagina.contenido ?? []).filter((s) => s.estado !== 'DADO_DE_BAJA') })
          setActivo(0)
        })
        .catch(() => vigente && setFallo(true))
    }, ESPERA_MS)
    return () => {
      vigente = false
      window.clearTimeout(temporizador)
    }
  }, [consulta, suficiente, estado])

  const vigentes = suficiente && encontrados.consulta === consulta
  const visibles = vigentes ? encontrados.items : []
  const buscando = suficiente && !vigentes && !fallo

  function elegir(servidor: ServidorResumenRespuesta | undefined) {
    if (!servidor?.id || yaDefinidos.has(servidor.id)) return
    onElegir({ id: servidor.id, nombre: servidor.hostname ?? '' })
    setTexto('')
    setAbierta(false)
  }

  function alTeclear(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setAbierta(true)
      setActivo((a) => Math.min(a + 1, visibles.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActivo((a) => Math.max(a - 1, 0))
    } else if (e.key === 'Enter' && abierta && visibles.length > 0) {
      e.preventDefault()
      elegir(visibles[activo])
    } else if (e.key === 'Escape') {
      setAbierta(false)
    }
  }

  return (
    <div
      ref={contenedor}
      className="relative w-full"
      onBlur={(e) => {
        if (!contenedor.current?.contains(e.relatedTarget)) setAbierta(false)
      }}
    >
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" aria-hidden="true" />
      <input
        type="text"
        role="combobox"
        aria-expanded={abierta}
        aria-controls={idLista}
        aria-autocomplete="list"
        aria-activedescendant={abierta && visibles.length > 0 ? `${idLista}-${activo}` : undefined}
        aria-label="Buscar servidor"
        placeholder="Buscar servidor…"
        value={texto}
        onChange={(e) => {
          setTexto(e.target.value)
          setFallo(false)
          setAbierta(true)
        }}
        onFocus={() => setAbierta(true)}
        onKeyDown={alTeclear}
        className="w-full rounded-lg border border-line bg-panel py-2 pl-9 pr-9 text-sm text-ink placeholder:text-ink-faint focus:border-accent focus:outline-none"
      />
      {buscando && (
        <LoaderCircle className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-ink-faint" aria-hidden="true" />
      )}

      {abierta && texto !== '' && (
        <ul
          id={idLista}
          role="listbox"
          aria-label="Servidores"
          className="absolute z-20 mt-1 max-h-72 w-full overflow-y-auto rounded-lg border border-line bg-panel p-1 text-sm shadow-lg"
        >
          {!suficiente ? (
            <li className="px-2.5 py-2 text-ink-faint">Escriba {MINIMO_CARACTERES} letras o más</li>
          ) : fallo ? (
            <li className="px-2.5 py-2 text-danger">No se pudo buscar</li>
          ) : visibles.length === 0 ? (
            <li className="px-2.5 py-2 text-ink-faint">{buscando ? 'Buscando…' : 'Sin resultados'}</li>
          ) : (
            visibles.map((s, i) => {
              const definido = yaDefinidos.has(s.id ?? -1)
              return (
                <li
                  key={s.id}
                  id={`${idLista}-${i}`}
                  role="option"
                  aria-selected={i === activo}
                  aria-disabled={definido || undefined}
                  // mousedown evita que el campo pierda el foco antes de registrar la elección
                  onMouseDown={(e) => {
                    e.preventDefault()
                    elegir(s)
                  }}
                  onMouseEnter={() => setActivo(i)}
                  className={`flex items-center justify-between gap-3 rounded-md px-2.5 py-1.5 ${
                    definido ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'
                  } ${i === activo ? 'bg-panel-muted' : ''}`}
                >
                  <span className="font-mono font-medium text-ink">{s.hostname}</span>
                  <span className="truncate text-xs text-ink-faint">
                    {definido ? 'Ya definido' : [s.entorno?.nombre, s.criticidad?.nombre].filter(Boolean).join(' · ')}
                  </span>
                </li>
              )
            })
          )}
        </ul>
      )}
    </div>
  )
}
