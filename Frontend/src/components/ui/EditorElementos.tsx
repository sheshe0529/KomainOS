import { useState } from 'react'
import type { ClipboardEvent, KeyboardEvent } from 'react'
import { X } from 'lucide-react'
import type { TipoDato } from '@/api/dominio'
import { compararElementos, normalizarElemento, SEPARADORES } from '@/utils/umbrales'

interface EditorElementosProps {
  valor: string[]
  onChange: (elementos: string[]) => void
  tipo: TipoDato
  ignoraMayusculas?: boolean
  placeholder?: string
  'aria-label'?: string
}

/** Campo de etiquetas: Enter, coma o punto y coma agregan el elemento; pegar varios los agrega a la vez */
export function EditorElementos({ valor, onChange, tipo, ignoraMayusculas = false, placeholder, 'aria-label': ariaLabel }: EditorElementosProps) {
  const [texto, setTexto] = useState('')
  const [invalidos, setInvalidos] = useState<string[]>([])

  function agregar(crudo: string) {
    const partes = crudo.split(SEPARADORES).map((p) => p.trim()).filter(Boolean)
    if (partes.length === 0) return
    const nuevos = new Set(valor)
    const rechazados: string[] = []
    for (const parte of partes) {
      const elemento = normalizarElemento(parte, tipo, ignoraMayusculas)
      if (elemento) nuevos.add(elemento)
      else rechazados.push(parte)
    }
    onChange([...nuevos].sort(compararElementos(tipo)))
    setInvalidos(rechazados)
    setTexto('')
  }

  function alTeclear(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' || e.key === ',' || e.key === ';') {
      // Enter con el campo vacío no debe enviar el formulario por accidente
      e.preventDefault()
      if (texto.trim()) agregar(texto)
    } else if (e.key === 'Backspace' && texto === '' && valor.length > 0) {
      onChange(valor.slice(0, -1))
    }
  }

  function alPegar(e: ClipboardEvent<HTMLInputElement>) {
    const pegado = e.clipboardData.getData('text')
    if (SEPARADORES.test(pegado.trim())) {
      e.preventDefault()
      agregar(pegado)
    }
  }

  return (
    <div>
      <div className="flex min-h-[2.5rem] flex-wrap items-center gap-1.5 rounded-lg border border-line bg-panel px-2 py-1.5 focus-within:border-accent">
        {valor.map((elemento) => (
          <span key={elemento} className="inline-flex items-center gap-1 rounded-md bg-panel-muted py-0.5 pl-2 pr-1 font-mono text-xs text-ink">
            {elemento}
            <button
              type="button"
              aria-label={`Quitar ${elemento}`}
              onClick={() => onChange(valor.filter((v) => v !== elemento))}
              className="rounded p-0.5 text-ink-faint hover:bg-line hover:text-ink"
            >
              <X className="h-3 w-3" aria-hidden="true" />
            </button>
          </span>
        ))}
        <input
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          onKeyDown={alTeclear}
          onPaste={alPegar}
          onBlur={() => agregar(texto)}
          placeholder={valor.length === 0 ? placeholder : undefined}
          aria-label={ariaLabel}
          className="min-w-[6rem] flex-1 bg-transparent px-1 py-0.5 text-sm text-ink outline-none placeholder:text-ink-faint"
        />
      </div>
      {invalidos.length > 0 && (
        <p className="mt-1 text-xs text-danger" role="alert">
          {tipo === 'STRING' ? 'No válidos' : 'No son números válidos'}: {invalidos.join(', ')}
        </p>
      )}
    </div>
  )
}
