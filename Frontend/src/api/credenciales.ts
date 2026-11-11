import { consulta, descargar, http } from './cliente'
import type { FormatoArchivo } from './intercambio'
import type {
  AsignacionServidoresPeticion,
  CredencialPeticion,
  CredencialRespuesta,
  CuentaServicioRespuesta,
  DatosCredencialPeticion,
  ResultadoAsignacionRespuesta,
  SecretoPeticion,
  SecretoReveladoRespuesta,
  ServidorAsignableRespuesta,
  VistaAnsiblePeticion,
  VistaAnsibleRespuesta,
} from './types'

export type FinalidadCuentaServicio = AsignacionServidoresPeticion['finalidad']

/** Operaciones comunes a credenciales documentales y cuentas de servicio (RF04, RF08) */
export const credencialesApi = {
  documentales: (idServidor: number) => http.get<CredencialRespuesta[]>(`/servidores/${idServidor}/credenciales`),
  registrarDocumental: (idServidor: number, datos: CredencialPeticion) =>
    http.post<CredencialRespuesta>(`/servidores/${idServidor}/credenciales`, datos),
  actualizarDatos: (id: number, datos: DatosCredencialPeticion) => http.put<CredencialRespuesta>(`/credenciales/${id}`, datos),
  actualizarSecreto: (id: number, datos: SecretoPeticion) => http.put<CredencialRespuesta>(`/credenciales/${id}/secreto`, datos),
  revocar: (id: number, motivo?: string) => http.post<CredencialRespuesta>(`/credenciales/${id}/revocacion`, { motivo }),
  revelar: (id: number, contrasena: string) =>
    http.post<SecretoReveladoRespuesta>(`/credenciales/${id}/revelado`, { contrasena }),
  marcarPrincipal: (id: number) => http.post<CredencialRespuesta>(`/credenciales/${id}/principal`),
  /** Exportación adicional: todas las credenciales documentales vigentes de todos los servidores */
  exportarTodas: (formato: FormatoArchivo, contrasena: string) =>
    descargar(`/credenciales/exportacion${consulta({ formato })}`, `credenciales_servidores.${formato.toLowerCase()}`, {
      method: 'POST',
      body: JSON.stringify({ contrasena }),
    }),
}

export const cuentasServicioApi = {
  listar: () => http.get<CuentaServicioRespuesta[]>('/cuentas-servicio'),
  registrar: (datos: CredencialPeticion) => http.post<CuentaServicioRespuesta>('/cuentas-servicio', datos),
  asignaciones: () => http.get<ServidorAsignableRespuesta[]>('/cuentas-servicio/asignaciones'),
  asignar: (id: number, idsServidores: number[], finalidad: FinalidadCuentaServicio) =>
    http.put<ResultadoAsignacionRespuesta>(`/cuentas-servicio/${id}/servidores`, { idsServidores, finalidad }),
  definirPredeterminada: (idCuentaServicio: number | null, finalidad: FinalidadCuentaServicio) =>
    http.put<CuentaServicioRespuesta[]>('/cuentas-servicio/predeterminada', { idCuentaServicio, finalidad }),
  /** Configuración de Ansible que resulta de la cuenta, con los secretos enmascarados (DEC-57) */
  vistaAnsible: (datos: VistaAnsiblePeticion) => http.post<VistaAnsibleRespuesta[]>('/cuentas-servicio/vista-ansible', datos),
}
