import { useEffect, useState } from 'react'
import { FileCode2 } from 'lucide-react'
import { cuentasServicioApi } from '@/api/credenciales'
import type { MetodoElevacion, TipoAutenticacion } from '@/api/dominio'
import type { VariableAnsibleRespuesta, VistaAnsibleRespuesta } from '@/api/types'
import { ETIQUETA_FAMILIA } from '@/utils/etiquetas'
import { textoDeError } from '@/utils/errores'

interface VistaAnsibleProps {
  usuario: string
  tipo: TipoAutenticacion
  metodo: MetodoElevacion
  conContrasena: boolean
}

const GRUPOS: { clave: 'cuenta' | 'controlador' | 'servidor'; comentario: string }[] = [
  { clave: 'cuenta', comentario: 'De la cuenta' },
  { clave: 'controlador', comentario: 'Del controlador (iguales para todas las cuentas)' },
  { clave: 'servidor', comentario: 'Del servidor donde se use' },
]

/**
 * Configuración de Ansible que resulta de la cuenta (DEC-57): la genera el backend con el mismo código que arma el
 * inventario de cada ejecución, con los secretos enmascarados, así lo que se ve es lo que se ejecuta.
 */
export function VistaAnsible({ usuario, tipo, metodo, conContrasena }: VistaAnsibleProps) {
  const [vistas, setVistas] = useState<VistaAnsibleRespuesta[]>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    let vigente = true
    const espera = setTimeout(() => {
      cuentasServicioApi
        .vistaAnsible({
          usuarioAcceso: usuario.trim() || undefined,
          tipoAutenticacion: tipo,
          metodoElevacion: metodo,
          elevacionConContrasena: conContrasena,
        })
        .then((v) => {
          if (vigente) {
            setVistas(v)
            setError(undefined)
          }
        })
        .catch((e) => vigente && setError(textoDeError(e)))
    }, 300)
    return () => {
      vigente = false
      clearTimeout(espera)
    }
  }, [usuario, tipo, metodo, conContrasena])

  return (
    <section className="flex flex-col gap-2" aria-label="Configuración de Ansible">
      <h3 className="flex items-center gap-2 text-sm font-medium text-ink">
        <FileCode2 className="h-4 w-4 text-ink-faint" aria-hidden="true" />
        Configuración de Ansible
      </h3>
      <p className="text-xs text-ink-soft">
        Lo que recibirá Ansible en cada ejecución de esta cuenta. Los secretos van enmascarados: Ansible los recibe en un
        inventario temporal con permisos 0600 que se borra al terminar.
        {metodo === 'RUNAS' && ' Con runas, ansible_become_password es la misma contraseña de la cuenta.'}
      </p>
      {error && <p className="text-xs text-danger">{error}</p>}
      {vistas?.length === 0 && <p className="text-xs text-danger">Esta combinación no sirve para Linux ni para Windows.</p>}
      {vistas?.map((v) => (
        <div key={v.familia} className="flex flex-col gap-1">
          {vistas.length > 1 && v.familia && (
            <span className="text-xs font-medium text-ink-soft">En un servidor {ETIQUETA_FAMILIA[v.familia]}</span>
          )}
          <pre className="overflow-x-auto rounded-lg border border-line bg-panel-muted px-3 py-2 font-mono text-xs leading-5 text-ink">
            {GRUPOS.map((g) => (
              <Grupo key={g.clave} comentario={g.comentario} variables={v[g.clave] ?? []} />
            ))}
          </pre>
        </div>
      ))}
    </section>
  )
}

function Grupo({ comentario, variables }: { comentario: string; variables: VariableAnsibleRespuesta[] }) {
  if (variables.length === 0) return null
  return (
    <>
      <span className="text-ink-faint"># {comentario}</span>
      {'\n'}
      {variables.map((x) => (
        <span key={x.nombre}>
          <span className="text-accent">{x.nombre}</span>: {x.valor}
          {'\n'}
        </span>
      ))}
    </>
  )
}
