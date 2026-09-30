import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { LogIn } from 'lucide-react'
import { useSesion } from '@/auth/sesion-context'
import { Logo } from '@/components/common/Logo'
import { Boton } from '@/components/ui/Boton'
import { Campo, Entrada } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'

/** HU01: inicio de sesión con código y contraseña. */
export function LoginPage() {
  const { usuario, iniciarSesion, expirada } = useSesion()
  const navigate = useNavigate()
  const location = useLocation()
  const [codigo, setCodigo] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<unknown>()

  const destino = (location.state as { desde?: string } | null)?.desde ?? '/'

  if (usuario) {
    return <Navigate to={destino} replace />
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setError(undefined)
    try {
      await iniciarSesion(codigo.trim(), contrasena)
      navigate(destino, { replace: true })
    } catch (e) {
      // HU01 CA2: mensaje genérico; el backend no distingue usuario de contraseña.
      setError(e)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-canvas px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-3 text-center">
          <Logo decorativo className="h-24 w-24" />
          <div>
            <h1 className="text-2xl font-semibold text-ink">KomainOS</h1>
            <span className="mx-auto mt-2 block h-1 w-10 rounded-full bg-detail" aria-hidden="true" />
            <p className="mt-1 text-sm text-ink-soft">Gestión automatizada del mantenimiento de servidores virtuales</p>
          </div>
        </div>

        <form onSubmit={enviar} className="flex flex-col gap-4 rounded-xl border border-line bg-panel p-6 shadow-sm">
          {expirada && !error && (
            <p className="rounded-lg bg-warning-soft px-3 py-2 text-sm text-warning">
              Su sesión expiró. Inicie sesión nuevamente.
            </p>
          )}
          <MensajeError error={error} />
          <Campo etiqueta="Código de usuario" obligatorio>
            <Entrada
              value={codigo}
              onChange={(e) => setCodigo(e.target.value)}
              autoComplete="username"
              autoFocus
              required
            />
          </Campo>
          <Campo etiqueta="Contraseña" obligatorio>
            <Entrada
              type="password"
              value={contrasena}
              onChange={(e) => setContrasena(e.target.value)}
              autoComplete="current-password"
              required
            />
          </Campo>
          <Boton type="submit" variante="primario" icono={LogIn} cargando={enviando} className="mt-2 w-full">
            Iniciar sesión
          </Boton>
        </form>
      </div>
    </div>
  )
}
