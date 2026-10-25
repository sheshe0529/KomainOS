import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { umbralesApi } from '@/api/umbrales'
import { useSesion } from '@/auth/sesion-context'
import { ReglaDePrecedencia } from '@/components/umbrales/ReglaDePrecedencia'
import { UmbralDetalle } from '@/components/umbrales/UmbralDetalle'
import { UmbralFormulario } from '@/components/umbrales/UmbralFormulario'
import { useAvisos } from '@/components/ui/avisos-context'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { useConsulta } from '@/hooks/useConsulta'

function VolverALosUmbrales() {
  return (
    <Link to="/umbrales" className="inline-flex items-center gap-1 self-start text-sm text-ink-soft hover:text-ink">
      <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver a umbrales
    </Link>
  )
}

/** Detalle de un umbral: el administrador define las excepciones, el operador las consulta (RF16) */
export function UmbralDetallePage() {
  const { idUmbral } = useParams()
  const id = Number(idUmbral)
  const navigate = useNavigate()
  const { avisar } = useAvisos()
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const [editando, setEditando] = useState(false)
  const umbral = useConsulta(() => umbralesApi.umbral(id), [id])

  return (
    <div className="flex flex-col gap-4">
      <VolverALosUmbrales />
      <MensajeError error={umbral.error} onReintentar={umbral.recargar} />
      {umbral.cargando && !umbral.datos ? (
        <Cargando />
      ) : (
        umbral.datos && (
          <>
            <ReglaDePrecedencia />
            <UmbralDetalle
              umbral={umbral.datos}
              esAdmin={esAdmin}
              onActualizado={umbral.reemplazar}
              onRecargar={umbral.recargar}
              onEditar={() => setEditando(true)}
              onEliminado={() => navigate('/umbrales')}
            />
            {editando && (
              <UmbralFormulario
                umbral={umbral.datos}
                metricas={umbral.datos.metrica ? [umbral.datos.metrica] : []}
                onCerrar={() => setEditando(false)}
                onGuardado={(guardado) => {
                  setEditando(false)
                  umbral.reemplazar(guardado)
                  avisar('Umbral guardado.')
                }}
              />
            )}
          </>
        )
      )}
    </div>
  )
}
