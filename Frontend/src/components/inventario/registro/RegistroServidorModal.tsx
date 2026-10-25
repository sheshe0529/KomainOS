import { useState } from 'react'
import { ArrowLeft, ArrowRight, Check } from 'lucide-react'
import { servidoresApi } from '@/api/inventario'
import type { FichaServidorRespuesta, ServidorPeticion, UsuarioRespuesta } from '@/api/types'
import type { Catalogos } from '@/hooks/useCatalogos'
import { Boton } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { ServidorFormulario } from '../ServidorFormulario'
import { MantenimientoPaso } from './MantenimientoPaso'
import { PasosRegistro } from './PasosRegistro'
import { PlanificacionPaso } from './PlanificacionPaso'
import { prepararServidor, problemaMantenimiento, type ServidorPreparado } from './preparacion'
import { ResumenRegistro } from './ResumenRegistro'

interface Props {
  abierto: boolean
  catalogos: Catalogos
  responsables: UsuarioRespuesta[]
  onCerrar: () => void
  onGuardado: (s: FichaServidorRespuesta) => void
}

export function RegistroServidorModal({ abierto, catalogos, responsables, onCerrar, onGuardado }: Props) {
  const [paso, setPaso] = useState(0)
  const [datos, setDatos] = useState<ServidorPeticion>()
  const [servidores, setServidores] = useState<ServidorPreparado[]>([])
  const [revisado, setRevisado] = useState(false)
  const [trabajando, setTrabajando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function analizar(peticion: ServidorPeticion) {
    setTrabajando(true)
    try {
      await servidoresApi.analizarRegistro(peticion)
      // Conserva los ajustes al volver a los datos, salvo que cambie la criticidad o el SO.
      if (!datos || datos.idNivelCriticidad !== peticion.idNivelCriticidad || datos.idVersionSistemaOperativo !== peticion.idVersionSistemaOperativo) {
        setServidores([prepararServidor(peticion, catalogos)])
      } else {
        setServidores((anteriores) => anteriores.map((s) => ({ ...s, hostname: peticion.hostname, direccionIp: peticion.direccionIp })))
      }
      setDatos(peticion)
      setRevisado(false)
      setError(undefined)
      setPaso(1)
    } finally {
      setTrabajando(false)
    }
  }

  function continuar() {
    const problema = paso === 2 ? servidores.map(problemaMantenimiento).find(Boolean) : undefined
    if (problema) { setError(new Error(problema)); return }
    setError(undefined)
    setPaso(paso + 1)
  }

  async function confirmar() {
    if (!datos || servidores.length !== 1) return
    setTrabajando(true)
    setError(undefined)
    try {
      onGuardado(await servidoresApi.registrar({ servidor: datos, mantenimiento: servidores[0].mantenimiento }))
    } catch (e) { setError(e) } finally { setTrabajando(false) }
  }

  const cerrar = () => { if (!trabajando) onCerrar() }
  return <Modal abierto={abierto} onCerrar={cerrar} titulo="Registrar servidor" ancho="xl" descripcion="Prepare los datos y el mantenimiento antes de confirmar el registro."
    pie={<>
      {paso > 0 && <Boton icono={ArrowLeft} disabled={trabajando} onClick={() => { setError(undefined); setPaso(paso - 1) }} className="mr-auto">Atrás</Boton>}
      <Boton onClick={cerrar} disabled={trabajando}>Cancelar</Boton>
      {paso === 0 ? <Boton type="submit" form="form-servidor" variante="primario" icono={ArrowRight} cargando={trabajando}>Analizar datos</Boton>
        : paso === 4 ? <Boton variante="primario" icono={Check} onClick={confirmar} cargando={trabajando}>Confirmar registro</Boton>
          : <Boton variante="primario" icono={ArrowRight} onClick={continuar} disabled={paso === 1 && !revisado}>Continuar</Boton>}
    </>}>
    <div className="flex flex-col gap-5">
      <PasosRegistro paso={paso} />
      <MensajeError error={error} />
      {/* Se mantiene montado para conservar los datos al volver a la primera etapa. */}
      <div hidden={paso !== 0}><ServidorFormulario abierto sinModal catalogos={catalogos} responsables={responsables} onCerrar={cerrar} onGuardado={onGuardado} onPreparado={analizar} /></div>
      {paso === 1 && datos && <div className="flex flex-col gap-4">
        <div className="rounded-lg bg-success-soft p-4 text-sm text-success">Análisis correcto: hostname e IP disponibles y referencias válidas. El servidor está listo para configurar su mantenimiento.</div>
        <dl className="grid gap-3 rounded-lg border border-line p-4 text-sm sm:grid-cols-2">
          <div><dt className="text-ink-soft">Hostname</dt><dd className="font-mono">{datos.hostname}</dd></div>
          <div><dt className="text-ink-soft">Direcciones IP</dt><dd className="font-mono">{[datos.direccionIp, ...(datos.direccionesIpAdicionales ?? [])].join(', ')}</dd></div>
          <div><dt className="text-ink-soft">Sistema operativo</dt><dd>{servidores[0]?.sistemaOperativo}</dd></div>
          <div><dt className="text-ink-soft">Criticidad</dt><dd>{servidores[0]?.criticidad}</dd></div>
          <div><dt className="text-ink-soft">Entorno</dt><dd>{catalogos.entornos.find((e) => e.id === datos.idEntorno)?.nombre}</dd></div>
          <div><dt className="text-ink-soft">Responsable</dt><dd>{responsables.find((r) => r.id === datos.idResponsable)?.nombreCompleto}</dd></div>
        </dl>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={revisado} onChange={(e) => setRevisado(e.target.checked)} className="accent-[var(--color-accent)]" />He revisado los datos y confirmo que son correctos.</label>
      </div>}
      {paso === 2 && <MantenimientoPaso servidores={servidores} onCambiar={setServidores} />}
      {paso === 3 && <PlanificacionPaso servidores={servidores} onCambiar={setServidores} />}
      {paso === 4 && <ResumenRegistro servidores={servidores} />}
    </div>
  </Modal>
}
