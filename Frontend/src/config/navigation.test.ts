import { describe, expect, it } from 'vitest'
import { matchPath } from 'react-router-dom'
import { inicioPara, isNavGroup, navigation, navegacionPara } from './navigation'

const hojas = navigation.flatMap((item) => (isNavGroup(item) ? item.children : [item]))
function activas(ruta: string) {
  return hojas.filter((h) => matchPath({ path: h.path, end: h.exact ?? false }, ruta)).map((h) => h.id)
}
describe('Selección y ubicación del menú', () => {
  it.each([
    ['/automatizacion', 'sala'],
    ['/automatizacion/scripts', 'scripts'],
    ['/automatizacion/scripts/4/publicar', 'scripts'],
    ['/automatizacion/tareas', 'tareas'],
    ['/automatizacion/ejecuciones', 'ejecuciones'],
    ['/automatizacion/incidencias', 'incidencias'],
    ['/configuracion/politicas-cambio', 'politicas'],
    ['/servidores/12', 'servidores'],
    ['/dashboard', 'dashboard'],
    ['/umbrales/3', 'umbrales'],
    ['/ordenes/12', 'ordenes'],
    ['/cronograma/dia/2026-11-06', 'cronograma'],
  ])('selecciona una sola sección para %s', (ruta, id) => expect(activas(ruta)).toEqual([id]))
  it('ubica las políticas dentro de Configuración', () => {
    const grupo = navigation.find((g) => isNavGroup(g) && g.children.some((h) => h.id === 'politicas'))
    expect(grupo?.id).toBe('configuracion')
  })
  it('sigue el orden acordado de los módulos', () => {
    expect(navigation.map((m) => m.id)).toEqual([
      'dashboard',
      'planificacion',
      'inventario',
      'automatizacion',
      'referencias',
      'alertas',
      'auditoria',
      'configuracion',
    ])
  })
  it('agrupa catálogos y umbrales en un mismo módulo', () => {
    const grupo = navigation.find((g) => g.id === 'referencias')
    expect(grupo && isNavGroup(grupo) ? grupo.children.map((h) => h.id) : []).toEqual(['catalogos', 'umbrales'])
  })
  it('el responsable entra al cronograma y no ve el dashboard', () => {
    expect(inicioPara('RESPONSABLE')).toBe('/cronograma')
    expect(inicioPara('OPERADOR')).toBe('/dashboard')
    expect(navegacionPara('RESPONSABLE').some((m) => m.id === 'dashboard')).toBe(false)
  })
  it('Mi cuenta está en el menú de todos los roles y es la pantalla del nombre en la barra superior', () => {
    for (const rol of ['ADMINISTRADOR', 'OPERADOR', 'RESPONSABLE'] as const) {
      const hojas = navegacionPara(rol).flatMap((g) => (isNavGroup(g) ? g.children : [g]))
      expect(hojas.find((h) => h.id === 'mi-cuenta')?.path).toBe('/mi-cuenta')
    }
    expect(activas('/mi-cuenta')).toEqual(['mi-cuenta'])
  })
  it('el responsable consulta las órdenes y el cronograma desde Planificación', () => {
    const grupo = navegacionPara('RESPONSABLE').find((m) => m.id === 'planificacion')
    expect(grupo && isNavGroup(grupo) ? grupo.children.map((h) => h.id) : []).toEqual(['ordenes', 'cronograma'])
  })
  it('el responsable no recibe las políticas administrativas', () => {
    const visibles = navegacionPara('RESPONSABLE').flatMap((g) => (isNavGroup(g) ? g.children : [g]))
    expect(visibles.some((h) => h.id === 'politicas')).toBe(false)
  })
  it('la auditoría aparece como entrada principal para el administrador', () => {
    for (const rol of ['ADMINISTRADOR', 'OPERADOR', 'RESPONSABLE'] as const) {
      const visibles = navegacionPara(rol)
      expect(visibles.some((h) => !isNavGroup(h) && h.id === 'auditoria')).toBe(rol === 'ADMINISTRADOR')
    }
    expect(activas('/configuracion/auditoria')).toEqual(['auditoria'])
  })
})
