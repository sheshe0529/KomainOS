#!/usr/bin/env bash
# Instala el despliegue automático en el EC2, una sola vez (DEC-58, DESPLIEGUE.md sección 20):
#
#   cd ~/komainos && git fetch origin && git checkout --force origin/main
#   bash despliegue/instalar.sh [--marcar-desplegado <sha>]
#
# - Registra como ya aplicadas las migraciones de Backend/scripts/bd/cambios que trae el repositorio: la base debe estar
#   al día con ellas (la instaló instalar_base.sql y se aplicaron los cambios posteriores).
# - --marcar-desplegado evita que se despliegue ese commit: úselo con el commit de main que ya está en servicio.
# - Crea el servicio y el temporizador de systemd que ejecutan despliegue/vigilar.py cada minuto.
set -Eeuo pipefail

REPO=${KOMAINOS_REPO:-$HOME/komainos}
ESTADO=${KOMAINOS_ESTADO:-$HOME/komainos-estado}

fallar() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

main() {
    local comando
    for comando in docker git python3 psql curl flock; do
        command -v "$comando" >/dev/null || fallar "Falta $comando (DESPLIEGUE.md, paso 5)"
    done
    [[ -f $REPO/.env ]] || fallar "Falta $REPO/.env (DESPLIEGUE.md, paso 8)"
    [[ -x $REPO/iniciar.sh ]] || fallar "Falta $REPO/iniciar.sh (DESPLIEGUE.md, paso 10)"
    [[ -f $REPO/despliegue/vigilar.py ]] || fallar "El repositorio no tiene despliegue/vigilar.py: actualícelo a main"
    id -nG | grep -qw docker || fallar "$USER no está en el grupo docker"

    mkdir -p "$ESTADO/registros" "$ESTADO/respaldos"
    chmod 700 "$ESTADO" "$ESTADO/respaldos"

    if [[ -f $ESTADO/migraciones_aplicadas ]]; then
        echo "Migraciones ya registradas: $(wc -l < "$ESTADO/migraciones_aplicadas")"
    else
        local f
        for f in "$REPO"/Backend/scripts/bd/cambios/*.sql; do basename "$f"; done | sort > "$ESTADO/migraciones_aplicadas"
        echo "Registradas como aplicadas $(wc -l < "$ESTADO/migraciones_aplicadas") migraciones (la base está al día)"
    fi

    if [[ ${1:-} == --marcar-desplegado ]]; then
        [[ ${2:-} =~ ^[0-9a-f]{40}$ ]] || fallar "--marcar-desplegado requiere un sha de 40 caracteres"
        echo "$2" > "$ESTADO/desplegado"
        echo "Marcado como desplegado: ${2:0:12}"
    fi

    sudo tee /etc/systemd/system/komainos-despliegue.service >/dev/null <<EOF
[Unit]
Description=KomainOS: despliega el commit nuevo de main cuando sus pruebas pasan (DEC-58)
Wants=network-online.target
After=network-online.target docker.service

[Service]
Type=oneshot
User=$USER
Group=$(id -gn)
WorkingDirectory=$REPO
ExecStart=/usr/bin/python3 $REPO/despliegue/vigilar.py
TimeoutStartSec=45min
EOF

    sudo tee /etc/systemd/system/komainos-despliegue.timer >/dev/null <<'EOF'
[Unit]
Description=KomainOS: busca cada minuto un commit nuevo de main para desplegar (DEC-58)

[Timer]
OnBootSec=3min
OnUnitInactiveSec=1min
AccuracySec=5s
Unit=komainos-despliegue.service

[Install]
WantedBy=timers.target
EOF

    sudo systemctl daemon-reload
    sudo systemctl enable --now komainos-despliegue.timer >/dev/null
    systemctl list-timers komainos-despliegue.timer --no-pager | head -2
    echo "Instalado. Registro: journalctl -u komainos-despliegue -f    Estado: python3 $REPO/despliegue/vigilar.py --estado"
}

main "$@"; exit $?
