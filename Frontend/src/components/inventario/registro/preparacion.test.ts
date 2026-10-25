import { describe, expect, it } from 'vitest'
import type { Catalogos } from '@/hooks/useCatalogos'
import { filasSeleccionadas, prepararServidor, problemaMantenimiento } from './preparacion'

const catalogos: Catalogos = {
  entornos: [],
  criticidades: [{ id: 1, nombre: 'Alta', frecuenciaRevisionDias: 7, frecuenciaMantenimientoDias: 30 }],
  sistemasOperativos: [{ id: 1, nombre: 'Ubuntu', familia: 'LINUX', versiones: [{ id: 2, version: '24.04' }] }],
}
const datos = { hostname: 'srv-01', direccionIp: '10.0.0.1', idNivelCriticidad: 1, idVersionSistemaOperativo: 2 }

describe('Registro por etapas (RF09, RF12, RF17-RF19)', () => {
  it('copia las frecuencias de criticidad y exige una ventana antes de continuar', () => {
    const s = prepararServidor(datos, catalogos)
    expect(s.mantenimiento.configuracion).toMatchObject({ frecuenciaRevisionDias: 7, frecuenciaMantenimientoDias: 30, modalidadPlanificacion: 'AUTOMATICA' })
    expect(problemaMantenimiento(s)).toContain('intervalo')
    expect(catalogos.criticidades[0].frecuenciaRevisionDias).toBe(7)
  })

  it('conserva configuración, cuenta, modalidad y ventana de un servidor existente', () => {
    const s = prepararServidor(datos, catalogos, 3, { id: 8, fechaActualizacion: '2026-10-24T12:00:00Z',
      configuracion: { frecuenciaRevisionDias: 5, frecuenciaMantenimientoDias: 15, modalidadPlanificacion: 'BAJO_DEMANDA', idCuentaServicio: 4, fechaActualizacion: '2026-10-24T10:00:00Z' },
      ventanas: [{ diaInicio: 'SABADO', horaInicio: '22:00:00', diaFin: 'DOMINGO', horaFin: '02:00:00' }],
    })
    expect(s.mantenimiento.configuracion).toMatchObject({ frecuenciaRevisionDias: 5, modalidadPlanificacion: 'BAJO_DEMANDA', idCuentaServicio: 4, versionEsperada: '2026-10-24T10:00:00Z' })
    expect(s.idServidorExistente).toBe(8)
    expect(s.mantenimiento.ventanas[0].horaInicio).toBe('22:00')
    expect(problemaMantenimiento(s)).toBeUndefined()
  })

  it('solo prepara nuevos y duplicados sobrescribibles confirmados', () => {
    const seleccionadas = filasSeleccionadas([
      { fila: 2, estado: 'NUEVA' }, { fila: 3, estado: 'DUPLICADA', sobrescribible: true },
      { fila: 4, estado: 'DUPLICADA', sobrescribible: false }, { fila: 5, estado: 'ERRONEA' },
      { fila: 6, estado: 'DUPLICADA', sobrescribible: true },
    ], [3, 4, 5])
    expect(seleccionadas.map((s) => s.fila)).toEqual([2, 3])
  })

  it('rechaza frecuencias vacías, decimales y ventanas con fin anterior o igual al inicio', () => {
    const s = prepararServidor(datos, catalogos)
    s.mantenimiento.configuracion.frecuenciaRevisionDias = 1.5
    expect(problemaMantenimiento(s)).toContain('enteros')
    s.mantenimiento.configuracion.frecuenciaRevisionDias = 7
    s.mantenimiento.ventanas = [{ diaInicio: 'SABADO', horaInicio: '05:00', diaFin: 'SABADO', horaFin: '01:00' }]
    expect(problemaMantenimiento(s)).toContain('revise')
    s.mantenimiento.ventanas[0].horaFin = '05:00'
    expect(problemaMantenimiento(s)).toContain('revise')
  })
})
