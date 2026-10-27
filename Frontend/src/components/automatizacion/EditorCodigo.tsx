import { useEffect, useRef } from 'react'
import { basicSetup } from 'codemirror'
import { Compartment, EditorState, RangeSetBuilder } from '@codemirror/state'
import type { Extension } from '@codemirror/state'
import { Decoration, EditorView, keymap, placeholder } from '@codemirror/view'
import { HighlightStyle, StreamLanguage, syntaxHighlighting } from '@codemirror/language'
import { indentWithTab } from '@codemirror/commands'
import { shell } from '@codemirror/legacy-modes/mode/shell'
import { powerShell } from '@codemirror/legacy-modes/mode/powershell'
import { tags } from '@lezer/highlight'
import type { Lenguaje } from '@/utils/parametrosScript'

interface EditorCodigoProps {
  valor: string
  onChange?: (valor: string) => void
  lenguaje: Lenguaje
  soloLectura?: boolean
  /** Líneas (desde 1) que se destacan, como el bloque de parámetros que antepone KomainOS */
  resaltar?: { desde: number; hasta: number }
  textoVacio?: string
  altoMinimo?: string
  altoMaximo?: string
  'aria-label'?: string
}

const LENGUAJES: Record<Lenguaje, Extension> = {
  bash: StreamLanguage.define(shell),
  powershell: StreamLanguage.define(powerShell),
}

/** Los colores son variables del tema: el editor cambia con el modo claro u oscuro sin reconstruirse */
const resaltadoSintaxis = HighlightStyle.define([
  { tag: [tags.keyword, tags.controlKeyword, tags.operatorKeyword], color: 'var(--color-codigo-clave)' },
  { tag: [tags.string, tags.special(tags.string)], color: 'var(--color-codigo-texto)' },
  {
    tag: [tags.variableName, tags.special(tags.variableName), tags.definition(tags.variableName)],
    color: 'var(--color-codigo-variable)',
  },
  { tag: [tags.number, tags.bool, tags.null, tags.atom], color: 'var(--color-codigo-numero)' },
  {
    tag: [tags.function(tags.variableName), tags.standard(tags.variableName), tags.standard(tags.name)],
    color: 'var(--color-codigo-comando)',
  },
  {
    tag: [tags.comment, tags.lineComment, tags.blockComment, tags.meta],
    color: 'var(--color-ink-faint)',
    fontStyle: 'italic',
  },
  { tag: tags.operator, color: 'var(--color-ink-soft)' },
])

const tema = EditorView.theme({
  '&': {
    backgroundColor: 'var(--color-panel)',
    color: 'var(--color-ink)',
    fontSize: '0.8125rem',
    borderRadius: '0.5rem',
  },
  '&.cm-focused': { outline: 'none' },
  '.cm-scroller': { fontFamily: 'var(--font-mono)', lineHeight: '1.6' },
  '.cm-content': { caretColor: 'var(--color-ink)' },
  '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--color-ink)' },
  '.cm-gutters': {
    backgroundColor: 'var(--color-panel-muted)',
    color: 'var(--color-ink-faint)',
    borderRight: '1px solid var(--color-line)',
    borderTopLeftRadius: '0.5rem',
    borderBottomLeftRadius: '0.5rem',
  },
  '.cm-activeLine': { backgroundColor: 'color-mix(in srgb, var(--color-panel-muted) 60%, transparent)' },
  '.cm-activeLineGutter': { backgroundColor: 'var(--color-line)', color: 'var(--color-ink)' },
  '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground': {
    backgroundColor: 'var(--color-accent-soft)',
  },
  '.cm-matchingBracket': { backgroundColor: 'var(--color-accent-soft)', outline: '1px solid var(--color-line)' },
  '.cm-searchMatch': { backgroundColor: 'var(--color-warning-soft)', outline: '1px solid var(--color-warning)' },
  '.cm-placeholder': { color: 'var(--color-ink-faint)' },
  '.cm-foldPlaceholder': { backgroundColor: 'var(--color-panel-muted)', border: 'none', color: 'var(--color-ink-soft)' },
  '.cm-tooltip': { backgroundColor: 'var(--color-panel)', border: '1px solid var(--color-line)' },
  '.cm-panels': { backgroundColor: 'var(--color-panel-muted)', color: 'var(--color-ink)' },
  '.cm-panels.cm-panels-bottom': { borderTop: '1px solid var(--color-line)' },
  '.cm-textfield': {
    backgroundColor: 'var(--color-panel)',
    border: '1px solid var(--color-line)',
    borderRadius: '0.375rem',
    color: 'var(--color-ink)',
  },
  '.cm-button': {
    backgroundImage: 'none',
    backgroundColor: 'var(--color-panel)',
    border: '1px solid var(--color-line)',
    borderRadius: '0.375rem',
    color: 'var(--color-ink)',
  },
  '.cm-bloque-komainos': {
    backgroundColor: 'var(--color-accent-soft)',
    boxShadow: 'inset 3px 0 0 var(--color-accent)',
  },
})

function lineasResaltadas(rango?: { desde: number; hasta: number }): Extension {
  if (!rango) return []
  return EditorView.decorations.compute(['doc'], (estado) => {
    const constructor = new RangeSetBuilder<Decoration>()
    const linea = Decoration.line({ class: 'cm-bloque-komainos' })
    for (let n = Math.max(1, rango.desde); n <= Math.min(rango.hasta, estado.doc.lines); n++) {
      const inicio = estado.doc.line(n).from
      constructor.add(inicio, inicio, linea)
    }
    return constructor.finish()
  })
}

/**
 * Editor de scripts sobre CodeMirror: números de línea, resaltado de Bash o PowerShell, búsqueda (Ctrl+F),
 * plegado y Tab para sangrar. Con soloLectura sirve también como visor
 */
export function EditorCodigo({
  valor,
  onChange,
  lenguaje,
  soloLectura = false,
  resaltar,
  textoVacio,
  altoMinimo = '16rem',
  altoMaximo = '36rem',
  'aria-label': ariaLabel,
}: EditorCodigoProps) {
  const contenedor = useRef<HTMLDivElement>(null)
  const vista = useRef<EditorView | null>(null)
  const alCambiar = useRef(onChange)
  const configurable = useRef({ lenguaje: new Compartment(), lectura: new Compartment(), resaltado: new Compartment() })

  useEffect(() => {
    alCambiar.current = onChange
  }, [onChange])

  // La vista se crea una vez; los cambios de props se aplican por compartimentos para no perder cursor ni historial
  useEffect(() => {
    if (!contenedor.current) return
    const c = configurable.current
    const editor = new EditorView({
      parent: contenedor.current,
      state: EditorState.create({
        doc: valor,
        extensions: [
          basicSetup,
          keymap.of([indentWithTab]),
          tema,
          syntaxHighlighting(resaltadoSintaxis),
          EditorView.theme({
            '&': { minHeight: altoMinimo, maxHeight: altoMaximo },
            '.cm-scroller': { overflow: 'auto' },
          }),
          c.lenguaje.of(LENGUAJES[lenguaje]),
          c.lectura.of([EditorState.readOnly.of(soloLectura), EditorView.editable.of(!soloLectura)]),
          c.resaltado.of(lineasResaltadas(resaltar)),
          textoVacio ? placeholder(textoVacio) : [],
          EditorView.contentAttributes.of({ 'aria-label': ariaLabel ?? 'Código del script' }),
          EditorView.updateListener.of((u) => {
            if (u.docChanged) alCambiar.current?.(u.state.doc.toString())
          }),
        ],
      }),
    })
    vista.current = editor
    return () => {
      editor.destroy()
      vista.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- se crea una sola vez; los demás cambios llegan por los efectos de abajo
  }, [])

  useEffect(() => {
    const editor = vista.current
    if (!editor || editor.state.doc.toString() === valor) return
    editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: valor } })
  }, [valor])

  useEffect(() => {
    vista.current?.dispatch({ effects: configurable.current.lenguaje.reconfigure(LENGUAJES[lenguaje]) })
  }, [lenguaje])

  useEffect(() => {
    vista.current?.dispatch({
      effects: configurable.current.lectura.reconfigure([
        EditorState.readOnly.of(soloLectura),
        EditorView.editable.of(!soloLectura),
      ]),
    })
  }, [soloLectura])

  const desde = resaltar?.desde
  const hasta = resaltar?.hasta
  useEffect(() => {
    const rango = desde && hasta ? { desde, hasta } : undefined
    vista.current?.dispatch({ effects: configurable.current.resaltado.reconfigure(lineasResaltadas(rango)) })
  }, [desde, hasta])

  return <div ref={contenedor} className="overflow-hidden rounded-lg border border-line focus-within:border-accent" />
}
