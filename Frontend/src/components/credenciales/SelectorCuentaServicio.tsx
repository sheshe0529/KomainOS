import type { FinalidadCuentaServicio } from '@/api/credenciales'
import type { FamiliaSistemaOperativo } from '@/api/dominio'
import type { CuentaServicioRespuesta } from '@/api/types'
import { Campo, Selector } from '@/components/ui/Campo'
import { FINALIDADES_CUENTA, rotuloCuentaServicio } from '@/utils/cuentasServicio'

interface SelectorCuentaServicioProps {
  finalidad: FinalidadCuentaServicio
  cuentas: CuentaServicioRespuesta[]
  valor?: number
  onCambiar: (id?: number) => void
  familia?: FamiliaSistemaOperativo
  deshabilitado?: boolean
  error?: string
}

export function SelectorCuentaServicio({ finalidad, cuentas, valor, onCambiar, familia, deshabilitado, error }: SelectorCuentaServicioProps) {
  const campos = FINALIDADES_CUENTA.find((item) => item.finalidad === finalidad)!
  const predeterminada = cuentas.find((cuenta) => cuenta[campos.campoPredeterminada])
  const efectiva = valor ? cuentas.find((cuenta) => cuenta.id === valor) : predeterminada
  const incompatible = familia === 'WINDOWS' && efectiva?.tipoAutenticacion === 'LLAVE_SSH'

  return (
    <Campo
      etiqueta={`Cuenta de ${campos.etiqueta.toLowerCase()}`}
      error={error ?? (incompatible ? 'Windows requiere una cuenta con contraseña.' : undefined)}
      ayuda={finalidad === 'EVALUACION' ? 'Para las revisiones y las evaluaciones previa y posterior.' : 'Para probar el acceso y ejecutar las tareas de mantenimiento.'}
    >
      <Selector value={valor ?? ''} onChange={(evento) => onCambiar(evento.target.value ? Number(evento.target.value) : undefined)} disabled={deshabilitado}>
        <option value="">
          {predeterminada ? (
            <>{rotuloCuentaServicio(predeterminada)} <em className="text-ink-faint">predeterminada del sistema</em></>
          ) : (
            <><em>Predeterminada del sistema</em> (sin definir)</>
          )}
        </option>
        {cuentas
          .filter((cuenta) => cuenta.estado === 'VIGENTE' || cuenta.id === valor)
          .map((cuenta) => (
            <option key={cuenta.id} value={cuenta.id} disabled={cuenta.estado !== 'VIGENTE' || (familia === 'WINDOWS' && cuenta.tipoAutenticacion === 'LLAVE_SSH')}>
              {rotuloCuentaServicio(cuenta)}
            </option>
          ))}
      </Selector>
    </Campo>
  )
}
