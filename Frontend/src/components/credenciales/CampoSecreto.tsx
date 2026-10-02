import { useRef, useState } from 'react'
import { Eye, EyeOff, Upload } from 'lucide-react'
import type { FamiliaSistemaOperativo, TipoAutenticacion } from '@/api/dominio'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { ETIQUETA_AUTENTICACION } from '@/utils/etiquetas'

const TIPOS: TipoAutenticacion[] = ['PASSWORD', 'LLAVE_SSH']

interface MecanismoProps {
  valor: TipoAutenticacion
  onCambiar: (tipo: TipoAutenticacion) => void
  /** WinRM solo admite contraseña (HU04 CA3) */
  familia?: FamiliaSistemaOperativo
  ayuda?: string
}

export function SelectorMecanismo({ valor, onCambiar, familia, ayuda }: MecanismoProps) {
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 text-sm font-medium text-ink">Mecanismo de autenticación</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {TIPOS.map((t) => {
          const bloqueado = t === 'LLAVE_SSH' && familia === 'WINDOWS'
          return (
            <label
              key={t}
              className={`flex items-center gap-2 rounded-lg border border-line p-3 text-sm has-[:checked]:border-accent has-[:checked]:bg-accent-soft ${
                bloqueado ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'
              }`}
            >
              <input
                type="radio"
                name="tipo-autenticacion"
                checked={valor === t}
                disabled={bloqueado}
                onChange={() => onCambiar(t)}
                className="accent-[var(--color-accent)]"
              />
              {ETIQUETA_AUTENTICACION[t]}
            </label>
          )
        })}
      </div>
      {familia === 'WINDOWS' ? (
        <span className="text-xs text-ink-faint">Los servidores Windows se conectan por WinRM: solo admiten usuario y contraseña.</span>
      ) : (
        ayuda && <span className="text-xs text-ink-faint">{ayuda}</span>
      )}
    </fieldset>
  )
}

interface CampoSecretoProps {
  tipo: TipoAutenticacion
  valor: string
  onCambiar: (valor: string) => void
  error?: string
}

/** El secreto no se recorta: un espacio puede ser parte de la contraseña */
export function CampoSecreto({ tipo, valor, onCambiar, error }: CampoSecretoProps) {
  const [visible, setVisible] = useState(false)
  const archivo = useRef<HTMLInputElement>(null)

  async function cargar(lista: FileList | null) {
    const elegido = lista?.[0]
    if (elegido) onCambiar(await elegido.text())
    if (archivo.current) archivo.current.value = ''
  }

  if (tipo === 'LLAVE_SSH') {
    return (
      <div className="flex flex-col gap-2">
        <Campo
          etiqueta="Llave privada"
          obligatorio
          error={error}
          ayuda="Formato PEM u OpenSSH, desde -----BEGIN … PRIVATE KEY----- hasta la línea END."
        >
          <AreaTexto
            rows={7}
            value={valor}
            onChange={(e) => onCambiar(e.target.value)}
            spellCheck={false}
            autoComplete="off"
            className="font-mono text-xs"
            placeholder="-----BEGIN OPENSSH PRIVATE KEY-----"
            required
          />
        </Campo>
        <input ref={archivo} type="file" className="hidden" onChange={(e) => cargar(e.target.files)} />
        <Boton icono={Upload} variante="fantasma" className="self-start" onClick={() => archivo.current?.click()}>
          Cargar desde archivo
        </Boton>
      </div>
    )
  }

  return (
    <Campo etiqueta="Contraseña" obligatorio error={error}>
      <span className="relative flex">
        <Entrada
          type={visible ? 'text' : 'password'}
          value={valor}
          onChange={(e) => onCambiar(e.target.value)}
          autoComplete="new-password"
          spellCheck={false}
          className="pr-10 font-mono"
          required
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          title={visible ? 'Ocultar' : 'Mostrar'}
          className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-ink-faint hover:text-ink"
        >
          {visible ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
        </button>
      </span>
    </Campo>
  )
}
