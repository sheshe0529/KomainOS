import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Check, Copy, Eye, EyeOff, Lock } from 'lucide-react'
import type { SecretoReveladoRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { ETIQUETA_AUTENTICACION, ETIQUETA_TIPO_USUARIO } from '@/utils/etiquetas'
import { errorDeCampo, textoDeError } from '@/utils/errores'
import { CampoReautenticacion } from './CampoReautenticacion'

interface RevelarSecretoModalProps {
  abierto: boolean
  credencial: { nombre?: string; usuarioAcceso?: string }
  onCerrar: () => void
  onRevelar: (contrasena: string) => Promise<SecretoReveladoRespuesta>
}

type Parte = 'secreto' | 'su'

/** RF08: reautenticación, visualización temporal y copia al portapapeles, los secretos solo viven en el estado del modal */
export function RevelarSecretoModal({ abierto, credencial, onCerrar, onRevelar }: RevelarSecretoModalProps) {
  const [contrasena, setContrasena] = useState('')
  const [revelado, setRevelado] = useState<SecretoReveladoRespuesta | null>(null)
  const [venceEn, setVenceEn] = useState(0)
  const [restantes, setRestantes] = useState(0)
  const [vencido, setVencido] = useState(false)
  const [copiado, setCopiado] = useState<Parte | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<unknown>()

  useEffect(() => {
    if (!revelado) return
    const temporizador = window.setInterval(() => {
      const quedan = Math.ceil((venceEn - Date.now()) / 1000)
      if (quedan <= 0) {
        setRevelado(null)
        setVencido(true)
      } else {
        setRestantes(quedan)
      }
    }, 250)
    return () => window.clearInterval(temporizador)
  }, [revelado, venceEn])

  async function revelar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setError(undefined)
    try {
      const respuesta = await onRevelar(contrasena)
      const segundos = respuesta.segundosVisible ?? 30
      setContrasena('')
      setVencido(false)
      setRestantes(segundos)
      setVenceEn(Date.now() + segundos * 1000)
      setRevelado(respuesta)
    } catch (e) {
      setError(e)
    } finally {
      setEnviando(false)
    }
  }

  async function copiar(parte: Parte, texto?: string) {
    if (!texto) return
    try {
      await navigator.clipboard.writeText(texto)
      setCopiado(parte)
      window.setTimeout(() => setCopiado(null), 2000)
    } catch (e) {
      setError(new Error(`No se pudo copiar al portapapeles: ${textoDeError(e)}`))
    }
  }

  const total = revelado?.segundosVisible ?? 30

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      ancho="lg"
      titulo={`Secreto de ${credencial.nombre}`}
      descripcion="Confirme su contraseña para ver el secreto vigente. Se mostrará por unos segundos y la consulta queda registrada en la auditoría."
      pie={
        revelado ? (
          <Boton icono={EyeOff} onClick={onCerrar}>
            Ocultar y cerrar
          </Boton>
        ) : (
          <>
            <Boton onClick={onCerrar}>Cancelar</Boton>
            <Boton type="submit" form="form-revelar" variante="primario" icono={Eye} cargando={enviando}>
              Revelar
            </Boton>
          </>
        )
      }
    >
      {revelado ? (
        <div className="flex flex-col gap-4">
          <MensajeError error={error} />
          <p className="text-sm text-ink-soft">
            <span className="font-mono text-ink">{revelado.usuarioAcceso}</span>
            {revelado.tipoAutenticacion && ` · ${ETIQUETA_AUTENTICACION[revelado.tipoAutenticacion]}`}
            {revelado.tipoUsuario && ` · ${ETIQUETA_TIPO_USUARIO[revelado.tipoUsuario]}`} · versión {revelado.numeroVersion}
          </p>
          <BloqueSecreto
            titulo={revelado.tipoAutenticacion === 'LLAVE_SSH' ? 'Llave privada' : 'Contraseña'}
            texto={revelado.secreto}
            copiado={copiado === 'secreto'}
            onCopiar={() => copiar('secreto', revelado.secreto)}
          />
          {revelado.secretoSu && (
            <BloqueSecreto
              titulo="Contraseña su (root)"
              texto={revelado.secretoSu}
              copiado={copiado === 'su'}
              onCopiar={() => copiar('su', revelado.secretoSu)}
            />
          )}
          <div className="flex items-center gap-3 text-xs text-ink-soft">
            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-panel-muted" aria-hidden="true">
              <div className="h-full rounded-full bg-accent transition-[width] duration-300" style={{ width: `${(restantes / total) * 100}%` }} />
            </div>
            <span className="tabular-nums" aria-live="polite">
              Se oculta en {restantes} s
            </span>
          </div>
        </div>
      ) : (
        <form id="form-revelar" onSubmit={revelar} className="flex flex-col gap-4">
          {vencido && (
            <p className="flex items-center gap-2 rounded-lg bg-panel-muted px-3 py-2 text-sm text-ink-soft">
              <Lock className="h-4 w-4" aria-hidden="true" /> El secreto se ocultó. Confirme su contraseña para verlo otra vez.
            </p>
          )}
          {!errorDeCampo(error, 'contrasena') && <MensajeError error={error} />}
          <CampoReautenticacion valor={contrasena} onCambiar={setContrasena} error={errorDeCampo(error, 'contrasena')} autoFocus />
        </form>
      )}
    </Modal>
  )
}

function BloqueSecreto({ titulo, texto, copiado, onCopiar }: { titulo: string; texto?: string; copiado: boolean; onCopiar: () => void }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium text-ink-soft">{titulo}</span>
        <Boton icono={copiado ? Check : Copy} variante="fantasma" className="px-2 py-1 text-xs" onClick={onCopiar}>
          {copiado ? 'Copiado' : 'Copiar'}
        </Boton>
      </div>
      <pre className="max-h-[35dvh] overflow-auto whitespace-pre-wrap break-all rounded-lg border border-line bg-panel-muted px-3 py-2 font-mono text-sm text-ink">
        {texto}
      </pre>
    </div>
  )
}
