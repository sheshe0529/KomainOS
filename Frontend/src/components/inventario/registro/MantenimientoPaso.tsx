import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { cuentasServicioApi } from '@/api/credenciales'
import { DIAS_SEMANA, type DiaSemana } from '@/api/dominio'
import { SelectorCuentaServicio } from '@/components/credenciales/SelectorCuentaServicio'
import { Boton, BotonIcono } from '@/components/ui/Boton'
import { Campo, Entrada, Selector } from '@/components/ui/Campo'
import { MensajeError } from '@/components/ui/MensajeError'
import { SelectorHora } from '@/components/ui/SelectorHora'
import { useConsulta } from '@/hooks/useConsulta'
import { ETIQUETA_DIA } from '@/utils/etiquetas'
import { FINALIDADES_CUENTA } from '@/utils/cuentasServicio'
import { RevisionPeriodicaCampo } from '../RevisionPeriodicaCampo'
import { VistaSemanalVentanas } from '../VistaSemanalVentanas'
import { problemaMantenimiento, type ServidorPreparado } from './preparacion'

interface Props {
  servidores: ServidorPreparado[]
  onCambiar: (servidores: ServidorPreparado[]) => void
}

export function MantenimientoPaso({ servidores, onCambiar }: Props) {
  const [clave, setClave] = useState(servidores[0]?.clave)
  const cuentas = useConsulta(() => cuentasServicioApi.listar(), [])
  const [aviso, setAviso] = useState('')
  const s = servidores.find((item) => item.clave === clave) ?? servidores[0]
  if (!s) return null
  const { configuracion: c, ventanas } = s.mantenimiento
  const cambiar = (mantenimiento: ServidorPreparado['mantenimiento']) => {
    setAviso('')
    onCambiar(servidores.map((item) => item.clave === s.clave ? { ...item, mantenimiento } : item))
  }
  const configurar = (cambios: Partial<typeof c>) => cambiar({ configuracion: { ...c, ...cambios }, ventanas })
  const cambiarVentana = (i: number, cambios: Partial<(typeof ventanas)[number]>) => cambiar({ configuracion: c, ventanas: ventanas.map((v, j) => i === j ? { ...v, ...cambios } : v) })
  const problema = problemaMantenimiento(s)

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h4 className="font-semibold">Configuración de mantenimiento</h4>
        <p className="mt-1 text-sm text-ink-soft">Revise cada servidor. Las frecuencias iniciales provienen de su criticidad; los servidores existentes conservan sus valores actuales.</p>
      </div>
      {servidores.length > 1 && <Campo etiqueta={`Servidor a configurar (${servidores.length})`}>
        <Selector value={s.clave} onChange={(e) => setClave(Number(e.target.value))}>
          {servidores.map((item) => <option key={item.clave} value={item.clave}>{item.hostname} · {problemaMantenimiento(item) ? 'Por completar' : 'Configurado'}</option>)}
        </Selector>
      </Campo>}
      <div className="rounded-lg bg-panel-muted p-3 text-sm"><span className="font-mono font-medium">{s.hostname}</span><span className="ml-3 text-ink-soft">{s.criticidad} · {s.sistemaOperativo}</span></div>
      <RevisionPeriodicaCampo
        habilitada={c.revisionPeriodicaHabilitada ?? true}
        onCambiar={(revisionPeriodicaHabilitada) => configurar({ revisionPeriodicaHabilitada })}
      >
        <Campo etiqueta="Frecuencia de revisión (días)" obligatorio className="sm:max-w-xs"><Entrada type="number" min={1} step={1} value={c.frecuenciaRevisionDias ?? ''} onChange={(e) => configurar({ frecuenciaRevisionDias: e.target.value ? Number(e.target.value) : undefined })} /></Campo>
      </RevisionPeriodicaCampo>
      <Campo etiqueta="Frecuencia de mantenimiento (días)" obligatorio className="sm:max-w-xs"><Entrada type="number" min={1} step={1} value={c.frecuenciaMantenimientoDias ?? ''} onChange={(e) => configurar({ frecuenciaMantenimientoDias: e.target.value ? Number(e.target.value) : undefined })} /></Campo>
      {FINALIDADES_CUENTA.map(({ finalidad, campoId }) => (
        <SelectorCuentaServicio
          key={finalidad}
          finalidad={finalidad}
          cuentas={cuentas.datos ?? []}
          valor={c[campoId]}
          onCambiar={(id) => configurar({ [campoId]: id })}
          familia={s.familia}
          deshabilitado={cuentas.cargando}
        />
      ))}
      <MensajeError error={cuentas.error} onReintentar={cuentas.recargar} />
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-2 text-sm font-semibold">Ventana permisiva</legend>
        <p className="text-xs text-ink-soft">Defina cuándo se permite el mantenimiento. Para cruzar medianoche, elija el día siguiente como día de fin.</p>
        {ventanas.map((v, i) => <div key={i} className="grid gap-2 rounded-lg border border-line p-3 sm:grid-cols-[1fr_1fr_auto]">
          <div className="grid grid-cols-2 gap-2"><Campo etiqueta="Día de inicio"><Selector value={v.diaInicio} onChange={(e) => cambiarVentana(i, { diaInicio: e.target.value as DiaSemana })}>{DIAS_SEMANA.map((d) => <option key={d} value={d}>{ETIQUETA_DIA[d]}</option>)}</Selector></Campo><Campo etiqueta="Hora de inicio"><SelectorHora aria-label="Hora de inicio" value={v.horaInicio} onChange={(valor) => cambiarVentana(i, { horaInicio: valor })} /></Campo></div>
          <div className="grid grid-cols-2 gap-2"><Campo etiqueta="Día de fin"><Selector value={v.diaFin} onChange={(e) => cambiarVentana(i, { diaFin: e.target.value as DiaSemana })}>{DIAS_SEMANA.map((d) => <option key={d} value={d}>{ETIQUETA_DIA[d]}</option>)}</Selector></Campo><Campo etiqueta="Hora de fin"><SelectorHora aria-label="Hora de fin" value={v.horaFin} onChange={(valor) => cambiarVentana(i, { horaFin: valor })} /></Campo></div>
          <BotonIcono className="self-end" icono={Trash2} etiqueta={`Quitar intervalo ${i + 1}`} onClick={() => cambiar({ configuracion: c, ventanas: ventanas.filter((_, j) => j !== i) })} />
        </div>)}
        <Boton className="self-start" icono={Plus} disabled={ventanas.length >= 50} onClick={() => cambiar({ configuracion: c, ventanas: [...ventanas, { diaInicio: 'SABADO', horaInicio: '01:00', diaFin: 'SABADO', horaFin: '05:00' }] })}>Agregar intervalo</Boton>
        {ventanas.length > 0 && <VistaSemanalVentanas ventanas={ventanas} altoFila={20} />}
      </fieldset>
      {problema && <p className="text-sm text-warning">{problema}</p>}
      {servidores.length > 1 && <div className="flex flex-col items-start gap-2 border-t border-line pt-4">
        <Boton onClick={() => {
          onCambiar(servidores.map((item) => ({ ...item, mantenimiento: { ...item.mantenimiento, ventanas: ventanas.map((v) => ({ ...v })), configuracion: { ...item.mantenimiento.configuracion, frecuenciaRevisionDias: c.frecuenciaRevisionDias, frecuenciaMantenimientoDias: c.frecuenciaMantenimientoDias } } })))
          setAviso('Frecuencias y ventana aplicadas a todos los servidores. Configure las revisiones periódicas y las cuentas por servidor.')
        }}>Aplicar frecuencias y ventana a todos</Boton>
        <p className="text-xs text-ink-soft" role="status">{aviso || `${servidores.filter((item) => !problemaMantenimiento(item)).length} de ${servidores.length} servidores con mantenimiento definido.`}</p>
      </div>}
    </div>
  )
}
