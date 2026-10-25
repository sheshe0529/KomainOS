import { useState } from 'react'
import { automatizacionApi } from '@/api/automatizacion'
import type { ConexionGrupoRespuesta } from '@/api/types'
import { Tarjeta } from '@/components/common/Tarjeta'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { useSesion } from '@/auth/sesion-context'

export function ConectividadGrupo({ id }: { id: number }) {
  const { tieneRol } = useSesion()
  const [resultados, setResultados] = useState<ConexionGrupoRespuesta[]>()
  const [trabajando, setTrabajando] = useState(false)
  const [error, setError] = useState<unknown>()
  if (!tieneRol('ADMINISTRADOR')) return null

  async function probar() {
    setTrabajando(true)
    setError(undefined)
    try {
      setResultados(await automatizacionApi.probarGrupo(id))
    } catch (e) {
      setError(e)
    } finally {
      setTrabajando(false)
    }
  }

  return (
    <Tarjeta
      titulo="Conectividad del grupo"
      acciones={
        <Boton cargando={trabajando} onClick={probar}>
          Probar todos los servidores
        </Boton>
      }
    >
      <MensajeError error={error} />
      {!resultados && (
        <p className="text-sm text-ink-soft">
          Compruebe la conexión y los permisos con la cuenta de servicio del grupo. En modo simulado no se abren
          conexiones reales.
        </p>
      )}
      <div className="flex flex-col gap-2" role="status">
        {resultados?.map((r) => (
          <div key={r.idServidor} className="rounded-lg border border-line p-3 text-sm">
            <strong>
              {r.servidor} · {r.correcto ? 'Correcto' : 'Falló'}
              {r.simulado && ' · Simulado'}
            </strong>
            <p className={r.correcto ? 'text-ink-soft' : 'text-danger'}>{r.mensaje}</p>
          </div>
        ))}
      </div>
    </Tarjeta>
  )
}
