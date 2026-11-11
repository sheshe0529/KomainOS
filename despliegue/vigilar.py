#!/usr/bin/env python3
"""
Despliegue automático de KomainOS en el EC2 (DEC-58). Lo ejecuta un temporizador de systemd cada minuto.

Cuando main tiene un commit que todavía no está desplegado y GitHub Actions ya aprobó sus pruebas, actualiza el
repositorio a ese commit y ejecuta despliegue/desplegar.sh. No abre puertos ni guarda credenciales en GitHub ni en el
servidor: lee el repositorio público con git y la API pública de GitHub, que admite 60 consultas por hora sin token.

Uso manual:
  python3 despliegue/vigilar.py            una pasada (lo que hace el temporizador)
  python3 despliegue/vigilar.py --estado   qué está desplegado y qué se consultó por última vez
  python3 despliegue/vigilar.py --reintentar
                                           vuelve a intentar el commit cuyo despliegue falló
"""

import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(os.environ.get("KOMAINOS_REPO", str(Path.home() / "komainos")))
ESTADO = Path(os.environ.get("KOMAINOS_ESTADO", str(Path.home() / "komainos-estado")))
RAMA = os.environ.get("KOMAINOS_RAMA", "main")
API = os.environ.get("KOMAINOS_API_GITHUB", "https://api.github.com")

# Los jobs de .github/workflows/pruebas.yml que deben terminar bien antes de desplegar
PRUEBAS = ("Pruebas del backend", "Pruebas del panel", "Pruebas del despliegue")
# Un commit sin pruebas registradas pasado este tiempo no se despliega (por ejemplo, uno anterior al DEC-58)
MINUTOS_SIN_PRUEBAS = 60
# Pasado este tiempo desde el commit ya no se vuelve a consultar: hay que desplegarlo a mano
MINUTOS_MAXIMOS = 24 * 60
# Pausa entre consultas a la API por un mismo commit, según cómo estaban sus pruebas
SEGUNDOS_ENTRE_CONSULTAS = {"pendiente": 110, "rechazado": 900}


def registrar(mensaje):
    print(f"{datetime.now(timezone.utc):%Y-%m-%dT%H:%M:%SZ} {mensaje}", flush=True)


def leer(nombre):
    archivo = ESTADO / nombre
    return archivo.read_text(encoding="utf-8").strip() if archivo.exists() else ""


def escribir(nombre, valor):
    (ESTADO / nombre).write_text(valor + "\n", encoding="utf-8")


def git(*argumentos):
    return subprocess.run(["git", "-C", str(REPO), *argumentos], check=True, capture_output=True,
                          text=True).stdout.strip()


def repositorio():
    """dueño/nombre a partir del remoto origin (https://github.com/dueño/nombre.git)"""
    url = git("remote", "get-url", "origin")
    coincidencia = re.search(r"github\.com[/:]([^/]+/[^/]+?)(?:\.git)?/?$", url)
    if not coincidencia:
        raise RuntimeError(f"El remoto origin no es de GitHub: {url}")
    return coincidencia.group(1)


def consultar_api(ruta):
    peticion = urllib.request.Request(API + ruta, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "komainos-despliegue",
        "X-GitHub-Api-Version": "2022-11-28",
    })
    with urllib.request.urlopen(peticion, timeout=20) as respuesta:
        return json.load(respuesta)


def decidir(check_runs, minutos_desde_commit):
    """
    'aprobado', 'pendiente' o 'rechazado' según las pruebas de GitHub Actions del commit. Si un job se volvió a
    ejecutar, cuenta su última ejecución.
    """
    ultimas = {}
    for check in sorted(check_runs, key=lambda c: c.get("started_at") or ""):
        nombre = next((p for p in PRUEBAS if p in check.get("name", "")), None)
        if nombre:
            ultimas[nombre] = check
    if not ultimas:
        return "rechazado" if minutos_desde_commit > MINUTOS_SIN_PRUEBAS else "pendiente"
    terminadas = [c for c in ultimas.values() if c.get("status") == "completed"]
    if any(c.get("conclusion") != "success" for c in terminadas):
        return "rechazado"
    if len(ultimas) == len(PRUEBAS) and len(terminadas) == len(PRUEBAS):
        return "aprobado"
    return "pendiente"


def debe_consultar(sha, ahora):
    """Respeta la pausa entre consultas por commit, para no agotar las 60 consultas por hora"""
    anterior = leer("consulta.json")
    if not anterior:
        return True
    consulta = json.loads(anterior)
    if consulta.get("sha") != sha:
        return True
    pausa = SEGUNDOS_ENTRE_CONSULTAS.get(consulta.get("estado"), 0)
    return ahora - consulta.get("momento", 0) >= pausa


def una_pasada():
    sha = git("ls-remote", "origin", f"refs/heads/{RAMA}").split()[0]
    if sha in (leer("desplegado"), leer("fallido"), leer("omitido")):
        return 0
    git("fetch", "--quiet", "origin", RAMA)
    minutos = (time.time() - int(git("log", "-1", "--format=%ct", sha))) / 60
    if minutos > MINUTOS_MAXIMOS:
        registrar(f"{sha[:12]}: el commit tiene más de {MINUTOS_MAXIMOS // 60} h sin desplegarse; se omite "
                  f"(desplegarlo a mano: bash despliegue/desplegar.sh {sha})")
        escribir("omitido", sha)
        return 0
    ahora = time.time()
    if not debe_consultar(sha, ahora):
        return 0
    try:
        check_runs = consultar_api(f"/repos/{repositorio()}/commits/{sha}/check-runs?per_page=100")["check_runs"]
    except (urllib.error.URLError, TimeoutError, KeyError, ValueError) as error:
        registrar(f"{sha[:12]}: no se pudo consultar GitHub ({error}); se reintenta en la próxima pasada")
        return 0
    estado = decidir(check_runs, minutos)
    escribir("consulta.json", json.dumps({"sha": sha, "estado": estado, "momento": ahora}))
    if estado != "aprobado":
        registrar(f"{sha[:12]}: pruebas {estado}")
        return 0

    registrar(f"{sha[:12]}: pruebas aprobadas, se despliega")
    git("checkout", "--quiet", "--force", sha)
    resultado = subprocess.run(["bash", str(REPO / "despliegue" / "desplegar.sh"), sha])
    if resultado.returncode == 0:
        escribir("desplegado", sha)
        (ESTADO / "fallido").unlink(missing_ok=True)
        registrar(f"{sha[:12]}: desplegado")
    else:
        escribir("fallido", sha)
        registrar(f"{sha[:12]}: el despliegue falló (código {resultado.returncode}); revise "
                  f"{ESTADO / 'registros'}. Para reintentar: python3 despliegue/vigilar.py --reintentar")
    return resultado.returncode


def main(argumentos):
    ESTADO.mkdir(mode=0o700, parents=True, exist_ok=True)
    if "--estado" in argumentos:
        for nombre in ("desplegado", "fallido", "omitido", "consulta.json"):
            print(f"{nombre}: {leer(nombre) or '—'}")
        return 0
    if "--reintentar" in argumentos:
        (ESTADO / "fallido").unlink(missing_ok=True)
        (ESTADO / "consulta.json").unlink(missing_ok=True)
        print("El próximo ciclo vuelve a intentar el commit de main")
        return 0
    import fcntl  # solo Linux: las pruebas de decidir() corren en cualquier sistema

    with open(ESTADO / ".bloqueo-vigilancia", "w") as bloqueo:
        try:
            fcntl.flock(bloqueo, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return 0
        try:
            return una_pasada()
        except subprocess.CalledProcessError as error:
            # Sin red o GitHub no disponible: se reintenta en la próxima pasada
            registrar(f"git {' '.join(error.cmd[3:])} falló: {(error.stderr or '').strip()[:300]}")
            return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
