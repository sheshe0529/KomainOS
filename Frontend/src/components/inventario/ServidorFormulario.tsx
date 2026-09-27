import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import { servidoresApi } from '@/api/inventario'
import type { FichaServidorRespuesta, ServidorPeticion } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada, Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import type { Catalogos } from '@/hooks/useCatalogos'
import type { UsuarioRespuesta } from '@/api/types'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'

interface ServidorFormularioProps {
  abierto: boolean
  onCerrar: () => void
  onGuardado: (servidor: FichaServidorRespuesta) => void
  catalogos: Catalogos
  responsables: UsuarioRespuesta[]
  /** Si se indica, el formulario edita ese servidor (RF10); si no, registra uno nuevo (RF09). */
  servidor?: FichaServidorRespuesta
}

type Formulario = Record<keyof ServidorPeticion, string>

function inicial(s?: FichaServidorRespuesta): Formulario {
  return {
    hostname: s?.hostname ?? '',
    direccionIp: s?.direccionIp ?? '',
    datacenter: s?.datacenter ?? '',
    servidorFisico: s?.servidorFisico ?? '',
    vlan: s?.vlan ?? '',
    cluster: s?.cluster ?? '',
    dns: s?.dns ?? '',
    idVersionSistemaOperativo: s?.versionSistemaOperativo?.id?.toString() ?? '',
    plataforma: s?.plataforma ?? '',
    idEntorno: s?.entorno?.id?.toString() ?? '',
    idNivelCriticidad: s?.criticidad?.id?.toString() ?? '',
    idResponsable: s?.responsable?.id?.toString() ?? '',
    descripcion: s?.descripcion ?? '',
  }
}

/**
 * Alta y edición de un servidor (RF09, RF10, HU06). El backend detecta los
 * duplicados de hostname e IP y valida cada campo; sus mensajes se muestran
 * junto al campo correspondiente.
 */
export function ServidorFormulario({ abierto, onCerrar, onGuardado, catalogos, responsables, servidor }: ServidorFormularioProps) {
  const [f, setF] = useState<Formulario>(() => inicial(servidor))
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()
  const esEdicion = servidor?.id !== undefined

  const cambiar = (campo: keyof Formulario) => (valor: string) => setF((prev) => ({ ...prev, [campo]: valor }))

  // Al editar se muestran también las referencias actuales aunque estén
  // inactivas; al crear, solo las activas.
  const entornos = catalogos.entornos.filter((e) => e.activo || e.id === servidor?.entorno?.id)
  const criticidades = catalogos.criticidades.filter((c) => c.activo || c.id === servidor?.criticidad?.id)
  const versiones = catalogos.sistemasOperativos.flatMap((so) =>
    (so.versiones ?? [])
      .filter((v) => (so.activo && v.activo) || v.id === servidor?.versionSistemaOperativo?.id)
      .map((v) => ({ id: v.id, nombre: `${so.nombre} ${v.version}` })),
  )
  const opcionesResponsable = [...responsables]
  if (servidor?.responsable?.id && !responsables.some((r) => r.id === servidor.responsable?.id)) {
    opcionesResponsable.push({ id: servidor.responsable.id, nombreCompleto: servidor.responsable.nombre })
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    const peticion: ServidorPeticion = {
      hostname: f.hostname.trim(),
      direccionIp: f.direccionIp.trim(),
      datacenter: f.datacenter || undefined,
      servidorFisico: f.servidorFisico || undefined,
      vlan: f.vlan || undefined,
      cluster: f.cluster || undefined,
      dns: f.dns || undefined,
      idVersionSistemaOperativo: Number(f.idVersionSistemaOperativo),
      plataforma: f.plataforma || undefined,
      idEntorno: Number(f.idEntorno),
      idNivelCriticidad: Number(f.idNivelCriticidad),
      idResponsable: Number(f.idResponsable),
      descripcion: f.descripcion || undefined,
    }
    try {
      const guardado = esEdicion
        ? await servidoresApi.actualizar(servidor!.id!, peticion)
        : await servidoresApi.crear(peticion)
      onGuardado(guardado)
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  const texto = (campo: keyof Formulario, etiqueta: string, obligatorio = false, ayuda?: string) => (
    <Campo etiqueta={etiqueta} obligatorio={obligatorio} error={errorDeCampo(error, campo)} ayuda={ayuda}>
      <Entrada value={f[campo]} onChange={(e) => cambiar(campo)(e.target.value)} required={obligatorio} />
    </Campo>
  )

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      ancho="lg"
      titulo={esEdicion ? `Editar ${servidor?.hostname}` : 'Registrar servidor'}
      descripcion={
        esEdicion
          ? 'Los cambios quedan registrados en la auditoría.'
          : 'El servidor queda pendiente de configuración hasta definir su configuración de mantenimiento.'
      }
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-servidor" variante="primario" icono={Save} cargando={guardando}>
            Guardar
          </Boton>
        </>
      }
    >
      <form id="form-servidor" onSubmit={guardar} className="flex flex-col gap-4">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <div className="grid gap-4 sm:grid-cols-2">
          {texto('hostname', 'Hostname', true)}
          {texto('direccionIp', 'Dirección IP', true, 'IPv4 o IPv6')}
          <Campo etiqueta="Sistema operativo y versión" obligatorio error={errorDeCampo(error, 'idVersionSistemaOperativo')}>
            <Selector value={f.idVersionSistemaOperativo} onChange={(e) => cambiar('idVersionSistemaOperativo')(e.target.value)} required>
              <option value="">Seleccione…</option>
              {versiones.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.nombre}
                </option>
              ))}
            </Selector>
          </Campo>
          {texto('plataforma', 'Plataforma', false, 'Por ejemplo, VMware')}
          <Campo etiqueta="Entorno" obligatorio error={errorDeCampo(error, 'idEntorno')}>
            <Selector value={f.idEntorno} onChange={(e) => cambiar('idEntorno')(e.target.value)} required>
              <option value="">Seleccione…</option>
              {entornos.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.nombre}
                </option>
              ))}
            </Selector>
          </Campo>
          <Campo etiqueta="Criticidad" obligatorio error={errorDeCampo(error, 'idNivelCriticidad')}>
            <Selector value={f.idNivelCriticidad} onChange={(e) => cambiar('idNivelCriticidad')(e.target.value)} required>
              <option value="">Seleccione…</option>
              {criticidades.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nombre}
                </option>
              ))}
            </Selector>
          </Campo>
          <Campo etiqueta="Responsable" obligatorio error={errorDeCampo(error, 'idResponsable')}>
            <Selector value={f.idResponsable} onChange={(e) => cambiar('idResponsable')(e.target.value)} required>
              <option value="">Seleccione…</option>
              {opcionesResponsable.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.nombreCompleto}
                </option>
              ))}
            </Selector>
          </Campo>
          {texto('datacenter', 'Datacenter')}
          {texto('servidorFisico', 'Servidor físico')}
          {texto('cluster', 'Clúster')}
          {texto('vlan', 'VLAN')}
          {texto('dns', 'DNS')}
        </div>
        <Campo etiqueta="Descripción" error={errorDeCampo(error, 'descripcion')}>
          <AreaTexto value={f.descripcion} onChange={(e) => cambiar('descripcion')(e.target.value)} />
        </Campo>
      </form>
    </Modal>
  )
}
