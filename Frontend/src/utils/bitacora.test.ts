import { describe, expect, it } from 'vitest'
import type { FichaAutomatizacionRespuesta } from '@/api/types'
import { bitacoraCsv, eventosBitacora } from './bitacora'

const ficha: FichaAutomatizacionRespuesta = {
  historial: [{ estadoAnterior: 'PROGRAMADA', estadoNuevo: 'EN_EVALUACION', fechaHora: '2026-10-01T10:00:00Z' }],
  evaluaciones: [
    {
      id: 1,
      tipo: 'PREVIA',
      estado: 'FINALIZADA',
      nivel: 'ADVERTENCIA',
      servidor: 'SERVPRODWEBV01',
      script: 'Análisis',
      numeroVersion: 2,
      fechaInicio: '2026-10-01T10:00:05Z',
      mediciones: [{ codigo: 'disco_uso_pct', instancia: '/', valor: 85 }],
    },
  ],
  mops: [
    {
      servidor: 'SERVPRODWEBV01',
      tareas: [
        {
          orden: 1,
          tarea: 'Liberar temporales',
          script: 'Liberar',
          numeroVersion: 1,
          estado: 'FALLIDA',
          codigoRetorno: 2,
          error: 'La carpeta "/tmp; rm" no existe',
          fechaInicio: '2026-10-01T11:00:00Z',
        },
      ],
    },
  ],
}

describe('bitácora exportable (RF53, R3.4)', () => {
  it('ordena los eventos por fecha y resume cada uno', () => {
    const eventos = eventosBitacora(ficha)
    expect(eventos.map((e) => e.tipo)).toEqual(['Estado de la orden', 'Evaluación previa', 'Tarea'])
    expect(eventos[1].detalle).toBe('disco_uso_pct[/]=85')
    expect(eventos[2].resultado).toBe('Fallida (código 2)')
  })

  it('escapa en el CSV los campos con comillas o punto y coma', () => {
    const csv = bitacoraCsv(ficha)
    expect(csv.split('\r\n')).toHaveLength(4)
    expect(csv).toContain('"La carpeta ""/tmp; rm"" no existe"')
  })
})
