import { useState } from 'react'
import type { FormEvent } from 'react'
import { Save } from 'lucide-react'
import { gruposApi } from '@/api/inventario'
import type { FichaGrupoRespuesta } from '@/api/types'
import { Boton } from '@/components/ui/Boton'
import { AreaTexto, Campo, Entrada } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { Modal } from '@/components/ui/Modal'
import { errorDeCampo, tieneErroresDeCampo } from '@/utils/errores'

interface GrupoFormularioProps {
  abierto: boolean
  grupo?: FichaGrupoRespuesta
  onCerrar: () => void
  onGuardado: (grupo: FichaGrupoRespuesta) => void
}

export function GrupoFormulario({ abierto, grupo, onCerrar, onGuardado }: GrupoFormularioProps) {
  const [nombre, setNombre] = useState(grupo?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(grupo?.descripcion ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<unknown>()

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setGuardando(true)
    setError(undefined)
    try {
      const datos = { nombre: nombre.trim(), descripcion: descripcion.trim() || undefined }
      onGuardado(grupo?.id ? await gruposApi.actualizar(grupo.id, datos) : await gruposApi.crear(datos))
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={grupo ? `Editar ${grupo.nombre}` : 'Nuevo grupo de mantenimiento'}
      descripcion={grupo ? undefined : 'Después podrá agregar integrantes con el mismo responsable, entorno y sistema operativo.'}
      pie={
        <>
          <Boton onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" form="form-grupo" variante="primario" icono={Save} cargando={guardando}>
            Guardar
          </Boton>
        </>
      }
    >
      <form id="form-grupo" onSubmit={guardar} className="flex flex-col gap-4">
        {!tieneErroresDeCampo(error) && <MensajeError error={error} />}
        <Campo etiqueta="Nombre" obligatorio error={errorDeCampo(error, 'nombre')}>
          <Entrada value={nombre} onChange={(e) => setNombre(e.target.value)} required autoFocus />
        </Campo>
        <Campo etiqueta="Descripción" error={errorDeCampo(error, 'descripcion')}>
          <AreaTexto value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
        </Campo>
      </form>
    </Modal>
  )
}
