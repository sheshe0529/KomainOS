import { useRef, useState } from 'react'
import { Eye, EyeOff, Upload } from 'lucide-react'
import type { FamiliaSistemaOperativo, MetodoElevacion, TipoAutenticacion } from '@/api/dominio'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { METODOS_ELEVACION, metodosAdmitidos } from '@/utils/elevacion'
import { ETIQUETA_AUTENTICACION, ETIQUETA_METODO_ELEVACION } from '@/utils/etiquetas'

const TIPOS: TipoAutenticacion[] = ['PASSWORD', 'LLAVE_SSH']

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

interface ElevacionProps {
  valor: MetodoElevacion
  onCambiar: (metodo: MetodoElevacion) => void
  /** Credencial de un servidor: solo los métodos de su familia. Sin familia (cuenta de servicio), todos */
  familia?: FamiliaSistemaOperativo
  tipo: TipoAutenticacion
}

/** Cómo obtiene privilegios Ansible después de conectarse: se aplica tal cual en cada ejecución (DEC-57) */
export function SelectorElevacion({ valor, onCambiar, familia, tipo }: ElevacionProps) {
  const admitidos = metodosAdmitidos(familia, tipo)
  return (
    <Opciones
      leyenda="Elevación de privilegios"
      nombre="metodo-elevacion"
      opciones={METODOS_ELEVACION.filter((m) => familia === undefined || admitidos.includes(m))}
      valor={valor}
      etiquetas={ETIQUETA_METODO_ELEVACION}
      detalle={{
        NINGUNO: 'Ejecuta con los permisos del usuario de acceso, sea o no administrador.',
        SUDO: 'Linux: ejecuta como root con sudo, con o sin contraseña.',
        SU: 'Linux: cambia a root con su y la contraseña de root.',
        RUNAS: 'Windows: la misma cuenta, con el token completo de administrador.',
      }}
      bloqueada={(m) => !admitidos.includes(m)}
      onCambiar={onCambiar}
      ayuda={
        valor === 'SUDO'
          ? 'Ansible ejecuta «sudo /bin/sh -c …»: sudoers debe permitirle una shell como root (por ejemplo, ALL), no solo ciertos comandos.'
          : tipo === 'LLAVE_SSH' && familia === undefined
            ? 'runas es para Windows, que solo admite contraseña.'
            : undefined
      }
    />
  )
}

interface CamposElevacionProps {
  metodo: MetodoElevacion
  conContrasena: boolean
  onConContrasena: (valor: boolean) => void
  valor: string
  onCambiar: (valor: string) => void
  error?: string
  /** Al rotar: vacía conserva la contraseña de elevación vigente */
  conserva?: boolean
}

/** sudo puede pedir la contraseña del propio usuario o no pedir ninguna (NOPASSWD); su siempre pide la de root */
export function CamposElevacion({ metodo, conContrasena, onConContrasena, valor, onCambiar, error, conserva = false }: CamposElevacionProps) {
  if (metodo === 'SUDO') {
    return (
      <div className="flex flex-col gap-3">
        <label className="flex items-center gap-2 text-sm text-ink">
          <input
            type="checkbox"
            checked={conContrasena}
            onChange={(e) => onConContrasena(e.target.checked)}
            className="h-4 w-4 accent-[var(--color-accent)]"
          />
          sudo pide contraseña
          <span className="text-xs text-ink-faint">(sin marcar: autorizado sin contraseña, NOPASSWD)</span>
        </label>
        {conContrasena && (
          <CampoContrasena
            etiqueta="Contraseña de sudo"
            valor={valor}
            onCambiar={onCambiar}
            obligatorio={!conserva}
            error={error}
            ayuda={conserva ? 'Déjela vacía para conservar la actual.' : 'La que pide sudo: normalmente la del propio usuario de acceso.'}
          />
        )}
      </div>
    )
  }
  if (metodo === 'SU') {
    return (
      <CampoContrasena
        etiqueta="Contraseña de root (su)"
        valor={valor}
        onCambiar={onCambiar}
        obligatorio={!conserva}
        error={error}
        ayuda={conserva ? 'Déjela vacía para conservar la actual.' : 'La que pide su para cambiar a root.'}
      />
    )
  }
  return null
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
          ayuda={ayuda ?? 'Formato PEM u OpenSSH, sin frase de contraseña, desde -----BEGIN … PRIVATE KEY----- hasta la línea END.'}
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
