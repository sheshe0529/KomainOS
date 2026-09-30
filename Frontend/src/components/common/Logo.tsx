import logoClaro from '@/assets/logo-komainos.png'
import logoOscuro from '@/assets/logo-komainos-oscuro.png'
import { useTheme } from '@/theme/theme-context'

interface LogoProps {
  className?: string
  /** Si el nombre «KomainOS» ya aparece al lado, la imagen no se anuncia a los lectores de pantalla. */
  decorativo?: boolean
}

/**
 * Logo de KomainOS (UI/Logo_KomainOS.png). En tema oscuro se usa una variante
 * con el azul marino aclarado, que de otro modo no se distinguiría del fondo;
 * el cian se conserva en ambas.
 */
export function Logo({ className = '', decorativo }: LogoProps) {
  const { theme } = useTheme()
  return (
    <img
      src={theme === 'dark' ? logoOscuro : logoClaro}
      alt={decorativo ? '' : 'KomainOS'}
      className={`object-contain ${className}`}
      draggable={false}
    />
  )
}
