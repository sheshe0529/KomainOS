import { useState } from 'react'
import type { FormEvent } from 'react'
import { Plus, Power, PowerOff, Save } from 'lucide-react'
import { catalogosApi } from '@/api/catalogos'
import type { FamiliaSistemaOperativo } from '@/api/dominio'
import type { SistemaOperativoRespuesta } from '@/api/types'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Campo, Entrada, Selector } from '@/components/ui/Campo'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { ETIQUETA_FAMILIA } from '@/utils/etiquetas'
import { errorDeCampo, textoDeError, tieneErroresDeCampo } from '@/utils/errores'

type Dialogo = { tipo: 'so' } | { tipo: 'version'; sistema: SistemaOperativoRespuesta } | null

/** Catálogo de sistemas operativos y versiones (dependencia de RF10). */
export function SistemasOperativosTab() {
  const { avisar } = useAvisos()
  const sistemas = useConsulta(() => catalogosApi.sistemasOperativos(), [])
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  const [nombre, setNombre] = useState('')
  const [familia, setFamilia] = useState<FamiliaSistemaOperativo>('LINUX')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  function abrir(d: Dialogo) {
    setError(undefined)
    setNombre('')
    setFamilia('LINUX')
    setDialogo(d)
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    if (!dialogo) return
    setGuardando(true)
    setError(undefined)
    try {
      if (dialogo.tipo === 'so') await catalogosApi.crearSistemaOperativo({ nombre: nombre.trim(), familia })
      else await catalogosApi.agregarVersion(dialogo.sistema.id!, { version: nombre.trim() })
      setDialogo(null)
      sistemas.recargar()
      avisar(dialogo.tipo === 'so' ? 'Sistema operativo registrado.' : 'Versión agregada.')
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  async function ejecutar(accion: () => Promise<unknown>, mensaje: string) {
    try {
      await accion()
      sistemas.recargar()
      avisar(mensaje)
    } catch (e) {
      avisar(textoDeError(e), 'error')
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Boton variante="primario" icono={Plus} onClick={() => abrir({ tipo: 'so' })}>
          Nuevo sistema operativo
        </Boton>
      </div>
      <MensajeError error={sistemas.error} onReintentar={sistemas.recargar} />
      {sistemas.cargando && !sistemas.datos ? (
        <Cargando />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {sistemas.datos?.map((so) => (
            <section key={so.id} className="rounded-xl border border-line bg-panel p-4">
              <div className="mb-3 flex items-start justify-between gap-2">
                <div>
                  <h4 className="font-semibold text-ink">{so.nombre}</h4>
                  <p className="text-xs text-ink-faint">{so.familia ? ETIQUETA_FAMILIA[so.familia] : ''}</p>
                </div>
                <div className="flex items-center gap-1">
                  <StatusPill tone={so.activo ? 'success' : 'neutral'} label={so.activo ? 'Activo' : 'Inactivo'} />
                  <BotonIcono
                    icono={so.activo ? PowerOff : Power}
                    etiqueta={so.activo ? 'Desactivar' : 'Activar'}
                    onClick={() =>
                      ejecutar(
                        () => catalogosApi.actualizarSistemaOperativo(so.id!, { nombre: so.nombre!, familia: so.familia!, activo: !so.activo }),
                        'Estado actualizado.',
                      )
                    }
                  />
                </div>
              </div>
              <ul className="flex flex-wrap gap-2">
                {so.versiones?.map((v) => (
                  <li key={v.id}>
                    <button
                      type="button"
                      title={v.activo ? 'Desactivar versión' : 'Activar versión'}
                      onClick={() =>
                        ejecutar(() => catalogosApi.actualizarVersion(v.id!, { version: v.version!, activo: !v.activo }), 'Versión actualizada.')
                      }
                      className={`rounded-full px-2.5 py-1 font-mono text-xs ${
                        v.activo ? 'bg-accent-soft text-accent' : 'bg-panel-muted text-ink-faint line-through'
                      }`}
                    >
                      {v.version}
                    </button>
                  </li>
                ))}
                <li>
                  <button
                    type="button"
                    onClick={() => abrir({ tipo: 'version', sistema: so })}
                    className="inline-flex items-center gap-1 rounded-full border border-dashed border-line px-2.5 py-1 text-xs text-ink-soft hover:text-ink"
                  >
                    <Plus className="h-3 w-3" aria-hidden="true" /> Versión
                  </button>
                </li>
              </ul>
            </section>
          ))}
          {sistemas.datos?.length === 0 && <p className="text-sm text-ink-soft">No hay sistemas operativos registrados.</p>}
        </div>
      )}

      {dialogo && (
        <Modal
          abierto
          onCerrar={() => setDialogo(null)}
          titulo={dialogo.tipo === 'so' ? 'Nuevo sistema operativo' : `Nueva versión de ${dialogo.sistema.nombre}`}
          pie={
            <>
              <Boton onClick={() => setDialogo(null)}>Cancelar</Boton>
              <Boton type="submit" form="form-so" variante="primario" icono={Save} cargando={guardando}>
                Guardar
              </Boton>
            </>
          }
        >
          <form id="form-so" onSubmit={guardar} className="flex flex-col gap-4">
            {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
            <Campo
              etiqueta={dialogo.tipo === 'so' ? 'Nombre' : 'Versión'}
              obligatorio
              error={errorDeCampo(error, dialogo.tipo === 'so' ? 'nombre' : 'version')}
            >
              <Entrada value={nombre} onChange={(e) => setNombre(e.target.value)} required autoFocus />
            </Campo>
            {dialogo.tipo === 'so' && (
              <Campo etiqueta="Familia" obligatorio ayuda="Determina el canal remoto de ejecución sin agentes.">
                <Selector value={familia} onChange={(e) => setFamilia(e.target.value as FamiliaSistemaOperativo)}>
                  <option value="LINUX">{ETIQUETA_FAMILIA.LINUX}</option>
                  <option value="WINDOWS">{ETIQUETA_FAMILIA.WINDOWS}</option>
                </Selector>
              </Campo>
            )}
          </form>
        </Modal>
      )}
    </div>
  )
}
