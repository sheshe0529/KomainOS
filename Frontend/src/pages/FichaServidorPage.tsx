import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { servidoresApi } from '@/api/inventario'
import type { FichaServidorRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { Tarjeta } from '@/components/common/Tarjeta'
import { CredencialesDocumentales } from '@/components/credenciales/CredencialesDocumentales'
import { AvisosServidor } from '@/components/inventario/AvisosServidor'
import { BajaModal } from '@/components/inventario/BajaModal'
import { ConfiguracionModal } from '@/components/inventario/ConfiguracionModal'
import { ConfiguracionServidorTarjeta } from '@/components/inventario/ConfiguracionServidorTarjeta'
import { DatosGeneralesServidor } from '@/components/inventario/DatosGeneralesServidor'
import { EncabezadoFichaServidor, type DialogoFichaServidor } from '@/components/inventario/EncabezadoFichaServidor'
import { HistorialBajasServidor } from '@/components/inventario/HistorialBajasServidor'
import { RecursosServidor } from '@/components/inventario/RecursosServidor'
import { ServidorFormulario } from '@/components/inventario/ServidorFormulario'
import { VentanaPermisivaTarjeta } from '@/components/inventario/VentanaPermisivaTarjeta'
import { VentanasModal } from '@/components/inventario/VentanasModal'
import { OrdenesDelObjetivo } from '@/components/planificacion/OrdenesDelObjetivo'
import { ProgramarModal } from '@/components/planificacion/ProgramarModal'
import { useAvisos } from '@/components/ui/avisos-context'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { useCatalogos, useResponsables } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { textoDeError } from '@/utils/errores'

function VolverAlInventario() {
  return (
    <Link to="/servidores" className="inline-flex items-center gap-1 self-start text-sm text-ink-soft hover:text-ink">
      <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver al inventario
    </Link>
  )
}

export function FichaServidorPage() {
  const { id } = useParams()
  const idServidor = Number(id)
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  // Si el responsable ve la ficha, el servidor es suyo: el backend responde 404 si no (RF19)
  const puedeEditarVentana = tieneRol('ADMINISTRADOR', 'RESPONSABLE')
  const { avisar } = useAvisos()
  const [dialogo, setDialogo] = useState<DialogoFichaServidor | null>(null)
  const [reactivando, setReactivando] = useState(false)
  const [versionOrdenes, setVersionOrdenes] = useState(0)

  const ficha = useConsulta(() => servidoresApi.ficha(idServidor), [idServidor])
  const catalogos = useCatalogos()
  const responsables = useResponsables(esAdmin)
  const s = ficha.datos

  function actualizar(nueva: FichaServidorRespuesta, mensaje: string) {
    ficha.reemplazar(nueva)
    setDialogo(null)
    avisar(mensaje)
  }

  /** Programar, configurar, cambiar la ventana o dar de baja cambian las órdenes del servidor */
  const recargarOrdenes = () => setVersionOrdenes((v) => v + 1)

  async function reactivar() {
    setReactivando(true)
    try {
      actualizar(await servidoresApi.reactivar(idServidor), 'Servidor reactivado: complete su nueva configuración de mantenimiento.')
    } catch (e) {
      avisar(textoDeError(e), 'error')
    } finally {
      setReactivando(false)
    }
  }

  if (ficha.cargando && !s) return <Cargando texto="Cargando ficha del servidor…" />
  if (ficha.error && !s) {
    return (
      <div className="flex flex-col gap-4">
        <VolverAlInventario />
        <MensajeError error={ficha.error} onReintentar={ficha.recargar} />
      </div>
    )
  }
  if (!s) return null

  const dadoDeBaja = s.estado === 'DADO_DE_BAJA'
  const criticidad = catalogos.datos?.criticidades.find((c) => c.id === s.criticidad?.id)

  return (
    <div className="flex flex-col gap-6">
      <VolverAlInventario />
      <EncabezadoFichaServidor
        servidor={s}
        esAdmin={esAdmin}
        puedeEditarVentana={puedeEditarVentana}
        catalogosListos={!!catalogos.datos}
        reactivando={reactivando}
        onAbrir={setDialogo}
        onReactivar={reactivar}
      />
      <AvisosServidor servidor={s} />

      <div className="grid gap-6 lg:grid-cols-3">
        <DatosGeneralesServidor servidor={s} className="lg:col-span-2" />
        <div className="flex flex-col gap-6">
          <Tarjeta titulo="Recursos">
            <RecursosServidor cantidadCpu={s.cantidadCpu} ramGb={s.ramGb} hdVirtualGb={s.hdVirtualGb} />
          </Tarjeta>
          <ConfiguracionServidorTarjeta configuracion={s.configuracion} />
        </div>
      </div>

      <VentanaPermisivaTarjeta
        ventanas={s.ventanas ?? []}
        vacio="Sin ventana definida: el servidor no puede planificarse."
        onEditar={puedeEditarVentana && !dadoDeBaja ? () => setDialogo('ventanas') : undefined}
      />
      <HistorialBajasServidor servidor={s} />
      {esAdmin && (
        <CredencialesDocumentales idServidor={idServidor} hostname={s.hostname ?? ''} familia={s.familiaSistemaOperativo} editable={!dadoDeBaja} />
      )}
      <OrdenesDelObjetivo idServidor={idServidor} version={versionOrdenes} />

      {dialogo === 'programar' && (
        <ProgramarModal
          abierto
          objetivo={{ idServidor, nombre: s.hostname ?? '' }}
          onCerrar={() => setDialogo(null)}
          onGuardado={(orden) => {
            setDialogo(null)
            recargarOrdenes()
            avisar(`Orden ${orden.resumen?.codigo} programada.`)
          }}
        />
      )}
      {dialogo === 'editar' && catalogos.datos && (
        <ServidorFormulario
          abierto
          servidor={s}
          catalogos={catalogos.datos}
          responsables={responsables.datos ?? []}
          onCerrar={() => setDialogo(null)}
          onGuardado={(nuevo) => actualizar(nuevo, 'Datos del servidor actualizados.')}
        />
      )}
      {dialogo === 'configurar' && (
        <ConfiguracionModal
          abierto
          titulo={`Configuración de mantenimiento de ${s.hostname}`}
          actual={s.configuracion}
          recomendadas={{ revision: criticidad?.frecuenciaRevisionDias, mantenimiento: criticidad?.frecuenciaMantenimientoDias }}
          familia={s.familiaSistemaOperativo}
          onCerrar={() => setDialogo(null)}
          onGuardar={async (datos) => {
            actualizar(
              await servidoresApi.configurar(idServidor, {
                frecuenciaRevisionDias: datos.frecuenciaRevisionDias,
                frecuenciaMantenimientoDias: datos.frecuenciaMantenimientoDias,
                modalidadPlanificacion: datos.modalidadPlanificacion,
                idCuentaServicio: datos.idCuentaServicio,
              }),
              'Configuración de mantenimiento guardada.',
            )
            // En modalidad automática el backend genera el primer ciclo antes de responder (RF27): basta con recargar el historial
            recargarOrdenes()
          }}
        />
      )}
      {dialogo === 'ventanas' && (
        <VentanasModal
          abierto
          hostname={s.hostname ?? ''}
          actuales={s.ventanas ?? []}
          onCerrar={() => setDialogo(null)}
          onGuardar={async (ventanas) => {
            actualizar(await servidoresApi.reemplazarVentanas(idServidor, ventanas), 'Ventana permisiva actualizada.')
            recargarOrdenes()
          }}
        />
      )}
      {dialogo === 'baja' && (
        <BajaModal
          abierto
          hostname={s.hostname ?? ''}
          onCerrar={() => setDialogo(null)}
          onConfirmar={async (motivo) => {
            const resultado = await servidoresApi.darDeBaja(idServidor, { motivo })
            if (resultado.servidor) ficha.reemplazar(resultado.servidor)
            setDialogo(null)
            recargarOrdenes()
            avisar(resultado.mensaje ?? 'Solicitud de baja registrada.', resultado.aplicada ? 'exito' : 'info')
          }}
        />
      )}
    </div>
  )
}
