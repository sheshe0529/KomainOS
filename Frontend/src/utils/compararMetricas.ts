import type { Analisis } from '@/api/automatizacion'
export function compararMetricas(evaluaciones: Analisis[]) {
  const filas = new Map<
    string,
    {
      clave: string
      servidor: string
      nombre: string
      instancia: string
      antes: string
      despues: string
      nivel: string
    }
  >()
  for (const e of evaluaciones) {
    if (e.tipo !== 'PREVIA' && e.tipo !== 'POSTERIOR') continue
    for (const m of e.mediciones ?? []) {
      const clave = `${e.idServidor}/${m.codigo}/${m.instancia ?? ''}`
      const fila = filas.get(clave) ?? {
        clave,
        servidor: e.servidor ?? '',
        nombre: m.nombre ?? m.codigo ?? '',
        instancia: m.instancia ?? '',
        antes: '—',
        despues: '—',
        nivel: 'Pendiente',
      }
      const valor = m.valor !== undefined && m.valor !== null ? String(m.valor) : (m.elementos ?? []).join(', ')
      if (e.tipo === 'PREVIA') fila.antes = valor
      else {
        fila.despues = valor
        fila.nivel = m.umbrales?.some((u) => u.nivel === 'CRITICO')
          ? 'Crítico'
          : m.umbrales?.some((u) => u.nivel === 'ADVERTENCIA')
            ? 'Advertencia'
            : 'Normal'
      }
      filas.set(clave, fila)
    }
  }
  return [...filas.values()]
}
