import { useState } from 'react'
import { Pencil, Power, PowerOff, Trash2 } from 'lucide-react'
import { gruposApi } from '@/api/inventario'
import { umbralesApi } from '@/api/umbrales'
import type { ExcepcionUmbralRespuesta, UmbralRespuesta } from '@/api/types'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { textoDeError } from '@/utils/errores'
import { ETIQUETA_ALCANCE, formatearLimite, NIVEL, OPERADORES, simbolo } from '@/utils/umbrales'
import { destinoDe } from './acciones'
import type { AccionesExcepcion, Destino } from './acciones'
import { ExcepcionFormulario } from './ExcepcionFormulario'
import { ListaChips } from './ListaChips'
import { SeccionServidores } from './SeccionServidores'
import { TablaPorCatalogo } from './TablaPorCatalogo'

type Confirmacion = { tipo: 'umbral' } | { tipo: 'excepcion'; excepcion: ExcepcionUmbralRespuesta }

interface FormularioAbierto {
  destino: Destino
  excepcion?: ExcepcionUmbralRespuesta
}

interface UmbralDetalleProps {
  umbral: UmbralRespuesta
  esAdmin: boolean
  onActualizado: (umbral: UmbralRespuesta) => void
  onRecargar: () => void
  onEditar: () => void
  onEliminado: () => void
}

/** Un umbral: su valor global y, por prioridad, los valores propios de servidores, grupos, entornos y criticidades */
export function UmbralDetalle({ umbral, esAdmin, onActualizado, onRecargar, onEditar, onEliminado }: UmbralDetalleProps) {
  const { avisar } = useAvisos()
  const catalogos = useCatalogos()
  const grupos = useConsulta(() => gruposApi.listar(), [])
  const [formulario, setFormulario] = useState<FormularioAbierto | null>(null)
  const [confirmar, setConfirmar] = useState<Confirmacion | null>(null)
  const [trabajando, setTrabajando] = useState(false)

  const deLista = !!umbral.metrica?.esLista
  const unidad = umbral.metrica?.unidad
  const operador = OPERADORES.find((o) => o.valor === umbral.operador)
  const excepciones = umbral.excepciones ?? []
  const columnas = deLista ? ['Lista'] : ['Advertencia', 'Crítico']

  async function ejecutar(accion: () => Promise<unknown>, mensaje: string, alTerminar: () => void = onRecargar) {
    setTrabajando(true)
    try {
      await accion()
      alTerminar()
      avisar(mensaje)
    } catch (e) {
      avisar(textoDeError(e), 'error')
    } finally {
      setTrabajando(false)
      setConfirmar(null)
    }
  }

  const acciones: AccionesExcepcion = {
    ocupado: trabajando,
    definir: (destino) => setFormulario({ destino }),
    editar: (excepcion) => setFormulario({ destino: destinoDe(excepcion), excepcion }),
    alternar: (excepcion) =>
      ejecutar(() => umbralesApi.cambiarEstadoExcepcion(umbral.id!, excepcion.id!, !excepcion.activo), 'Estado actualizado.'),
    eliminar: (excepcion) => setConfirmar({ tipo: 'excepcion', excepcion }),
  }

  /** Las celdas de valores de una fila: sin excepción, los globales atenuados */
  function celdas(propia?: ExcepcionUmbralRespuesta) {
    const tono = propia ? (propia.activo ? 'text-ink' : 'text-ink-faint line-through') : 'text-ink-faint'
    const titulo = propia ? undefined : 'Valor global'
    if (deLista) {
      const lista = propia ? propia.lista : umbral.lista
      return (
        <td className={`px-3 py-2.5 ${tono}`} title={titulo}>
          <span className="mr-2">{lista?.nombre}</span>
          <ListaChips elementos={lista?.elementos} maximo={6} atenuado={!propia} />
        </td>
      )
    }
    const valor = (v?: number) => (v === undefined || v === null ? '—' : `${simbolo(umbral.operador)} ${formatearLimite(v, unidad)}`)
    return (
      <>
        <td className={`px-3 py-2.5 font-mono tabular-nums ${tono}`} title={titulo}>
          {valor(propia ? propia.valorAdvertencia : umbral.valorAdvertencia)}
        </td>
        <td className={`px-3 py-2.5 font-mono tabular-nums ${tono}`} title={titulo}>
          {valor(propia ? propia.valorCritico : umbral.valorCritico)}
        </td>
      </>
    )
  }

  const cargandoCatalogos = !catalogos.datos && !catalogos.error

  return (
    <div className="flex flex-col gap-4">
      <section className={`rounded-xl border border-line bg-panel px-5 py-4 ${umbral.activo ? '' : 'opacity-80'}`}>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xl font-semibold text-ink">{umbral.nombre}</h2>
              <StatusPill tone={umbral.activo ? 'success' : 'neutral'} label={umbral.activo ? 'Activo' : 'Inactivo'} />
              {!umbral.metrica?.activo && <StatusPill tone="warning" label="Métrica inactiva" />}
            </div>
            <p className="mt-0.5 text-sm text-ink-soft">
              {umbral.metrica?.nombre} <span className="font-mono text-xs text-ink-faint">{umbral.metrica?.codigo}</span>
            </p>
          </div>
          {esAdmin && (
            <div className="flex items-center gap-1">
              <BotonIcono icono={Pencil} etiqueta="Editar umbral" onClick={onEditar} />
              <BotonIcono
                icono={umbral.activo ? PowerOff : Power}
                etiqueta={umbral.activo ? 'Desactivar umbral' : 'Activar umbral'}
                disabled={trabajando}
                onClick={() =>
                  ejecutar(
                    async () => onActualizado(await umbralesApi.cambiarEstadoUmbral(umbral.id!, !umbral.activo)),
                    'Estado del umbral actualizado.',
                    () => undefined,
                  )
                }
              />
              <BotonIcono icono={Trash2} etiqueta="Eliminar umbral" onClick={() => setConfirmar({ tipo: 'umbral' })} />
            </div>
          )}
        </div>

        <div className="mt-4 border-t border-line pt-4">
          <h3 className="text-sm font-semibold text-ink">Valor global</h3>
          {deLista ? (
            <dl className="mt-3 grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg bg-panel-muted px-4 py-3">
                <dt className="text-xs font-medium text-ink-soft">Lista</dt>
                <dd className="mt-1 font-medium text-ink">{umbral.lista?.nombre}</dd>
                <dd className="mt-2">
                  <ListaChips elementos={umbral.lista?.elementos} maximo={12} />
                </dd>
              </div>
              {(['nivelFaltante', 'nivelSobrante'] as const).map((campo) => {
                const nivel = umbral[campo]
                const fondo = nivel === 'CRITICO' ? 'bg-danger-soft' : nivel === 'ADVERTENCIA' ? 'bg-warning-soft' : 'bg-panel-muted'
                return (
                  <div key={campo} className={`rounded-lg px-4 py-3 ${fondo}`}>
                    <dt className="text-xs font-medium text-ink-soft">{campo === 'nivelFaltante' ? 'Si falta' : 'Si sobra'}</dt>
                    <dd className="mt-1 text-lg font-semibold text-ink">{nivel ? NIVEL[nivel].etiqueta : 'No se evalúa'}</dd>
                  </div>
                )
              })}
            </dl>
          ) : (
            <dl className="mt-3 grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg bg-panel-muted px-4 py-3">
                <dt className="text-xs font-medium text-ink-soft">Condición</dt>
                <dd className="mt-1 flex items-baseline gap-2 text-ink">
                  <span className="font-mono text-2xl font-semibold">{operador?.simbolo}</span>
                  <span className="text-sm">{operador?.etiqueta}</span>
                </dd>
              </div>
              <div className="rounded-lg bg-warning-soft px-4 py-3">
                <dt className="flex items-center gap-1.5 text-xs font-medium text-warning">
                  <span className="h-2 w-2 rounded-full bg-warning" aria-hidden="true" /> Advertencia
                </dt>
                <dd className="mt-1 font-mono text-2xl font-semibold tabular-nums text-ink">{formatearLimite(umbral.valorAdvertencia, unidad)}</dd>
              </div>
              <div className="rounded-lg bg-danger-soft px-4 py-3">
                <dt className="flex items-center gap-1.5 text-xs font-medium text-danger">
                  <span className="h-2 w-2 rounded-full bg-danger" aria-hidden="true" /> Crítico
                </dt>
                <dd className="mt-1 font-mono text-2xl font-semibold tabular-nums text-ink">{formatearLimite(umbral.valorCritico, unidad)}</dd>
              </div>
            </dl>
          )}
        </div>
      </section>

      <MensajeError error={catalogos.error ?? grupos.error} onReintentar={() => { catalogos.recargar(); grupos.recargar() }} />
      <SeccionServidores prioridad={1} excepciones={excepciones} columnas={columnas} celdas={celdas} esAdmin={esAdmin} acciones={acciones} />
      <TablaPorCatalogo
        prioridad={2}
        titulo="Por grupo"
        alcance="GRUPO"
        encabezado="Grupo"
        nota="Solo en las órdenes de mantenimiento de ese grupo."
        excepciones={excepciones}
        columnas={columnas}
        celdas={celdas}
        catalogo={(grupos.datos ?? []).map((g) => ({ id: g.id, nombre: g.nombre, activo: g.estado !== 'INACTIVO' }))}
        esAdmin={esAdmin}
        acciones={acciones}
        cargando={!grupos.datos && !grupos.error}
      />
      <TablaPorCatalogo
        prioridad={3}
        titulo="Por entorno"
        alcance="ENTORNO"
        encabezado="Entorno"
        excepciones={excepciones}
        columnas={columnas}
        celdas={celdas}
        catalogo={(catalogos.datos?.entornos ?? []).map((e) => ({ id: e.id, nombre: e.nombre, activo: !!e.activo }))}
        esAdmin={esAdmin}
        acciones={acciones}
        cargando={cargandoCatalogos}
      />
      <TablaPorCatalogo
        prioridad={4}
        titulo="Por criticidad"
        alcance="CRITICIDAD"
        encabezado="Criticidad"
        excepciones={excepciones}
        columnas={columnas}
        celdas={celdas}
        catalogo={(catalogos.datos?.criticidades ?? []).map((c) => ({ id: c.id, nombre: c.nombre, activo: !!c.activo }))}
        esAdmin={esAdmin}
        acciones={acciones}
        cargando={cargandoCatalogos}
      />

      {formulario && (
        <ExcepcionFormulario
          umbral={umbral}
          destino={formulario.destino}
          excepcion={formulario.excepcion}
          onCerrar={() => setFormulario(null)}
          onGuardado={() => {
            setFormulario(null)
            onRecargar()
            avisar('Guardado.')
          }}
        />
      )}

      {confirmar && (
        <Modal
          abierto
          onCerrar={() => setConfirmar(null)}
          titulo={confirmar.tipo === 'umbral' ? 'Eliminar umbral' : 'Quitar excepción'}
          pie={
            <>
              <Boton onClick={() => setConfirmar(null)}>Cancelar</Boton>
              <Boton
                variante="peligro"
                icono={Trash2}
                cargando={trabajando}
                onClick={() =>
                  confirmar.tipo === 'umbral'
                    ? ejecutar(() => umbralesApi.eliminarUmbral(umbral.id!), 'Umbral eliminado.', onEliminado)
                    : ejecutar(() => umbralesApi.eliminarExcepcion(umbral.id!, confirmar.excepcion.id!), 'Excepción quitada.')
                }
              >
                {confirmar.tipo === 'umbral' ? 'Eliminar' : 'Quitar'}
              </Boton>
            </>
          }
        >
          {confirmar.tipo === 'umbral' ? (
            <p className="text-sm text-ink-soft">
              <strong className="text-ink">{umbral.nombre}</strong>
              {excepciones.length > 0 && ` y sus ${excepciones.length} excepciones`} se eliminarán.
            </p>
          ) : (
            <p className="text-sm text-ink-soft">
              Se quitarán los valores de{' '}
              <strong className="text-ink">
                {confirmar.excepcion.alcance ? ETIQUETA_ALCANCE[confirmar.excepcion.alcance] : ''} {confirmar.excepcion.objetivo?.nombre}
              </strong>
              ; se aplicará el siguiente en prioridad.
            </p>
          )}
        </Modal>
      )}
    </div>
  )
}
