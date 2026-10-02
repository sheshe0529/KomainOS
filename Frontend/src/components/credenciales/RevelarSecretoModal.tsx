import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Check, Copy, Eye, EyeOff, Lock } from 'lucide-react'
import type { SecretoReveladoRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { ETIQUETA_AUTENTICACION } from '@/utils/etiquetas'
import { errorDeCampo, textoDeError } from '@/utils/errores'

interface RevelarSecretoModalProps {
  abierto: boolean
  credencial: { nombre?: string; usuarioAcceso?: string }
  onCerrar: () => void
  onRevelar: (contrasena: string) => Promise<SecretoReveladoRespuesta>
}

/** RF08: reautenticación, visualización temporal y copia al portapapeles, el secreto solo vive en el estado del modal */
export function RevelarSecretoModal({ abierto, credencial, onCerrar, onRevelar }: RevelarSecretoModalProps) {
  const [contrasena, setContrasena] = useState('')
  const [revelado, setRevelado] = useState<SecretoReveladoRespuesta | null>(null)
  const [venceEn, setVenceEn] = useState(0)
  const [restantes, setRestantes] = useState(0)
  const [vencido, setVencido] = useState(false)
  const [copiado, setCopiado] = useState(false)
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

  async function copiar() {
    if (!revelado?.secreto) return
    try {
      await navigator.clipboard.writeText(revelado.secreto)
      setCopiado(true)
      window.setTimeout(() => setCopiado(false), 2000)
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
          <>
            <Boton icono={EyeOff} onClick={onCerrar}>
              Ocultar y cerrar
            </Boton>
            <Boton icono={copiado ? Check : Copy} variante="primario" onClick={copiar}>
              {copiado ? 'Copiado' : 'Copiar'}
            </Boton>
          </>
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
        <div className="flex flex-col gap-3">
          <MensajeError error={error} />
          <p className="text-sm text-ink-soft">
            <span className="font-mono text-ink">{revelado.usuarioAcceso}</span> ·{' '}
            {revelado.tipoAutenticacion ? ETIQUETA_AUTENTICACION[revelado.tipoAutenticacion] : ''} · versión {revelado.numeroVersion}
          </p>
          <pre className="max-h-[40dvh] overflow-auto whitespace-pre-wrap break-all rounded-lg border border-line bg-panel-muted px-3 py-2 font-mono text-sm text-ink">
            {revelado.secreto}
          </pre>
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
          <Campo etiqueta="Su contraseña" obligatorio error={errorDeCampo(error, 'contrasena')}>
            <Entrada
              type="password"
              value={contrasena}
              onChange={(e) => setContrasena(e.target.value)}
              autoComplete="current-password"
              required
              autoFocus
            />
          </Campo>
        </form>
      )}
    </Modal>
  )
}
