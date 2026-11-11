#!/usr/bin/env bash
# Despliega en el EC2 el commit en el que está el repositorio (DEC-58). Lo llama vigilar.py después de actualizar el
# repositorio; también se puede usar a mano:
#
#   cd ~/komainos && git fetch origin && git checkout --force <sha>
#   bash despliegue/desplegar.sh <sha>                      despliega
#   bash despliegue/desplegar.sh <sha> --probar-reversion   despliega y vuelve a la versión anterior (prueba)
#
# Pasos: construir la imagen, respaldar la base, aplicar las migraciones pendientes de Backend/scripts/bd/cambios,
# cambiar de versión y comprobar que la nueva responde con su commit. Si algo falla vuelve a la versión anterior.
# Todo queda en ~/komainos-estado/registros. Los secretos se leen de ~/komainos/.env y nunca se imprimen.
set -Eeuo pipefail

REPO=${KOMAINOS_REPO:-$HOME/komainos}
ESTADO=${KOMAINOS_ESTADO:-$HOME/komainos-estado}
SEGUNDOS_SALUD=${KOMAINOS_SEGUNDOS_SALUD:-300}
RESPALDOS_CONSERVADOS=15

paso() { printf '\n== %s  %s\n' "$(date -u +%H:%M:%S)" "$*"; }
fallar() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }
valor_env() { grep -m1 "^$1=" "$REPO/.env" | cut -d= -f2- || true; }

# Datos de conexión de KOMAINOS_DB_URL (jdbc:postgresql://host:puerto/base?...), usuario y clave de .env
cargar_bd() {
    local url resto equipo
    url=$(valor_env KOMAINOS_DB_URL)
    [[ $url == jdbc:postgresql://* ]] || fallar "KOMAINOS_DB_URL no es una URL de PostgreSQL"
    resto=${url#jdbc:postgresql://}
    equipo=${resto%%/*}
    BD_HOST=${equipo%%:*}
    BD_PUERTO=5432
    [[ $equipo == *:* ]] && BD_PUERTO=${equipo##*:}
    BD_NOMBRE=${resto#*/}
    BD_NOMBRE=${BD_NOMBRE%%\?*}
    BD_USUARIO=$(valor_env KOMAINOS_DB_USUARIO)
    BD_CLAVE=$(valor_env KOMAINOS_DB_CLAVE)
}

psql_bd() {
    PGPASSWORD=$BD_CLAVE psql "host=$BD_HOST port=$BD_PUERTO dbname=$BD_NOMBRE user=$BD_USUARIO sslmode=require" "$@"
}

# pg_dump de la misma versión mayor que el servidor: el psql 15 de Amazon Linux no puede volcar una base 18
respaldar() {
    local mayor archivo
    mayor=$(psql_bd -Atc "show server_version_num" | cut -c1-2)
    archivo="komainos_$(date -u +%Y%m%d_%H%M%S)_${CORTO}.dump"
    PGPASSWORD=$BD_CLAVE docker run --rm --user "$(id -u):$(id -g)" -e PGPASSWORD \
        -v "$ESTADO/respaldos:/r" "postgres:${mayor}-alpine" \
        pg_dump "host=$BD_HOST port=$BD_PUERTO dbname=$BD_NOMBRE user=$BD_USUARIO sslmode=require" -Fc -f "/r/$archivo"
    chmod 600 "$ESTADO/respaldos/$archivo"
    RESPALDO="$ESTADO/respaldos/$archivo"
    echo "Respaldo: $RESPALDO ($(stat -c %s "$RESPALDO") bytes)"
    ls -1t "$ESTADO"/respaldos/*.dump | tail -n +$((RESPALDOS_CONSERVADOS + 1)) | xargs -r rm -f
}

# En orden de nombre (la fecha va primero), las que no figuran en el registro de migraciones aplicadas
migraciones_pendientes() {
    local f
    for f in Backend/scripts/bd/cambios/*.sql; do
        grep -qxF "$(basename "$f")" "$ESTADO/migraciones_aplicadas" || echo "$f"
    done
}

# Espera a que el contenedor esté sano y publique el commit esperado (o cualquiera, si se pasa vacío)
esperar_version() {
    local esperado=$1 limite=$((SECONDS + SEGUNDOS_SALUD)) salud commit=""
    while ((SECONDS < limite)); do
        salud=$(docker inspect -f '{{.State.Health.Status}}' komainos 2>/dev/null || echo ausente)
        if [[ $salud == healthy ]]; then
            commit=$(docker exec komainos curl -fsS http://127.0.0.1:8080/actuator/info 2>/dev/null \
                | python3 -c 'import json,sys; print(json.load(sys.stdin).get("komainos", {}).get("commit", ""))' \
                2>/dev/null || true)
            if [[ -z $esperado || $commit == "$esperado" ]]; then
                echo "En servicio: salud ${salud}, commit ${commit:-sin informar}"
                return 0
            fi
        fi
        [[ $salud == unhealthy ]] && break
        sleep 5
    done
    echo "La versión no quedó en servicio: salud ${salud}, commit ${commit:-sin informar}"
    docker logs --tail 40 komainos 2>&1 | sed 's/^/    /' || true
    return 1
}

revertir() {
    paso "Volver a la versión anterior"
    if ! docker image inspect komainos:anterior >/dev/null 2>&1; then
        echo "No hay imagen komainos:anterior: el servicio queda como está"
        return 1
    fi
    docker tag komainos:anterior komainos:latest
    ./iniciar.sh >/dev/null
    if esperar_version ""; then
        echo "Volvió la versión anterior"
    else
        echo "La versión anterior tampoco arrancó"
    fi
    if ((${#APLICADAS[@]})); then
        echo "La base quedó con las migraciones ${APLICADAS[*]}. Si la versión anterior no funciona con ellas,"
        echo "restaure el respaldo previo: $RESPALDO (DESPLIEGUE.md, sección 20)"
    fi
}

main() {
    local sha=${1:-} opcion=${2:-}
    [[ $sha =~ ^[0-9a-f]{40}$ ]] || fallar "Uso: desplegar.sh <sha de 40 caracteres> [--probar-reversion]"
    CORTO=${sha:0:12}
    APLICADAS=()
    RESPALDO=""
    mkdir -p "$ESTADO/registros" "$ESTADO/respaldos"
    chmod 700 "$ESTADO" "$ESTADO/respaldos"
    exec 9>"$ESTADO/.bloqueo-despliegue"
    flock -n 9 || fallar "Ya hay un despliegue en curso"
    local registro
    registro="$ESTADO/registros/$(date -u +%Y%m%d_%H%M%S)_${CORTO}.log"
    exec > >(tee -a "$registro") 2>&1
    ls -1t "$ESTADO"/registros/*.log | tail -n +51 | xargs -r rm -f

    paso "Despliegue de $CORTO ($(git -C "$REPO" log -1 --format=%s "$sha" 2>/dev/null || echo 'commit desconocido'))"
    cd "$REPO"
    [[ $(git rev-parse HEAD) == "$sha" ]] || fallar "El repositorio está en $(git rev-parse --short HEAD), no en $CORTO"
    [[ -f .env && -x iniciar.sh ]] || fallar "Faltan $REPO/.env o $REPO/iniciar.sh (DESPLIEGUE.md, pasos 8 y 10)"
    [[ -f $ESTADO/migraciones_aplicadas ]] || fallar "Falta $ESTADO/migraciones_aplicadas: ejecute despliegue/instalar.sh"
    cargar_bd

    paso "1/6 Construir la imagen"
    docker build -q -t "komainos:$CORTO" --build-arg KOMAINOS_COMMIT="$sha" \
        --build-arg KOMAINOS_CONSTRUCCION="$(date -u +%Y-%m-%dT%H:%M:%SZ)" . >/dev/null
    echo "Imagen komainos:$CORTO"

    paso "2/6 Respaldar la base"
    respaldar

    paso "3/6 Migraciones pendientes"
    local pendientes f
    mapfile -t pendientes < <(migraciones_pendientes)
    if ((${#pendientes[@]} == 0)); then
        echo "Ninguna"
    else
        # La versión en servicio no conoce el esquema nuevo: se detiene antes de cambiarlo
        docker stop komainos >/dev/null
        for f in "${pendientes[@]}"; do
            echo "Aplicando $(basename "$f")"
            if ! psql_bd -v ON_ERROR_STOP=1 -q -f "$f"; then
                ((${#APLICADAS[@]})) || docker start komainos >/dev/null
                fallar "Falló $(basename "$f"). Se deshizo su transacción; revise el script. Respaldo previo: $RESPALDO"
            fi
            basename "$f" >> "$ESTADO/migraciones_aplicadas"
            APLICADAS+=("$(basename "$f")")
        done
    fi

    paso "4/6 Cambiar de versión"
    if docker image inspect komainos:latest >/dev/null 2>&1; then
        docker tag komainos:latest komainos:anterior
    fi
    docker tag "komainos:$CORTO" komainos:latest
    ./iniciar.sh >/dev/null

    paso "5/6 Comprobar la nueva versión"
    if ! esperar_version "$sha"; then
        revertir || true
        fallar "La versión $CORTO no quedó en servicio"
    fi
    if [[ $opcion == --probar-reversion ]]; then
        revertir
        fallar "Prueba de reversión: la versión $CORTO se desplegó y se volvió a la anterior a propósito"
    fi

    paso "6/6 Limpieza"
    docker images --format '{{.Repository}}:{{.Tag}}' \
        | grep -E '^komainos:[0-9a-f]{12}$' | grep -v "^komainos:$CORTO$" | xargs -r docker rmi >/dev/null 2>&1 || true
    docker image prune -f >/dev/null
    paso "Desplegado $CORTO"
}

main "$@"; exit $?
