import { useRef, useState } from 'react'
import { Eye, EyeOff, Upload } from 'lucide-react'
import type { FamiliaSistemaOperativo, TipoAutenticacion, TipoUsuario } from '@/api/dominio'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { ETIQUETA_AUTENTICACION, ETIQUETA_TIPO_USUARIO } from '@/utils/etiquetas'

const TIPOS: TipoAutenticacion[] = ['PASSWORD', 'LLAVE_SSH']
const TIPOS_USUARIO: TipoUsuario[] = ['ADMINISTRADOR', 'GENERICO']

interface OpcionesProps<T extends string> {
  leyenda: string
  nombre: string
  opciones: T[]
  valor: T
  etiquetas: Record<T, string>
  detalle?: Partial<Record<T, string>>
  bloqueada?: (opcion: T) => boolean
  onCambiar: (valor: T) => void
  ayuda?: string
}

function Opciones<T extends string>({ leyenda, nombre, opciones, valor, etiquetas, detalle, bloqueada, onCambiar, ayuda }: OpcionesProps<T>) {
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 text-sm font-medium text-ink">{leyenda}</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {opciones.map((o) => {
          const deshabilitada = bloqueada?.(o) ?? false
          return (
            <label
              key={o}
              className={`flex items-start gap-2 rounded-lg border border-line p-3 text-sm has-[:checked]:border-accent has-[:checked]:bg-accent-soft ${
                deshabilitada ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'
              }`}
            >
              <input
                type="radio"
                name={nombre}
                checked={valor === o}
                disabled={deshabilitada}
                onChange={() => onCambiar(o)}
                className="mt-0.5 accent-[var(--color-accent)]"
              />
              <span>
                <span className="font-medium text-ink">{etiquetas[o]}</span>
                {detalle?.[o] && <span className="block text-xs text-ink-soft">{detalle[o]}</span>}
              </span>
            </label>
          )
        })}
      </div>
      {ayuda && <span className="text-xs text-ink-faint">{ayuda}</span>}
    </fieldset>
  )
}

interface MecanismoProps {
  valor: TipoAutenticacion
  onCambiar: (tipo: TipoAutenticacion) => void
  /** WinRM solo admite contraseña (HU04 CA3) */
  familia?: FamiliaSistemaOperativo
}

export function SelectorMecanismo({ valor, onCambiar, familia }: MecanismoProps) {
  return (
    <Opciones
      leyenda="Mecanismo de acceso"
      nombre="tipo-autenticacion"
      opciones={TIPOS}
      valor={valor}
      etiquetas={ETIQUETA_AUTENTICACION}
      bloqueada={(t) => t === 'LLAVE_SSH' && familia === 'WINDOWS'}
      onCambiar={onCambiar}
      ayuda={familia === 'WINDOWS' ? 'Los servidores Windows se conectan por WinRM: solo admiten usuario y contraseña.' : undefined}
    />
  )
}

export function SelectorTipoUsuario({ valor, onCambiar }: { valor: TipoUsuario; onCambiar: (tipo: TipoUsuario) => void }) {
  return (
    <Opciones
      leyenda="Tipo de usuario"
      nombre="tipo-usuario"
      opciones={TIPOS_USUARIO}
      valor={valor}
      etiquetas={ETIQUETA_TIPO_USUARIO}
      detalle={{ ADMINISTRADOR: 'Tiene privilegios de administrador.', GENERICO: 'Para subir a root necesita la contraseña su.' }}
      onCambiar={onCambiar}
    />
  )
}

interface CampoContrasenaProps {
  etiqueta: string
  valor: string
  onCambiar: (valor: string) => void
  error?: string
  ayuda?: string
  obligatorio?: boolean
}

/** El secreto no se recorta: un espacio puede ser parte de la contraseña */
export function CampoContrasena({ etiqueta, valor, onCambiar, error, ayuda, obligatorio = true }: CampoContrasenaProps) {
  const [visible, setVisible] = useState(false)
  return (
    <Campo etiqueta={etiqueta} obligatorio={obligatorio} error={error} ayuda={ayuda}>
      <span className="relative flex">
        <Entrada
          type={visible ? 'text' : 'password'}
          value={valor}
          onChange={(e) => onCambiar(e.target.value)}
          autoComplete="new-password"
          spellCheck={false}
          className="pr-10 font-mono"
          required={obligatorio}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Ocultar' : 'Mostrar'}
          title={visible ? 'Ocultar' : 'Mostrar'}
          className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-ink-faint hover:text-ink"
        >
          {visible ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
        </button>
      </span>
    </Campo>
  )
}

interface CampoSecretoProps {
  tipo: TipoAutenticacion
  valor: string
  onCambiar: (valor: string) => void
  error?: string
  ayuda?: string
  obligatorio?: boolean
}

export function CampoSecreto({ tipo, valor, onCambiar, error, ayuda, obligatorio = true }: CampoSecretoProps) {
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
          obligatorio={obligatorio}
          error={error}
          ayuda={ayuda ?? 'Formato PEM u OpenSSH, desde -----BEGIN … PRIVATE KEY----- hasta la línea END.'}
        >
          <AreaTexto
            rows={7}
            value={valor}
            onChange={(e) => onCambiar(e.target.value)}
            spellCheck={false}
            autoComplete="off"
            className="font-mono text-xs"
            placeholder="-----BEGIN OPENSSH PRIVATE KEY-----"
            required={obligatorio}
          />
        </Campo>
        <input ref={archivo} type="file" className="hidden" onChange={(e) => cargar(e.target.files)} />
        <Boton icono={Upload} variante="fantasma" className="self-start" onClick={() => archivo.current?.click()}>
          Cargar desde archivo
        </Boton>
      </div>
    )
  }

  return <CampoContrasena etiqueta="Contraseña" valor={valor} onCambiar={onCambiar} error={error} ayuda={ayuda} obligatorio={obligatorio} />
}
