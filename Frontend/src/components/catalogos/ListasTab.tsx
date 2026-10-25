import { useState } from 'react'
import { Pencil, Plus, Power, PowerOff, Trash2 } from 'lucide-react'
import { listasApi } from '@/api/umbrales'
import type { ListaReferenciaRespuesta } from '@/api/types'
import { ListaChips } from '@/components/umbrales/ListaChips'
import { useAvisos } from '@/components/ui/avisos-context'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Cargando } from '@/components/ui/Cargando'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { textoDeError } from '@/utils/errores'
import { ETIQUETA_TIPO_DATO } from '@/utils/umbrales'
import { ListaFormulario } from './ListaFormulario'

/** Listas de referencia: conjuntos esperados o permitidos con los que se comparan las métricas de lista */
export function ListasTab({ esAdmin = false }: { esAdmin?: boolean }) {
  const { avisar } = useAvisos()
  const listas = useConsulta(() => listasApi.listar(), [])
  const [editando, setEditando] = useState<ListaReferenciaRespuesta | 'nueva' | null>(null)
  const [eliminando, setEliminando] = useState<ListaReferenciaRespuesta | null>(null)
  const [trabajando, setTrabajando] = useState(false)

  async function cambiarEstado(l: ListaReferenciaRespuesta) {
    try {
      await listasApi.cambiarEstado(l.id!, !l.activo)
      listas.recargar()
      avisar(l.activo ? 'Lista desactivada.' : 'Lista activada.')
    } catch (e) {
      avisar(textoDeError(e), 'error')
    }
  }

  async function eliminar(l: ListaReferenciaRespuesta) {
    setTrabajando(true)
    try {
      await listasApi.eliminar(l.id!)
      listas.recargar()
      avisar('Lista eliminada.')
    } catch (e) {
      avisar(textoDeError(e), 'error')
    } finally {
      setTrabajando(false)
      setEliminando(null)
    }
  }

  const usos = (l: ListaReferenciaRespuesta) => (l.umbrales ?? 0) + (l.excepciones ?? 0)

  return (
    <div className="flex flex-col gap-4">
      {esAdmin && <div className="flex justify-end">
        <Boton variante="primario" icono={Plus} onClick={() => setEditando('nueva')}>
          Nueva lista
        </Boton>
      </div>}
      <MensajeError error={listas.error} onReintentar={listas.recargar} />
      {listas.cargando && !listas.datos ? (
        <Cargando />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-line bg-panel">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead>
              <tr className="border-b border-line text-xs uppercase tracking-wide text-ink-faint">
                <th className="px-4 py-3 font-medium">Lista</th>
                <th className="px-4 py-3 font-medium">Tipo</th>
                <th className="px-4 py-3 font-medium">Elementos</th>
                <th className="px-4 py-3 font-medium">Usos</th>
                <th className="px-4 py-3 font-medium">Estado</th>
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {listas.datos?.map((l) => (
                <tr key={l.id} className="hover:bg-panel-muted">
                  <td className="px-4 py-3 font-medium text-ink">{l.nombre}</td>
                  <td className="px-4 py-3 text-ink-soft">
                    {l.tipoDato ? ETIQUETA_TIPO_DATO[l.tipoDato] : ''}
                    {l.ignoraMayusculas && <span className="block text-xs text-ink-faint">Sin mayúsculas</span>}
                  </td>
                  <td className="px-4 py-3">
                    <ListaChips elementos={l.elementos} atenuado={!l.activo} />
                  </td>
                  <td
                    className="px-4 py-3 tabular-nums text-ink-soft"
                    title={`${l.umbrales ?? 0} umbral(es) y ${l.excepciones ?? 0} excepción(es)`}
                  >
                    {usos(l)}
                  </td>
                  <td className="px-4 py-3">
                    <StatusPill tone={l.activo ? 'success' : 'neutral'} label={l.activo ? 'Activa' : 'Inactiva'} />
                  </td>
                  <td className="px-4 py-3">
                    {esAdmin && <div className="flex justify-end gap-1">
                      <BotonIcono icono={Pencil} etiqueta={`Editar ${l.nombre}`} onClick={() => setEditando(l)} />
                      <BotonIcono
                        icono={l.activo ? PowerOff : Power}
                        etiqueta={l.activo ? 'Desactivar' : 'Activar'}
                        onClick={() => cambiarEstado(l)}
                      />
                      <BotonIcono icono={Trash2} etiqueta={`Eliminar ${l.nombre}`} onClick={() => setEliminando(l)} />
                    </div>}
                  </td>
                </tr>
              ))}
              {listas.datos?.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-ink-soft">
                    Sin listas
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {editando && (
        <ListaFormulario
          lista={editando === 'nueva' ? undefined : editando}
          onCerrar={() => setEditando(null)}
          onGuardado={() => {
            setEditando(null)
            listas.recargar()
            avisar('Lista guardada.')
          }}
        />
      )}
      {eliminando && (
        <Modal
          abierto
          onCerrar={() => setEliminando(null)}
          titulo="Eliminar lista"
          pie={
            <>
              <Boton onClick={() => setEliminando(null)}>Cancelar</Boton>
              <Boton variante="peligro" icono={Trash2} cargando={trabajando} onClick={() => eliminar(eliminando)}>
                Eliminar
              </Boton>
            </>
          }
        >
          <p className="text-sm text-ink-soft">
            <strong className="text-ink">{eliminando.nombre}</strong> se eliminará.
            {usos(eliminando) > 0 && ' Está en uso: desactívela o cambie antes la lista de los umbrales que la usan.'}
          </p>
        </Modal>
      )}
    </div>
  )
}
