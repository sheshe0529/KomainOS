import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Search } from 'lucide-react'
import { automatizacionApi, type Problema } from '@/api/automatizacion'
import { PageHeader } from '@/components/common/PageHeader'
import { TablaAutomatizacion, celda } from '@/components/automatizacion/TablaAutomatizacion'
import { Boton } from '@/components/ui/Boton'
import { Campo, AreaTexto } from '@/components/ui/Campo'
import { Modal } from '@/components/ui/Modal'
import { MensajeError } from '@/components/ui/MensajeError'
import { Cargando } from '@/components/ui/Cargando'
import { StatusPill } from '@/components/ui/StatusPill'
import { useConsulta } from '@/hooks/useConsulta'
import { useSesion } from '@/auth/sesion-context'
import { formatearFechaHora } from '@/utils/formato'
const ORIGEN: Record<NonNullable<Problema['origen']>, string> = {
  PRUEBA_PILOTO: 'Prueba piloto',
  FALLO_EJECUCION: 'Ejecución',
  TIEMPO_EXCEDIDO: 'Tiempo de ejecución',
  VALIDACION_TECNICA_NO_CONFORME: 'Validación técnica',
  RECHAZO_FUNCIONAL: 'Validación funcional',
}
const ESTADO = {
  ABIERTA: { nombre: 'Abierta', tono: 'danger' },
  EN_ATENCION: { nombre: 'En atención', tono: 'warning' },
  CERRADA: { nombre: 'Cerrada', tono: 'success' },
} as const
function codigo(id?: number) {
  return `INC-${String(id ?? 0).padStart(4, '0')}`
}
function DetalleIncidencia({
  id,
  atender,
  onCerrar,
  onGuardado,
}: {
  id: number
  atender: boolean
  onCerrar: () => void
  onGuardado: () => void
}) {
  const { tieneRol } = useSesion()
  const ficha = useConsulta(() => automatizacionApi.incidencia(id), [id])
  const [editando, setEditando] = useState(atender)
  const [f, setF] = useState({ diagnostico: '', acciones: '', resultado: '', cerrar: false })
  const [error, setError] = useState<unknown>()
  const [guardando, setGuardando] = useState(false)
  const i = ficha.datos
  useEffect(() => {
    if (ficha.datos)
      setF({
        diagnostico: ficha.datos.diagnostico ?? '',
        acciones: ficha.datos.acciones ?? '',
        resultado: ficha.datos.resultado ?? '',
        cerrar: false,
      })
  }, [ficha.datos])
  async function guardar() {
    setGuardando(true)
    setError(undefined)
    try {
      await automatizacionApi.atender(id, f)
      onGuardado()
      onCerrar()
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }
  const editable = tieneRol('ADMINISTRADOR') && i && i.estado !== 'CERRADA'
  return (
    <Modal
      abierto
      ancho="lg"
      titulo={`${editando && editable ? 'Atender incidencia' : 'Detalle de incidencia'} · ${codigo(id)}`}
      onCerrar={() => !guardando && onCerrar()}
      pie={
        <>
          <Boton disabled={guardando} onClick={onCerrar}>
            Cerrar
          </Boton>
          {editable &&
            (editando ? (
              <Boton
                variante="primario"
                cargando={guardando}
                disabled={!f.diagnostico.trim() || !f.acciones.trim() || !f.resultado.trim()}
                onClick={guardar}
              >
                {f.cerrar ? 'Cerrar incidencia' : 'Guardar atención'}
              </Boton>
            ) : (
              <Boton variante="primario" onClick={() => setEditando(true)}>
                Atender incidencia
              </Boton>
            ))}
        </>
      }
    >
      <MensajeError error={error ?? ficha.error} onReintentar={ficha.recargar} />
      {ficha.cargando && !i && <Cargando texto="Cargando incidencia…" />}
      {i && (
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center gap-3">
            {i.estado && <StatusPill tone={ESTADO[i.estado].tono} label={ESTADO[i.estado].nombre} />}
            <span className="text-sm text-ink-soft">{i.origen && ORIGEN[i.origen]}</span>
          </div>
          <dl className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-xs text-ink-faint">Servidor</dt>
              <dd>
                <Link className="font-mono text-accent hover:underline" to={`/servidores/${i.idServidor}`}>
                  {i.servidor}
                </Link>
              </dd>
            </div>
            <div>
              <dt className="text-xs text-ink-faint">Orden</dt>
              <dd>
                <Link className="font-mono text-accent hover:underline" to={`/ordenes/${i.idOrden}`}>
                  {i.orden}
                </Link>
              </dd>
            </div>
            <div>
              <dt className="text-xs text-ink-faint">Detectada</dt>
              <dd>{formatearFechaHora(i.fechaApertura)}</dd>
            </div>
            {i.fechaCierre && (
              <div>
                <dt className="text-xs text-ink-faint">Cierre</dt>
                <dd>{formatearFechaHora(i.fechaCierre)}</dd>
              </div>
            )}
          </dl>
          <section>
            <h4 className="mb-1 text-sm font-medium">Error registrado</h4>
            <p className="whitespace-pre-wrap break-words rounded-lg bg-panel-muted p-3 text-sm leading-relaxed">
              {i.error || 'Sin error adicional.'}
            </p>
          </section>
          {editando && editable ? (
            <>
              {(['diagnostico', 'acciones', 'resultado'] as const).map((campo, n) => (
                <Campo key={campo} etiqueta={['Diagnóstico', 'Acciones realizadas', 'Resultado'][n]} obligatorio>
                  <AreaTexto
                    value={f[campo]}
                    maxLength={campo === 'resultado' ? 1000 : 10000}
                    onChange={(e) => setF({ ...f, [campo]: e.target.value })}
                  />
                </Campo>
              ))}
              <label className="flex items-start gap-2 text-sm">
                <input
                  className="mt-1"
                  type="checkbox"
                  checked={f.cerrar}
                  onChange={(e) => setF({ ...f, cerrar: e.target.checked })}
                />
                Cerrar la incidencia. La orden se cerrará al terminar el trabajo en curso y atender las incidencias
                restantes.
              </label>
            </>
          ) : (
            <>
              {[
                ['Diagnóstico', i.diagnostico],
                ['Acciones realizadas', i.acciones],
                ['Resultado', i.resultado],
              ].map(([titulo, valor]) => (
                <section key={titulo}>
                  <h4 className="mb-1 text-sm font-medium">{titulo}</h4>
                  <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-ink-soft">
                    {valor || 'Sin registrar.'}
                  </p>
                </section>
              ))}
            </>
          )}
        </div>
      )}
    </Modal>
  )
}
export function IncidenciasPage() {
  const { tieneRol } = useSesion()
  const [pagina, setPagina] = useState(0)
  const [estado, setEstado] = useState('')
  const [texto, setTexto] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [params, setParams] = useSearchParams()
  const id = Number(params.get('incidencia'))
  const [atender, setAtender] = useState(false)
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setBusqueda(texto)
      setPagina(0)
    }, 300)
    return () => window.clearTimeout(timer)
  }, [texto])
  const lista = useConsulta(() => automatizacionApi.incidencias(pagina, estado, busqueda), [pagina, estado, busqueda])
  function abrir(i: Problema, editar = false) {
    setAtender(editar)
    setParams({ incidencia: String(i.id) })
  }
  return (
    <>
      <PageHeader
        title="Incidencias"
        description="Consulte los errores del mantenimiento, registre su atención y revise el historial de cada caso."
      />
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <label className="flex w-full max-w-xs items-center gap-2 rounded-full border border-line bg-panel px-3 py-2 text-sm">
          <Search className="h-4 w-4 shrink-0 text-ink-faint" />
          <span className="sr-only">Buscar por servidor u orden</span>
          <input
            className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-ink-faint"
            placeholder="Buscar por servidor u orden"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
          />
        </label>
        <div className="flex flex-wrap gap-2" role="group" aria-label="Filtrar incidencias por estado">
          {[
            ['', 'Todas'],
            ['ABIERTA', 'Abierta'],
            ['EN_ATENCION', 'En atención'],
            ['CERRADA', 'Cerrada'],
          ].map(([valor, nombre]) => (
            <button
              key={valor}
              type="button"
              aria-pressed={estado === valor}
              onClick={() => {
                setEstado(valor)
                setPagina(0)
              }}
              className={`rounded-full px-4 py-2 text-xs font-medium ${estado === valor ? 'bg-accent text-accent-ink' : 'bg-accent-soft text-ink-soft hover:text-ink'}`}
            >
              {nombre}
            </button>
          ))}
        </div>
      </div>
      <MensajeError error={lista.error} onReintentar={lista.recargar} />
      {lista.cargando && !lista.datos && <Cargando texto="Cargando incidencias…" />}
      <TablaAutomatizacion
        pagina={lista.datos}
        onPagina={setPagina}
        cabeceras={['Incidencia', 'Servidor', 'Orden', 'Origen', 'Estado', 'Detectada', 'Acciones']}
        fila={(i) => (
          <tr key={i.id} className="hover:bg-panel-muted/50">
            <td className={`${celda} font-mono`}>
              <button type="button" className="text-accent hover:underline" onClick={() => abrir(i)}>
                {codigo(i.id)}
              </button>
            </td>
            <td className={`${celda} font-mono`}>{i.servidor}</td>
            <td className={`${celda} font-mono`}>
              <Link className="text-accent hover:underline" to={`/ordenes/${i.idOrden}`}>
                {i.orden}
              </Link>
            </td>
            <td className={celda}>{i.origen && ORIGEN[i.origen]}</td>
            <td className={celda}>
              {i.estado && <StatusPill tone={ESTADO[i.estado].tono} label={ESTADO[i.estado].nombre} />}
            </td>
            <td className={`${celda} whitespace-nowrap text-xs text-ink-soft`}>
              {formatearFechaHora(i.fechaApertura)}
            </td>
            <td className={celda}>
              <div className="flex gap-2">
                <Boton onClick={() => abrir(i)}>Ver detalle</Boton>
                {tieneRol('ADMINISTRADOR') && i.estado !== 'CERRADA' && (
                  <Boton onClick={() => abrir(i, true)}>Atender</Boton>
                )}
              </div>
            </td>
          </tr>
        )}
      />
      {id > 0 && Number.isInteger(id) && (
        <DetalleIncidencia
          key={id}
          id={id}
          atender={atender}
          onCerrar={() => setParams({})}
          onGuardado={lista.recargar}
        />
      )}
    </>
  )
}
