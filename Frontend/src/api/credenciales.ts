import { http } from './cliente'
import type {
  CredencialPeticion,
  CredencialRespuesta,
  CuentaServicioRespuesta,
  DatosCredencialPeticion,
  ResultadoAsignacionRespuesta,
  SecretoPeticion,
  SecretoReveladoRespuesta,
  ServidorAsignableRespuesta,
} from './types'

/** Operaciones comunes a credenciales documentales y cuentas de servicio (RF04, RF08) */
export const credencialesApi = {
  documentales: (idServidor: number) => http.get<CredencialRespuesta[]>(`/servidores/${idServidor}/credenciales`),
  registrarDocumental: (idServidor: number, datos: CredencialPeticion) =>
    http.post<CredencialRespuesta>(`/servidores/${idServidor}/credenciales`, datos),
  actualizarDatos: (id: number, datos: DatosCredencialPeticion) => http.put<CredencialRespuesta>(`/credenciales/${id}`, datos),
  actualizarSecreto: (id: number, datos: SecretoPeticion) => http.post<CredencialRespuesta>(`/credenciales/${id}/versiones`, datos),
  revocar: (id: number, motivo?: string) => http.post<CredencialRespuesta>(`/credenciales/${id}/revocacion`, { motivo }),
  revelar: (id: number, contrasena: string) =>
    http.post<SecretoReveladoRespuesta>(`/credenciales/${id}/revelado`, { contrasena }),
}

export const cuentasServicioApi = {
  listar: () => http.get<CuentaServicioRespuesta[]>('/cuentas-servicio'),
  registrar: (datos: CredencialPeticion) => http.post<CuentaServicioRespuesta>('/cuentas-servicio', datos),
  asignaciones: () => http.get<ServidorAsignableRespuesta[]>('/cuentas-servicio/asignaciones'),
  asignar: (id: number, idsServidores: number[]) =>
    http.put<ResultadoAsignacionRespuesta>(`/cuentas-servicio/${id}/servidores`, { idsServidores }),
  definirPredeterminada: (idCuentaServicio: number | null) =>
    http.put<CuentaServicioRespuesta[]>('/cuentas-servicio/predeterminada', { idCuentaServicio }),
}
