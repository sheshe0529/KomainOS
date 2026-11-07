import { useState } from 'react'
import { Bell, BellOff, Pencil, Save, X } from 'lucide-react'
import { politicasApi } from '@/api/automatizacion'
import type { PoliticaCambioPeticion, PoliticaCambioRespuesta } from '@/api/types'
import { useSesion } from '@/auth/sesion-context'
import { PageHeader } from '@/components/common/PageHeader'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton } from '@/components/ui/Boton'
import { Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { Info } from '@/components/ui/Info'
import { MensajeError } from '@/components/ui/MensajeError'
import { useCatalogos } from '@/hooks/useCatalogos'
import { useConsulta } from '@/hooks/useConsulta'
import { textoDeError } from '@/utils/errores'

type Afectacion = PoliticaCambioPeticion['nivelAfectacion']
type Destinatario = PoliticaCambioPeticion['notificaciones'][number]
type Modalidad = 'AUTOMATICA' | 'RESPONSABLE' | 'ADMINISTRADOR' | 'AMBOS' | 'DESACTIVADA'

const COLUMNAS: { id: Afectacion; nombre: string }[] = [
  { id: 'SIN_AFECTACION', nombre: 'Sin afectación' },
  { id: 'PARCIAL', nombre: 'Parcial' },
  { id: 'TOTAL', nombre: 'Total' },
]
const ROLES: { id: Destinatario; nombre: string }[] = [
  { id: 'RESPONSABLE', nombre: 'Responsable' },
  { id: 'ADMINISTRADOR', nombre: 'Administrador' },
  { id: 'OPERADOR', nombre: 'Operador' },
]
const MODALIDADES: { id: Modalidad; nombre: string; detalle: string }[] = [
  { id: 'AUTOMATICA', nombre: 'Automática', detalle: 'Se autoriza sin aprobaciones' },
  { id: 'RESPONSABLE', nombre: 'Responsable', detalle: 'Aprueba el responsable del servidor' },
  { id: 'ADMINISTRADOR', nombre: 'Administrador', detalle: 'Aprueba un administrador' },
  { id: 'AMBOS', nombre: 'Responsable y administrador', detalle: 'Primero aprueba el responsable del servidor y después un administrador. Ambos comparten el plazo.' },
  { id: 'DESACTIVADA', nombre: 'Desactivada', detalle: 'La orden no se evalúa en este entorno' },
]

/** Lo que se edita de cada celda: quién aprueba y a quién se notifica */
interface Celda {
  modalidad: Modalidad | ''
  notificaciones: Destinatario[]
}

const clave = (idEntorno: number, afectacion: Afectacion) => `${idEntorno}:${afectacion}`

function modalidad(r?: PoliticaCambioRespuesta): Modalidad | '' {
  if (!r) return ''
  if (!r.activo) return 'DESACTIVADA'
  if (r.requiereAdministrador && r.requiereResponsable) return 'AMBOS'
  return r.requiereResponsable ? 'RESPONSABLE' : r.requiereAdministrador ? 'ADMINISTRADOR' : 'AUTOMATICA'
}

function celdaDe(r?: PoliticaCambioRespuesta): Celda {
  return { modalidad: modalidad(r), notificaciones: [...(r?.notificaciones ?? [])] }
}

function iguales(a: Celda, b: Celda): boolean {
  return a.modalidad === b.modalidad && [...a.notificaciones].sort().join() === [...b.notificaciones].sort().join()
}

/** Vista de una celda: quién aprueba y a quién se avisa, sin controles */
function CeldaLectura({ celda }: { celda: Celda }) {
  const m = MODALIDADES.find((x) => x.id === celda.modalidad)
  const notifica = celda.notificaciones.length > 0
  return (
    <div className="flex flex-col gap-2">
      {m ? (
        <p className={`text-sm font-medium ${m.id === 'DESACTIVADA' ? 'text-ink-faint' : 'text-ink'}`}>{m.nombre}</p>
      ) : (
        <p className="text-sm text-warning">Sin configurar</p>
      )}
      <p className={`flex items-center gap-1.5 text-xs ${notifica ? 'text-ink-soft' : 'text-ink-faint'}`}>
        {notifica ? <Bell className="h-3.5 w-3.5" aria-hidden="true" /> : <BellOff className="h-3.5 w-3.5" aria-hidden="true" />}
        {notifica
          ? `Notifica a ${celda.notificaciones
              .map((d) => ROLES.find((r) => r.id === d)?.nombre.toLowerCase())
              .join(', ')}`
          : 'Sin notificación'}
      </p>
    </div>
  )
}

interface CeldaEdicionProps {
  celda: Celda
  nombre: string
  /** Solo una regla que ya existe se puede desactivar */
  existe: boolean
  onCambiar: (c: Celda) => void
}

function CeldaEdicion({ celda, nombre, existe, onCambiar }: CeldaEdicionProps) {
  const activa = celda.modalidad !== '' && celda.modalidad !== 'DESACTIVADA'
  const elegida = MODALIDADES.find((m) => m.id === celda.modalidad)
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <Selector
          aria-label={`Autorización · ${nombre}`}
          className="min-w-0 flex-1"
          value={celda.modalidad}
          onChange={(e) => onCambiar({ ...celda, modalidad: e.target.value as Modalidad })}
        >
          <option value="" disabled>
            Seleccione quién autoriza…
          </option>
          {MODALIDADES.filter((m) => existe || m.id !== 'DESACTIVADA').map((m) => (
            <option key={m.id} value={m.id}>
              {m.nombre}
            </option>
          ))}
        </Selector>
        <Info etiqueta={`Ayuda de autorización · ${nombre}`}>
          <p>{elegida?.detalle ?? 'Seleccione una modalidad para consultar quién debe aprobar el mantenimiento.'}</p>
          <p className="mt-2 text-xs">
            La afectación de la MOP es la mayor de sus tareas. Los límites de duración y concurrencia se consultan en Configuración › Parámetros.
          </p>
        </Info>
      </div>
      <fieldset disabled={!activa} className="flex flex-col gap-1.5 disabled:opacity-50">
        <legend className="mb-1 text-xs text-ink-soft">Notificar a</legend>
        <div className="flex flex-wrap gap-x-3 gap-y-1.5">
          {ROLES.map((r) => (
            <label key={r.id} className="flex items-center gap-1.5 text-xs text-ink">
              <input
                type="checkbox"
                checked={celda.notificaciones.includes(r.id)}
                onChange={(e) =>
                  onCambiar({
                    ...celda,
                    notificaciones: e.target.checked
                      ? [...celda.notificaciones, r.id]
                      : celda.notificaciones.filter((x) => x !== r.id),
                  })
                }
                className="h-3.5 w-3.5 accent-[var(--color-accent)]"
              />
              {r.nombre}
            </label>
          ))}
        </div>
      </fieldset>
    </div>
  )
}

/**
 * Política de gestión del cambio (RF43, HU24): quién autoriza según el entorno y la afectación. Se consulta de
 * entrada; el administrador la modifica con «Editar política» y guarda todos los cambios juntos
 */
export function PoliticasCambioPage() {
  const { tieneRol } = useSesion()
  const esAdmin = tieneRol('ADMINISTRADOR')
  const { avisar } = useAvisos()
  const lista = useConsulta(() => politicasApi.listar(), [])
  const catalogos = useCatalogos()
  const [borrador, setBorrador] = useState<Map<string, Celda>>()
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  const reglas = lista.datos ?? []
  const entornos = catalogos.datos?.entornos.filter((e) => e.activo || reglas.some((r) => r.idEntorno === e.id)) ?? []
  const regla = (idEntorno: number, afectacion: Afectacion) =>
    reglas.find((r) => r.idEntorno === idEntorno && r.nivelAfectacion === afectacion)
  const editando = !!borrador

  const cambios = borrador
    ? [...borrador.entries()].filter(([k, c]) => {
        const [id, afectacion] = k.split(':')
        return c.modalidad !== '' && !iguales(c, celdaDe(regla(Number(id), afectacion as Afectacion)))
      })
    : []

  function editar() {
    const inicial = new Map<string, Celda>()
    for (const e of entornos) {
      if (!e.activo) continue
      for (const c of COLUMNAS) inicial.set(clave(e.id!, c.id), celdaDe(regla(e.id!, c.id)))
    }
    setError(undefined)
    setBorrador(inicial)
  }

  async function guardar() {
    setGuardando(true)
    setError(undefined)
    let guardadas = 0
    try {
      // Una celda por solicitud, en orden: si una falla, las anteriores ya quedaron y se informa cuál
      for (const [k, c] of cambios) {
        const [id, afectacion] = k.split(':')
        const actual = regla(Number(id), afectacion as Afectacion)
        const desactivada = c.modalidad === 'DESACTIVADA'
        await politicasApi.guardar(actual?.id, {
          idEntorno: Number(id),
          nivelAfectacion: afectacion as Afectacion,
          activo: !desactivada,
          requiereResponsable: desactivada ? !!actual?.requiereResponsable : c.modalidad === 'RESPONSABLE' || c.modalidad === 'AMBOS',
          requiereAdministrador: desactivada
            ? !!actual?.requiereAdministrador
            : c.modalidad === 'ADMINISTRADOR' || c.modalidad === 'AMBOS',
          notificaciones: desactivada ? (actual?.notificaciones ?? []) : c.notificaciones,
          versionEsperada: actual?.fechaActualizacion,
        })
        guardadas++
      }
      avisar(guardadas === 1 ? 'Política de cambio actualizada: 1 regla.' : `Política de cambio actualizada: ${guardadas} reglas.`)
      setBorrador(undefined)
      lista.recargar()
    } catch (e) {
      setError(guardadas > 0 ? new Error(`Se guardaron ${guardadas} reglas; la siguiente falló: ${textoDeError(e)}`) : e)
      lista.recargar()
    } finally {
      setGuardando(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Políticas de cambio"
        description="Quién autoriza cada mantenimiento según el entorno del servidor y la afectación de sus tareas."
        actions={
          esAdmin &&
          (editando ? (
            <>
              <Boton icono={X} disabled={guardando} onClick={() => setBorrador(undefined)}>
                Cancelar
              </Boton>
              <Boton variante="primario" icono={Save} cargando={guardando} disabled={cambios.length === 0} onClick={guardar}>
                Guardar cambios{cambios.length > 0 ? ` (${cambios.length})` : ''}
              </Boton>
            </>
          ) : (
            <Boton icono={Pencil} disabled={!lista.datos || !catalogos.datos} onClick={editar}>
              Editar política
            </Boton>
          ))
        }
      />
      <MensajeError error={error ?? lista.error ?? catalogos.error} onReintentar={error ? undefined : lista.recargar} />

      {editando && (
        <p className="mb-4 rounded-lg bg-accent-soft px-4 py-3 text-sm text-ink">
          Está editando la política. Los cambios se aplican a las autorizaciones que se soliciten después de guardar.
        </p>
      )}

      {(lista.cargando || catalogos.cargando) && !lista.datos ? (
        <Cargando texto="Cargando matriz de políticas…" />
      ) : entornos.length === 0 ? (
        <p className="rounded-xl border border-line bg-panel p-6 text-sm text-ink-soft">
          Cree un entorno en Catálogos para configurar sus políticas de cambio.
        </p>
      ) : (
        <div className="-mx-2 overflow-x-auto pb-2">
          <table
            className="w-full min-w-[900px] table-fixed border-separate border-spacing-x-2 border-spacing-y-3 text-left text-sm"
            aria-label="Matriz de políticas de cambio"
          >
            <colgroup>
              <col className="w-36" />
              {COLUMNAS.map((c) => (
                <col key={c.id} />
              ))}
            </colgroup>
            <thead>
              <tr>
                <th>
                  <span className="sr-only">Entorno</span>
                </th>
                {COLUMNAS.map((c) => (
                  <th key={c.id} scope="col" className="px-3 text-xs font-medium text-ink-faint">
                    Afectación {c.nombre.toLowerCase()}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {entornos.map((e) => (
                <tr key={e.id}>
                  <th scope="row" className="px-2 align-top font-medium">
                    {e.nombre}
                    {!e.activo && <span className="mt-1 block text-xs font-normal text-ink-faint">Entorno inactivo</span>}
                  </th>
                  {COLUMNAS.map((c) => {
                    const k = clave(e.id!, c.id)
                    const enEdicion = borrador?.get(k)
                    const modificada = enEdicion && !iguales(enEdicion, celdaDe(regla(e.id!, c.id)))
                    return (
                      <td
                        key={c.id}
                        className={`rounded-2xl border bg-panel p-3 align-top shadow-sm shadow-black/5 ${
                          modificada ? 'border-accent' : 'border-line'
                        }`}
                      >
                        {enEdicion ? (
                          <CeldaEdicion
                            celda={enEdicion}
                            nombre={`${e.nombre} · ${c.nombre}`}
                            existe={!!regla(e.id!, c.id)}
                            onCambiar={(nueva) => setBorrador((b) => new Map(b).set(k, nueva))}
                          />
                        ) : (
                          <CeldaLectura celda={celdaDe(regla(e.id!, c.id))} />
                        )}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
