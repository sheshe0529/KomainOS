import { ETIQUETA_DIA, ETIQUETA_MODALIDAD } from '@/utils/etiquetas'
import type { ServidorPreparado } from './preparacion'
import { cuentasServicioApi } from '@/api/credenciales'
import { useConsulta } from '@/hooks/useConsulta'

export function ResumenRegistro({ servidores, omitidos = 0 }: { servidores: ServidorPreparado[]; omitidos?: number }) {
  const cuentas = useConsulta(() => cuentasServicioApi.listar(), [])
  const nuevos = servidores.filter((s) => !s.idServidorExistente).length
  return <div className="flex flex-col gap-4">
    <div><h4 className="font-semibold">Todo listo para confirmar</h4><p className="mt-1 text-sm text-ink-soft">{nuevos} {nuevos === 1 ? 'nuevo' : 'nuevos'} · {servidores.length - nuevos} por actualizar · {omitidos} omitidos. Revise el mantenimiento de cada servidor antes de guardar.</p></div>
    <div className="overflow-x-auto rounded-lg border border-line"><table className="w-full min-w-[780px] text-left text-sm">
      <thead className="bg-panel-muted text-xs text-ink-soft"><tr>{['Servidor', 'Frecuencias', 'Cuenta de servicio', 'Ventana permisiva', 'Planificación'].map((t) => <th key={t} className="px-3 py-3 font-medium">{t}</th>)}</tr></thead>
      <tbody className="divide-y divide-line">{servidores.map((s) => {
        const c = s.mantenimiento.configuracion
        return <tr key={s.clave}>
          <td className="px-3 py-3 align-top"><span className="block font-mono font-medium">{s.hostname}</span><span className="block text-xs text-ink-soft">{s.direccionIp} · {s.idServidorExistente ? 'Actualizar' : 'Nuevo'}</span><span className="block text-xs text-ink-soft">{s.criticidad} · {s.sistemaOperativo}</span></td>
          <td className="px-3 py-3 align-top text-xs">Revisión: {c.frecuenciaRevisionDias} días<br />Mantenimiento: {c.frecuenciaMantenimientoDias} días</td>
          <td className="px-3 py-3 align-top text-xs">{c.idCuentaServicio ? cuentas.datos?.find((cuenta) => cuenta.id === c.idCuentaServicio)?.nombre ?? `Cuenta #${c.idCuentaServicio}` : 'Predeterminada del sistema'}</td>
          <td className="px-3 py-3 align-top text-xs">{s.mantenimiento.ventanas.map((v, i) => <span key={i} className="block">{ETIQUETA_DIA[v.diaInicio]} {v.horaInicio} – {ETIQUETA_DIA[v.diaFin]} {v.horaFin}</span>)}</td>
          <td className="px-3 py-3 align-top">{ETIQUETA_MODALIDAD[c.modalidadPlanificacion]}</td>
        </tr>
      })}</tbody>
    </table></div>
    <p className="text-xs text-ink-soft">Al confirmar se guardarán los datos, la configuración y las ventanas. Los servidores con modalidad automática quedarán habilitados para el proceso de planificación del sistema.</p>
  </div>
}
