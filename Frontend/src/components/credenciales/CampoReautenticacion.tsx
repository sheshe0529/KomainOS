import { useSesion } from '@/auth/sesion-context'
import { Campo, Entrada } from '@/components/ui/Campo'

interface CampoReautenticacionProps {
  valor: string
  onCambiar: (valor: string) => void
  error?: string
  ayuda?: string
  autoFocus?: boolean
}

/** Va dentro de un form con el usuario oculto: si no, el navegador toma otro campo de la página como usuario y lo autocompleta */
export function CampoReautenticacion({ valor, onCambiar, error, ayuda, autoFocus }: CampoReautenticacionProps) {
  const { usuario } = useSesion()
  return (
    <>
      <input
        type="text"
        name="username"
        autoComplete="username"
        value={usuario?.codigo ?? ''}
        readOnly
        hidden
        tabIndex={-1}
        aria-hidden="true"
      />
      <Campo etiqueta="Su contraseña" obligatorio error={error} ayuda={ayuda}>
        <Entrada
          type="password"
          name="password"
          value={valor}
          onChange={(e) => onCambiar(e.target.value)}
          autoComplete="current-password"
          required
          autoFocus={autoFocus}
        />
      </Campo>
    </>
  )
}
