#!/usr/bin/env python3
"""Carga datos de ejemplo en KomainOS a traves de la API REST.

Usa la API (y no SQL directo) a proposito: cada registro pasa por las mismas
validaciones, reglas de negocio y auditoria que un registro hecho desde el
panel, asi que el script sirve ademas como prueba de extremo a extremo.

Es idempotente: si un registro ya existe (409) lo reutiliza y sigue.

Uso (con el backend corriendo):
    python scripts/cargar_datos_ejemplo.py                  # http://localhost:8081
    python scripts/cargar_datos_ejemplo.py --api http://localhost:8080/api

La contrasena del administrador se lee de KOMAINOS_ADMIN_CLAVE_INICIAL en
Backend/.env o de la opcion --clave. Los datos imitan las pantallas
preliminares (UI_preliminar) y no tienen valor documental.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

CLAVE_DEMO = "Cambiar.2026"  # contrasena de los usuarios de ejemplo (no del administrador)


class Api:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")
        self.token: str | None = None

    def llamar(self, metodo: str, ruta: str, cuerpo: object | None = None) -> tuple[int, object]:
        datos = None if cuerpo is None else json.dumps(cuerpo).encode("utf-8")
        peticion = urllib.request.Request(self.base + ruta, data=datos, method=metodo)
        peticion.add_header("Content-Type", "application/json")
        if self.token:
            peticion.add_header("Authorization", f"Bearer {self.token}")
        try:
            with urllib.request.urlopen(peticion, timeout=30) as r:
                texto = r.read().decode("utf-8")
                return r.status, (json.loads(texto) if texto else None)
        except urllib.error.HTTPError as e:
            texto = e.read().decode("utf-8")
            return e.code, (json.loads(texto) if texto else None)

    def exigir(self, metodo: str, ruta: str, cuerpo: object | None = None) -> object:
        estado, respuesta = self.llamar(metodo, ruta, cuerpo)
        if estado >= 400:
            sys.exit(f"{metodo} {ruta} -> {estado}: {respuesta}")
        return respuesta


def leer_clave_admin() -> str | None:
    env = Path(__file__).resolve().parent.parent / ".env"
    if env.is_file():
        for linea in env.read_text(encoding="utf-8").splitlines():
            if linea.startswith("KOMAINOS_ADMIN_CLAVE_INICIAL="):
                return linea.split("=", 1)[1].strip()
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default="http://localhost:8081/api")
    parser.add_argument("--usuario", default="admin")
    parser.add_argument("--clave", default=None)
    args = parser.parse_args()

    api = Api(args.api)
    clave = args.clave or leer_clave_admin()
    if not clave:
        sys.exit("Indique la contrasena del administrador con --clave o en Backend/.env")

    sesion = api.exigir("POST", "/auth/login", {"codigo": args.usuario, "contrasena": clave})
    api.token = sesion["token"]
    print(f"Sesion iniciada como {sesion['usuario']['nombreCompleto']}")

    # ------------------------------------------------------------ catalogos
    entornos = {e["nombre"]: e["id"] for e in api.exigir("GET", "/entornos")}
    for nombre, descripcion in [("Producción", "Servicios productivos"), ("Staging", "Preproducción"), ("QA", "Pruebas")]:
        if nombre not in entornos:
            entornos[nombre] = api.exigir("POST", "/entornos", {"nombre": nombre, "descripcion": descripcion})["id"]

    criticidades = {c["nombre"]: c["id"] for c in api.exigir("GET", "/niveles-criticidad")}
    for nombre, prioridad, revision, mantenimiento, autorizacion, validacion in [
        ("Alta", 1, 7, 30, 24, 24),
        ("Media", 2, 14, 45, 48, 48),
        ("Baja", 3, 30, 90, 72, 72),
    ]:
        if nombre not in criticidades:
            criticidades[nombre] = api.exigir("POST", "/niveles-criticidad", {
                "nombre": nombre, "prioridad": prioridad, "frecuenciaRevisionDias": revision,
                "frecuenciaMantenimientoDias": mantenimiento, "plazoAutorizacionHoras": autorizacion,
                "plazoValidacionHoras": validacion})["id"]

    def versiones_por_nombre() -> dict[str, int]:
        return {f"{so['nombre']} {v['version']}": v["id"]
                for so in api.exigir("GET", "/sistemas-operativos") for v in so.get("versiones", [])}

    sistemas = {so["nombre"]: so for so in api.exigir("GET", "/sistemas-operativos")}
    for nombre, familia, vers in [("Ubuntu", "LINUX", ["22.04", "24.04"]), ("RHEL", "LINUX", ["9"]),
                                  ("Windows Server", "WINDOWS", ["2022"])]:
        so = sistemas.get(nombre) or api.exigir("POST", "/sistemas-operativos", {"nombre": nombre, "familia": familia})
        existentes = {v["version"] for v in so.get("versiones", [])}
        for v in vers:
            if v not in existentes:
                api.exigir("POST", f"/sistemas-operativos/{so['id']}/versiones", {"version": v})
    versiones = versiones_por_nombre()

    # ------------------------------------------------------------- usuarios
    usuarios = {u["codigo"]: u["id"] for u in api.exigir("GET", "/usuarios?size=200")["contenido"]}
    for codigo, nombre, rol in [("m.herrera", "M. Herrera", "RESPONSABLE"), ("j.paredes", "J. Paredes", "RESPONSABLE"),
                                ("c.rojas", "C. Rojas", "RESPONSABLE"), ("operador", "Operador de turno", "OPERADOR")]:
        if codigo not in usuarios:
            usuarios[codigo] = api.exigir("POST", "/usuarios", {
                "codigo": codigo, "nombreCompleto": nombre, "contrasena": CLAVE_DEMO, "rol": rol})["id"]

    # ------------------------------------------------------------ servidores
    fin_de_semana = [{"diaInicio": "SABADO", "horaInicio": "01:00", "diaFin": "SABADO", "horaFin": "05:00"},
                     {"diaInicio": "DOMINGO", "horaInicio": "01:00", "diaFin": "DOMINGO", "horaFin": "05:00"}]
    noche_sabado = [{"diaInicio": "SABADO", "horaInicio": "22:00", "diaFin": "DOMINGO", "horaFin": "04:00"}]
    servidores = [
        # hostname, ip, dc, fisico, so, entorno, criticidad, responsable, modalidad, ventanas
        ("srv-app-01", "10.20.1.11", "DC-Norte", "esx-blade-04", "Ubuntu 22.04", "Producción", "Media", "m.herrera", "AUTOMATICA", fin_de_semana),
        ("srv-app-02", "10.20.1.12", "DC-Norte", "esx-blade-04", "Ubuntu 22.04", "Producción", "Media", "m.herrera", "AUTOMATICA", fin_de_semana),
        ("srv-db-01", "10.20.2.10", "DC-Norte", "esx-blade-02", "RHEL 9", "Producción", "Alta", "j.paredes", "AUTOMATICA", noche_sabado),
        ("srv-db-02", "10.20.2.11", "DC-Norte", "esx-blade-02", "RHEL 9", "Producción", "Alta", "j.paredes", "AUTOMATICA", noche_sabado),
        ("srv-cache-01", "10.20.3.10", "DC-Sur", "esx-blade-09", "Ubuntu 22.04", "Producción", "Alta", "m.herrera", "BAJO_DEMANDA", fin_de_semana),
        ("srv-web-01", "10.30.1.20", "DC-Sur", "esx-blade-11", "Windows Server 2022", "Staging", "Media", "c.rojas", "BAJO_DEMANDA", noche_sabado),
        ("srv-batch-01", "10.40.1.15", "DC-Norte", "esx-blade-06", "Ubuntu 24.04", "QA", "Baja", "c.rojas", None, []),
    ]
    existentes = {s["hostname"]: s["id"] for s in api.exigir("GET", "/servidores?size=500")["contenido"]}
    ids: dict[str, int] = {}
    for (host, ip, dc, fisico, so, entorno, criticidad, responsable, modalidad, ventanas) in servidores:
        if host in existentes:
            ids[host] = existentes[host]
            continue
        ficha = api.exigir("POST", "/servidores", {
            "hostname": host, "direccionIp": ip, "vdc": dc, "servidorFisico": fisico,
            "idVersionSistemaOperativo": versiones[so], "plataforma": "VMware", "dns": f"{host}.komainos.local",
            "idEntorno": entornos[entorno], "idNivelCriticidad": criticidades[criticidad],
            "idResponsable": usuarios[responsable], "descripcion": f"Servidor de ejemplo {host}"})
        ids[host] = ficha["id"]
        if ventanas:
            api.exigir("PUT", f"/servidores/{ficha['id']}/ventanas", {"ventanas": ventanas})
        if modalidad:
            api.exigir("PUT", f"/servidores/{ficha['id']}/configuracion", {"modalidadPlanificacion": modalidad})
        print(f"  servidor {host} registrado")

    # ---------------------------------------------------------------- grupos
    grupos = {g["nombre"]: g["id"] for g in api.exigir("GET", "/grupos")}
    if "Grupo DB Producción" not in grupos:
        g = api.exigir("POST", "/grupos", {"nombre": "Grupo DB Producción",
                                           "descripcion": "Bases de datos productivas de J. Paredes"})
        api.exigir("PUT", f"/grupos/{g['id']}/integrantes", {"idsServidores": [ids["srv-db-01"], ids["srv-db-02"]]})
        api.exigir("PUT", f"/grupos/{g['id']}/configuracion",
                   {"modalidadPlanificacion": "BAJO_DEMANDA", "modoEjecucion": "SECUENCIAL"})
        print("  grupo Grupo DB Producción registrado")

    total = api.exigir("GET", "/servidores?size=1")["totalElementos"]
    print(f"Listo: {total} servidores en el inventario. Usuarios de ejemplo con contrasena '{CLAVE_DEMO}'.")


if __name__ == "__main__":
    main()
