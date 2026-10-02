import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Download, KeyRound, Plus, Upload } from 'lucide-react'
import { servidoresApi, type FiltroServidores } from '@/api/inventario'
import type { FichaServidorRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { ExportarCredencialesModal } from '@/components/credenciales/ExportarCredencialesModal'
import { COLUMNAS_SERVIDORES, DEFINICIONES_SERVIDORES } from '@/components/inventario/columnasServidores'
import { ExportarModal } from '@/components/inventario/ExportarModal'
import { FiltrosServidores } from '@/components/inventario/FiltrosServidores'
import { ImportarModal } from '@/components/inventario/ImportarModal'
import { ServidorFormulario } from '@/components/inventario/ServidorFormulario'
import { TablaServidores, type Orden } from '@/components/inventario/TablaServidores'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { useCatalogos, useResponsables } from '@/hooks/useCatalogos'
import { useColumnasVisibles } from '@/hooks/useColumnasVisibles'
import { useConsulta } from '@/hooks/useConsulta'
import { useTonoCriticidad } from '@/hooks/useTonoCriticidad'

export function ServidoresPage() {
  const tonoCriticidad = useTonoCriticidad()
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const navigate = useNavigate()
  const { avisar } = useAvisos()
  const columnas = useColumnasVisibles('servidores', DEFINICIONES_SERVIDORES)

  const [busqueda, setBusqueda] = useState('')
  const [textoAplicado, setTextoAplicado] = useState('')
  const [filtro, setFiltro] = useState<FiltroServidores>({ estado: '', pagina: 0, tamano: 20 })
  const [orden, setOrden] = useState<Orden>({ propiedad: 'hostname', asc: true })
  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [dialogo, setDialogo] = useState<'importar' | 'exportar' | 'credenciales' | null>(null)

  // La búsqueda se aplica al dejar de escribir, para no consultar por cada tecla
  useEffect(() => {
    const t = window.setTimeout(() => {
      setTextoAplicado(busqueda)
      setFiltro((f) => ({ ...f, pagina: 0 }))
    }, 350)
    return () => window.clearTimeout(t)
  }, [busqueda])

  const catalogos = useCatalogos()
  const responsables = useResponsables(esAdmin)
  const servidores = useConsulta(
    () => servidoresApi.listar({ ...filtro, texto: textoAplicado, orden: `${orden.propiedad},${orden.asc ? 'asc' : 'desc'}` }),
    [filtro, textoAplicado, orden],
  )

  function alGuardar(servidor: FichaServidorRespuesta) {
    setFormularioAbierto(false)
    avisar(`Servidor ${servidor.hostname} registrado. Complete su configuración de mantenimiento.`)
    navigate(`/servidores/${servidor.id}`)
  }

  const total = servidores.datos?.totalElementos ?? 0
  const conFiltros = !!(textoAplicado || filtro.estado || filtro.idEntorno || filtro.idNivelCriticidad || filtro.idSistemaOperativo)

  return (
    <>
      <PageHeader
        title="Inventario de servidores"
        description={`${total} servidores virtuales bajo gestión`}
        actions={
          <>
            <Boton icono={Download} onClick={() => setDialogo('exportar')}>
              Exportar
            </Boton>
            {esAdmin && (
              <>
                <Boton icono={KeyRound} onClick={() => setDialogo('credenciales')}>
                  Exportar credenciales
                </Boton>
                <Boton icono={Upload} onClick={() => setDialogo('importar')}>
                  Importar
                </Boton>
                <Boton variante="primario" icono={Plus} onClick={() => setFormularioAbierto(true)} disabled={!catalogos.datos}>
                  Registrar servidor
                </Boton>
              </>
            )}
          </>
        }
      />

      <FiltrosServidores
        busqueda={busqueda}
        onBuscar={setBusqueda}
        filtro={filtro}
        onFiltrar={(cambios) => setFiltro((f) => ({ ...f, ...cambios, pagina: 0 }))}
        catalogos={catalogos.datos}
        columnas={columnas}
      />

      <MensajeError error={servidores.error} onReintentar={servidores.recargar} />

      <TablaServidores
        columnas={COLUMNAS_SERVIDORES.filter((c) => columnas.ve(c.id))}
        contexto={{ tonoCriticidad, ve: columnas.ve }}
        pagina={servidores.datos}
        cargando={servidores.cargando}
        orden={orden}
        onOrdenar={(propiedad) => setOrden((o) => ({ propiedad, asc: o.propiedad === propiedad ? !o.asc : true }))}
        onPagina={(p) => setFiltro((f) => ({ ...f, pagina: p }))}
        esAdmin={esAdmin}
        conFiltros={conFiltros}
      />

      {formularioAbierto && catalogos.datos && (
        <ServidorFormulario
          abierto
          onCerrar={() => setFormularioAbierto(false)}
          onGuardado={alGuardar}
          catalogos={catalogos.datos}
          responsables={responsables.datos ?? []}
        />
      )}
      {dialogo === 'exportar' && (
        <ExportarModal abierto filtro={{ ...filtro, texto: textoAplicado }} total={total} onCerrar={() => setDialogo(null)} />
      )}
      {dialogo === 'credenciales' && <ExportarCredencialesModal abierto onCerrar={() => setDialogo(null)} />}
      {dialogo === 'importar' && <ImportarModal abierto onCerrar={() => setDialogo(null)} onImportado={servidores.recargar} />}
    </>
  )
}
