#!/usr/bin/env bash
# Confía en el certificado HTTPS de WinRM de un servidor Windows sin desactivar la validación (DESPLIEGUE.md,
# sección 13). Se ejecuta en el EC2, una vez por servidor Windows con certificado autofirmado (o por CA interna):
#
#   bash ~/komainos/despliegue/confiar-winrm.sh <IP privada del servidor> <huella SHA-1 del certificado>
#
# La huella se lee en el propio Windows: es CertificateThumbprint en «winrm enumerate winrm/config/listener». El script
# descarga el certificado, lo agrega solo si su huella coincide con esa y comprueba que la conexión TLS se valida.
#
# Los certificados de confianza quedan en ~/komainos-certificados/winrm-confianza.pem, montado en el contenedor por
# iniciar.sh; REQUESTS_CA_BUNDLE en .env le indica a pywinrm que confíe solo en ellos. Agregar un servidor más no
# requiere reiniciar: cada ejecución de Ansible vuelve a leer el archivo.
set -Eeuo pipefail

REPO=${KOMAINOS_REPO:-$HOME/komainos}
DIR=${KOMAINOS_CERTIFICADOS:-$HOME/komainos-certificados}
ARCHIVO=winrm-confianza.pem
EN_CONTENEDOR=/opt/komainos/winrm-ca
IMAGEN=komainos:latest

fallar() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# Python de la imagen (el mismo de Ansible): no requiere openssl en el EC2 y funciona con el contenedor detenido
python_imagen() {
    docker run --rm -i --user "$(id -u):$(id -g)" -v "$DIR:/c" --entrypoint /opt/ansible/bin/python "$IMAGEN" - "$@"
}

agregar_certificado() {
    python_imagen "$1" "$2" "$3" "$ARCHIVO" <<'PY'
import hashlib, re, ssl, sys

ip, puerto, esperada, archivo = sys.argv[1], int(sys.argv[2]), sys.argv[3], "/c/" + sys.argv[4]
esperada = re.sub(r"[^0-9A-F]", "", esperada.upper())
huella = lambda pem: hashlib.sha1(ssl.PEM_cert_to_DER_cert(pem)).hexdigest().upper()

pem = ssl.get_server_certificate((ip, puerto), timeout=15)
if huella(pem) != esperada:
    sys.exit(f"El certificado de {ip}:{puerto} tiene la huella {huella(pem)}, no {esperada}: no se agregó")
try:
    actual = open(archivo, encoding="ascii").read()
except FileNotFoundError:
    actual = ""
bloques = re.findall(r"-----BEGIN CERTIFICATE-----.+?-----END CERTIFICATE-----", actual, re.S)
if esperada in {huella(b + "\n") for b in bloques}:
    print(f"El certificado de {ip} ya era de confianza")
else:
    with open(archivo, "a", encoding="ascii") as f:
        f.write(f"# {ip}:{puerto} SHA-1 {esperada}\n{pem.strip()}\n")
    print(f"Certificado de {ip} agregado (huella SHA-1 {esperada})")
PY
}

# 401 significa que el TLS se validó y WinRM pide credenciales: la prueba no envía ninguna
comprobar_tls() {
    python_imagen "$1" "$2" "$ARCHIVO" <<'PY'
import sys
import requests

ip, puerto, archivo = sys.argv[1], sys.argv[2], "/c/" + sys.argv[3]
r = requests.post(f"https://{ip}:{puerto}/wsman", verify=archivo, timeout=15)
print(f"TLS validado con {ip}:{puerto} (WinRM respondió {r.status_code}, pide credenciales)")
PY
}

esperar_salud() {
    local i estado=
    for ((i = 0; i < 60; i++)); do
        estado=$(docker inspect -f '{{.State.Health.Status}}' komainos 2>/dev/null || true)
        [[ $estado == healthy ]] && { echo "KomainOS en servicio"; return 0; }
        sleep 5
    done
    fallar "KomainOS no quedó en servicio (estado: ${estado:-sin contenedor}): docker logs komainos"
}

main() {
    local ip=${1:-} huella=${2:-} puerto cambio=false
    [[ $ip =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || fallar "Uso: confiar-winrm.sh <IP del servidor> <huella SHA-1>"
    [[ ${huella//[: ]/} =~ ^[0-9A-Fa-f]{40}$ ]] || fallar "La huella SHA-1 tiene 40 caracteres hexadecimales"
    [[ -f $REPO/.env && -f $REPO/iniciar.sh ]] || fallar "Faltan $REPO/.env o $REPO/iniciar.sh (DESPLIEGUE.md)"
    puerto=$(grep -m1 '^KOMAINOS_ANSIBLE_WINRM_PUERTO=' "$REPO/.env" | cut -d= -f2- || true)
    puerto=${puerto:-5986}

    install -d -m 755 "$DIR"
    # Si Docker creó la carpeta al montarla antes de que existiera, es de root
    [[ -w $DIR ]] || fallar "Sin permiso de escritura en $DIR: sudo chown $(id -un): $DIR"
    agregar_certificado "$ip" "$puerto" "$huella"
    chmod 644 "$DIR/$ARCHIVO"
    comprobar_tls "$ip" "$puerto"

    if ! grep -q '^REQUESTS_CA_BUNDLE=' "$REPO/.env"; then
        cp -p "$REPO/.env" "$REPO/.env.antes-winrm-$(date +%Y%m%d_%H%M%S)"
        printf '\n# Certificados WinRM de confianza (despliegue/confiar-winrm.sh, DESPLIEGUE.md sección 13)\nREQUESTS_CA_BUNDLE=%s/%s\n' \
            "$EN_CONTENEDOR" "$ARCHIVO" >> "$REPO/.env"
        echo "REQUESTS_CA_BUNDLE agregado a .env"
        cambio=true
    fi
    if ! grep -q "$EN_CONTENEDOR" "$REPO/iniciar.sh"; then
        grep -q '^  -v komainos-ssh:/home/komainos/.ssh \\$' "$REPO/iniciar.sh" \
            || fallar "Agregue a mano a iniciar.sh la línea «-v $DIR:$EN_CONTENEDOR:ro \\» (DESPLIEGUE.md, sección 10)"
        cp -p "$REPO/iniciar.sh" "$REPO/iniciar.sh.antes-winrm-$(date +%Y%m%d_%H%M%S)"
        sed -i "s|^  -v komainos-ssh:/home/komainos/.ssh \\\\\$|&\n  -v $DIR:$EN_CONTENEDOR:ro \\\\|" "$REPO/iniciar.sh"
        grep -q "$EN_CONTENEDOR" "$REPO/iniciar.sh" || fallar "No se pudo agregar el montaje a iniciar.sh"
        echo "Montaje de $DIR agregado a iniciar.sh"
        cambio=true
    fi
    if $cambio; then
        echo "Reiniciando KomainOS para aplicar la configuración..."
        (cd "$REPO" && ./iniciar.sh >/dev/null)
        esperar_salud
    fi
    echo "Listo: vuelva a usar «Probar acceso» en el servidor $ip"
}

main "$@"
