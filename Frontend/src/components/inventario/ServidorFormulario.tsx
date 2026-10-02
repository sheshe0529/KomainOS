import { useState } from 'react'
import type { FormEvent } from 'react'
import { Plus, Save, Trash2 } from 'lucide-react'
import { servidoresApi } from '@/api/inventario'
import type { FichaServidorRespuesta, ServidorPeticion } from '@/api/types'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada, Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import type { Catalogos } from '@/hooks/useCatalogos'
import type { UsuarioRespuesta } from '@/api/types'
import { ErrorApi } from '@/api/cliente'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'

interface ServidorFormularioProps {
  abierto: boolean
  onCerrar: () => void
  onGuardado: (servidor: FichaServidorRespuesta) => void
  catalogos: Catalogos
  responsables: UsuarioRespuesta[]
  /** Si se indica edita ese servidor (RF10), si no registra uno nuevo (RF09) */
  servidor?: FichaServidorRespuesta
}

/** Las direcciones IP se editan como lista aparte */
type CampoTexto = Exclude<keyof ServidorPeticion, 'direccionIp' | 'direccionesIpAdicionales'>
type Formulario = Record<CampoTexto, string>

/** La principal y hasta 20 adicionales */
const MAXIMO_DIRECCIONES = 21

function inicial(s?: FichaServidorRespuesta): Formulario {
  return {
    hostname: s?.hostname ?? '',
    vdc: s?.vdc ?? '',
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
    cantidadCpu: s?.cantidadCpu?.toString() ?? '',
    ramGb: s?.ramGb?.toString() ?? '',
    hdVirtualGb: s?.hdVirtualGb?.toString() ?? '',
  }
}

function errorDeDirecciones(error: unknown): string | undefined {
  if (!(error instanceof ErrorApi)) return undefined
  return error.errores?.find((e) => e.campo?.startsWith('direccionIp') || e.campo?.startsWith('direccionesIp'))?.mensaje
}

/** Una o varias IP, una de ellas principal y ninguna de otro servidor (DEC-37) */
export function ServidorFormulario({ abierto, onCerrar, onGuardado, catalogos, responsables, servidor }: ServidorFormularioProps) {
  const [f, setF] = useState<Formulario>(() => inicial(servidor))
  // La principal va primero (así la devuelve el backend)
  const [ips, setIps] = useState<string[]>(() =>
    servidor?.direccionesIp?.length ? servidor.direccionesIp.map((d) => d.direccion ?? '') : [servidor?.direccionIp ?? ''],
  )
  const [principal, setPrincipal] = useState(0)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()
  const esEdicion = servidor?.id !== undefined

  const cambiar = (campo: CampoTexto) => (valor: string) => setF((prev) => ({ ...prev, [campo]: valor }))

  function cambiarIp(indice: number, valor: string) {
    setIps((actuales) => actuales.map((ip, i) => (i === indice ? valor : ip)))
  }

  function quitarIp(indice: number) {
    setIps((actuales) => actuales.filter((_, i) => i !== indice))
    // La principal sigue apuntando a la misma dirección, si se quita pasa a la primera
    setPrincipal((p) => (indice === p ? 0 : indice < p ? p - 1 : p))
  }

  // Al editar se muestran también las referencias actuales aunque estén inactivas
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
    const direcciones = ips.map((ip) => ip.trim())
    const numero = (valor: string) => (valor.trim() ? Number(valor) : undefined)
    const peticion: ServidorPeticion = {
      hostname: f.hostname.trim(),
      direccionIp: direcciones[principal],
      // Las filas vacías se ignoran, la principal es obligatoria en su propio campo
      direccionesIpAdicionales: direcciones.filter((ip, i) => i !== principal && ip !== ''),
      vdc: f.vdc || undefined,
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
      cantidadCpu: numero(f.cantidadCpu),
      ramGb: numero(f.ramGb),
      hdVirtualGb: numero(f.hdVirtualGb),
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

  const texto = (campo: CampoTexto, etiqueta: string, obligatorio = false, ayuda?: string) => (
    <Campo etiqueta={etiqueta} obligatorio={obligatorio} error={errorDeCampo(error, campo)} ayuda={ayuda}>
      <Entrada value={f[campo]} onChange={(e) => cambiar(campo)(e.target.value)} required={obligatorio} />
    </Campo>
  )

  const numero = (campo: CampoTexto, etiqueta: string, paso: string, ayuda?: string) => (
    <Campo etiqueta={etiqueta} error={errorDeCampo(error, campo)} ayuda={ayuda}>
      <Entrada
        type="number"
        min={paso}
        step={paso}
        inputMode={paso === '1' ? 'numeric' : 'decimal'}
        value={f[campo]}
        onChange={(e) => cambiar(campo)(e.target.value)}
      />
    </Campo>
  )

  const errorIps = errorDeDirecciones(error)

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
      <form id="form-servidor" onSubmit={guardar} className="flex flex-col gap-5">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <div className="grid gap-4 sm:grid-cols-2">
          {texto('hostname', 'Hostname', true)}
          {texto('dns', 'DNS')}
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-medium text-ink">
            Direcciones IP<span className="ml-0.5 text-danger">*</span>
          </legend>
          <p className="text-xs text-ink-faint">
            Marque la principal: es la que identifica al servidor y la que usa el sistema para conectarse. Una IP no puede
            pertenecer a dos servidores.
          </p>
          <ul className="flex flex-col gap-2">
            {ips.map((ip, i) => (
              <li key={i} className="flex items-center gap-2">
                <label className="flex shrink-0 cursor-pointer items-center gap-1.5 text-xs text-ink-soft" title="Dirección principal">
                  <input
                    type="radio"
                    name="ip-principal"
                    checked={principal === i}
                    onChange={() => setPrincipal(i)}
                    className="accent-[var(--color-accent)]"
                    aria-label={`Marcar ${ip || 'esta dirección'} como principal`}
                  />
                  <span className={`w-16 ${principal === i ? 'font-medium text-accent' : ''}`}>
                    {principal === i ? 'Principal' : 'Adicional'}
                  </span>
                </label>
                <Entrada
                  value={ip}
                  onChange={(e) => cambiarIp(i, e.target.value)}
                  placeholder="IPv4 o IPv6"
                  aria-label={principal === i ? 'Dirección IP principal' : `Dirección IP adicional ${i + 1}`}
                  required={principal === i}
                  className="font-mono"
                />
                <BotonIcono icono={Trash2} etiqueta="Quitar dirección" onClick={() => quitarIp(i)} disabled={ips.length === 1} />
              </li>
            ))}
          </ul>
          {errorIps && <span className="text-xs text-danger">{errorIps}</span>}
          {ips.length < MAXIMO_DIRECCIONES && (
            <Boton icono={Plus} variante="fantasma" className="self-start" onClick={() => setIps((a) => [...a, ''])}>
              Agregar IP
            </Boton>
          )}
        </fieldset>

        <div className="grid gap-4 sm:grid-cols-2">
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
          {texto('vdc', 'VDC', false, 'Virtual DataCenter')}
          {texto('servidorFisico', 'Servidor físico')}
          {texto('cluster', 'Clúster')}
          {texto('vlan', 'VLAN')}
        </div>

        <fieldset>
          <legend className="mb-2 text-sm font-medium text-ink">Recursos</legend>
          <div className="grid gap-4 sm:grid-cols-3">
            {numero('cantidadCpu', 'CPU', '1', 'Cantidad de vCPU')}
            {numero('ramGb', 'RAM (GB)', '0.01')}
            {numero('hdVirtualGb', 'Disco virtual (GB)', '0.01', 'Capacidad total')}
          </div>
        </fieldset>

        <Campo etiqueta="Descripción" error={errorDeCampo(error, 'descripcion')}>
          <AreaTexto value={f.descripcion} onChange={(e) => cambiar('descripcion')(e.target.value)} />
        </Campo>
      </form>
    </Modal>
  )
}
