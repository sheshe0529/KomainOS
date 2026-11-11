# Despliegue de KomainOS en AWS (EC2 + RDS PostgreSQL)

Guía paso a paso para poner KomainOS en producción con **una instancia EC2 con Amazon Linux 2023** y una base
**Amazon RDS for PostgreSQL**. El servidor no necesita Java, Node ni Maven: Docker compila y empaqueta todo.

```
Usuarios ──HTTP/HTTPS──▶ EC2 (Amazon Linux 2023)
                          └─ contenedor komainos: panel web + API + Ansible
                                 │                          │
                      TLS 5432   ▼                          ▼  SSH 22 / WinRM 5986
                  Amazon RDS for PostgreSQL         Servidores que se mantienen
```

La imagen trae el panel compilado, la API de Spring Boot y el controlador Ansible que ejecuta los scripts en los
servidores gestionados. No hay modo simulado: cada análisis, prueba de acceso y tarea se ejecuta de verdad (DEC-56).

---

## Archivos necesarios

Todos están en la rama `main` del repositorio (`https://github.com/sheshe0529/KomainOS`). El único que hay que crear
en el servidor es `.env`.

| Archivo | Para qué sirve | Qué hacer |
|---|---|---|
| `Dockerfile` | Construye la imagen en tres etapas: panel (Node 22), backend (Maven + JDK 21) y ejecución (JRE 21 + `ansible-core` 2.21 + `pywinrm` + `sshpass` + certificados de RDS) | Nada: se usa tal cual |
| `.dockerignore` | Deja fuera de la imagen `node_modules`, `target`, documentos y cualquier `.env` | Nada |
| `.env.produccion.example` | Plantilla de las variables de entorno | Copiarlo a **`.env`** y completarlo (paso 8) |
| `Backend/scripts/bd/instalar_base.sql` | Crea la base: ejecuta `01_esquema.sql` (DDL) y `02_datos_sistema.sql` (solo lo que el backend necesita para operar) | Ejecutarlo **una sola vez** contra RDS (paso 7) |
| `Backend/scripts/bd/01_esquema.sql` | DDL del esquema `"KomainOS"`: 53 tablas, 96 claves foráneas, 32 enumerados | Lo ejecuta `instalar_base.sql` |
| `Backend/src/main/resources/application-prod.yml` | Perfil `prod` que la imagen activa: conexión a RDS, apagado ordenado, IP real detrás de un balanceador, Swagger desactivado | Nada |
| `iniciar.sh` | Arranca o reinicia el contenedor con las opciones correctas | Se crea en el paso 10 |
| `docker-compose.yml` | Alternativa a `iniciar.sh` | Opcional: Amazon Linux 2023 no trae el complemento `compose` |
| `base-temporal.env` e `iniciar-base-temporal.sh` | Base PostgreSQL temporal mientras no exista RDS | Opcional: se crean en la sección 18 y se borran al pasar a RDS |

No se usan en producción: `03_datos_prueba.sql` y `05_datos_prueba_umbrales.sql` (datos de demostración),
`Backend/.env.example` (desarrollo) ni los scripts de `Backend/scripts/bd/cambios/` (solo para bases creadas antes).

---

## 1. Requisitos en AWS

| Recurso | Recomendado | Mínimo |
|---|---|---|
| EC2 | Amazon Linux 2023, `t3.medium` (4 GiB), disco gp3 de 20 GiB | `t3.small` (2 GiB) con swap (paso 5). `t3.micro` no alcanza |
| RDS | PostgreSQL 16 o 17, `db.t4g.small`, 20 GiB gp3, copias automáticas de 7 días | PostgreSQL **15**, `db.t4g.micro` |
| Red | EC2 y RDS en la **misma VPC**; RDS **sin acceso público** | — |

**Una sola instancia del backend.** El limitador de inicios de sesión y las sesiones cerradas viven en la memoria del
proceso, y el ciclo de automatización se coordina con bloqueos de PostgreSQL pensados para un único backend: no
ejecute dos contenedores contra la misma base ni un grupo de autoescalado con más de una instancia.

## 2. Crear la instancia EC2

En **EC2 → Lanzar instancia**:

1. **AMI:** Amazon Linux 2023. **Tipo:** `t3.medium` (o `t3.small`). **Par de claves:** cree o elija uno y guarde el `.pem`.
2. **Configuración de red → Grupo de seguridad** (por ejemplo, `komainos-ec2`), reglas de entrada:

   | Tipo | Puerto | Origen |
   |---|---|---|
   | SSH | 22 | **Mi IP** |
   | HTTP | 80 | 0.0.0.0/0 |
   | HTTPS | 443 | 0.0.0.0/0 (solo si usará HTTPS, sección 15) |

   **No olvide la regla HTTP.** Si la instancia ya existe, agréguela en **EC2 → Grupos de seguridad → (el grupo de la
   instancia) → Editar reglas de entrada → Agregar regla → HTTP**. Sin ella todo funciona dentro del servidor, pero el
   navegador se queda esperando hasta agotar el tiempo (fue el único fallo de la instalación de referencia).

3. **Almacenamiento:** 20 GiB gp3.
4. Lance la instancia y asígnele una **IP elástica** (**EC2 → Direcciones IP elásticas → Asignar** y luego
   **Asociar** a la instancia). Sin ella la IP pública cambia al detener la instancia, y el panel deja de aceptar
   peticiones porque `KOMAINOS_CORS_ORIGENES` (paso 8) apunta a la IP anterior.

## 3. Crear la base de datos RDS

En **RDS → Crear base de datos**:

1. **Método:** Creación estándar. **Motor:** PostgreSQL, versión 17 o 18 (15 como mínimo).
2. **Plantilla:** Capa gratuita o Desarrollo y pruebas.
3. **Identificador de la instancia:** `komainos-db`. **Usuario maestro:** por ejemplo `komainos_admin` (anótelo: se
   usa en los pasos 7 y 8). **Administración de credenciales:** *Autoadministrado*; escriba una contraseña y guárdela.
4. **Clase:** `db.t4g.micro` o `db.t4g.small`. **Almacenamiento:** 20 GiB gp3.
5. **Conectividad → Recurso de computación:** *Conectarse a un recurso de computación EC2* y elija la instancia del
   paso 2. AWS crea los grupos de seguridad que permiten el puerto 5432 **solo desde esa instancia**.
   **Acceso público:** No.
6. **Configuración adicional → Nombre de la base de datos inicial:** `komainos` (en minúsculas).
   **Copias de seguridad automáticas:** 7 días.
7. Cree la base. Cuando el estado sea **Disponible**, copie el **Punto de enlace** (*endpoint*) de la pestaña
   **Conectividad y seguridad**, por ejemplo `komainos-db.abc123xyz.us-east-1.rds.amazonaws.com`.

> RDS para PostgreSQL 15 o superior exige conexiones cifradas (`rds.force_ssl=1`). No hay que cambiar nada: la
> imagen trae los certificados de RDS y la URL del paso 8 ya usa TLS.

## 4. Conectarse a la instancia EC2

Desde PowerShell en su PC (la primera vez, quite los permisos heredados de la llave; si no, el `ssh` de Windows la
rechaza con `UNPROTECTED PRIVATE KEY FILE`):

```powershell
icacls C:\ruta\tu-llave.pem /inheritance:r
icacls C:\ruta\tu-llave.pem /grant:r "$($env:USERNAME):(R)"
ssh -i C:\ruta\tu-llave.pem ec2-user@<IP-ELASTICA>
```

Desde Git Bash el mismo `ssh -i` funciona sin el paso de `icacls`. También puede usar
**EC2 → Conectar → EC2 Instance Connect** desde el navegador.

## 5. Preparar el servidor

```bash
sudo dnf update -y
sudo dnf install -y docker git postgresql15 nano
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
exit
```

Vuelva a conectarse (paso 4) para que su usuario tome el grupo `docker` y compruebe que funciona sin `sudo`:

```bash
id -nG                                   # debe incluir docker
docker info --format '{{.ServerVersion}}'  # 25.x en Amazon Linux 2023
```

El paquete `docker` de Amazon Linux 2023 ya trae `buildx`; no trae `docker compose` (por eso esta guía usa
`docker run` con `iniciar.sh`).

**Solo si la instancia tiene menos de 4 GiB** (`free -h`), agregue 2 GiB de swap. Sin ella, la compilación de la
imagen puede terminar con `Killed`:

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
```

## 6. Descargar el código

```bash
git clone https://github.com/sheshe0529/KomainOS.git ~/komainos
cd ~/komainos
ls Dockerfile .dockerignore .env.produccion.example Backend/scripts/bd/instalar_base.sql
```

## 7. Base de datos: crear el esquema en RDS

```bash
export RDS_HOST=<endpoint-de-rds>        # el del paso 3
export RDS_USER=<usuario-maestro>        # el del paso 3, por ejemplo komainos_admin

# Comprobar la conexión (pide la contraseña del usuario maestro)
psql "host=$RDS_HOST port=5432 dbname=komainos user=$RDS_USER sslmode=require" -c "select version();"
```

Con RDS en PostgreSQL 16 o superior, `psql` 15 avisa `psql major version 15, server major version 18`: es normal y
no afecta a la instalación, que solo usa SQL estándar.

Si responde `database "komainos" does not exist` (no se definió el nombre inicial en el paso 3), créela:

```bash
psql "host=$RDS_HOST port=5432 dbname=postgres user=$RDS_USER sslmode=require" -c "CREATE DATABASE komainos;"
```

Instale el esquema y los datos del sistema (desde la carpeta de los scripts, porque se incluyen entre sí):

```bash
cd ~/komainos/Backend/scripts/bd
psql "host=$RDS_HOST port=5432 dbname=komainos user=$RDS_USER sslmode=require" -f instalar_base.sql
cd ~/komainos
```

Debe terminar mostrando **53** tablas y `Base de KomainOS instalada`. Si el esquema ya existe, el script no hace
nada. Solo deja los datos sin los que el backend no funciona: los parámetros del sistema (1 fila), los factores de
ciclo (4) y las 21 variables de salud que informan los scripts de análisis. No carga umbrales, listas de referencia
ni datos de demostración: se registran en el panel (paso 12). El administrador tampoco se crea aquí: lo crea el
backend en su primer arranque (paso 10).

> **¿Todavía no tiene RDS?** Siga con los pasos 8 y 9 y luego use la sección 18: deja la aplicación funcionando con
> una base PostgreSQL temporal en la misma instancia hasta que RDS esté listo.

## 8. Crear el archivo `.env`

Cree el archivo y genere los secretos en el propio servidor (no aparecen en pantalla). Cambie `<IP-ELASTICA>` por la
IP o la URL con la que se abrirá el panel:

```bash
cd ~/komainos
cp .env.produccion.example .env && chmod 600 .env
JWT=$(openssl rand -base64 32); LLAVE=$(openssl rand -base64 32); ADMIN=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-20)
sed -i "s|^KOMAINOS_JWT_SECRETO=.*|KOMAINOS_JWT_SECRETO=$JWT|; s|^KOMAINOS_CREDENCIALES_LLAVE=.*|KOMAINOS_CREDENCIALES_LLAVE=$LLAVE|; s|^KOMAINOS_ADMIN_CLAVE_INICIAL=.*|KOMAINOS_ADMIN_CLAVE_INICIAL=$ADMIN|; s|^KOMAINOS_CORS_ORIGENES=.*|KOMAINOS_CORS_ORIGENES=http://<IP-ELASTICA>|" .env
unset JWT LLAVE ADMIN
```

Complete los datos de RDS con las variables `RDS_HOST` y `RDS_USER` del paso 7 (si abrió otra sesión, vuelva a
definirlas). El primer comando escribe el endpoint y el usuario; el segundo pide la contraseña sin mostrarla, la
prueba contra RDS y solo la guarda si es correcta (admite caracteres como `&`, `)`, `=` o `;`, que un `sed` rompería):

```bash
sed -i "s|^\(KOMAINOS_DB_URL=jdbc:postgresql://\)[^:/]*|\1$RDS_HOST|; s|^KOMAINOS_DB_USUARIO=.*|KOMAINOS_DB_USUARIO=$RDS_USER|" .env

read -rsp "Contraseña de $RDS_USER: " C; echo
PGPASSWORD="$C" psql "host=$RDS_HOST dbname=komainos user=$RDS_USER sslmode=require" -Atc "select 'clave correcta'" && (umask 077; C="$C" awk '/^KOMAINOS_DB_CLAVE=/{print "KOMAINOS_DB_CLAVE=" ENVIRON["C"]; next} {print}' .env > .env.nuevo && mv .env.nuevo .env)
unset C
```

Si todavía no tiene RDS, omita este bloque y siga la sección 18. Para revisar o corregir a mano cualquier valor:
`nano .env` (guardar: Ctrl+O, Enter; salir: Ctrl+X).

Para consultar después la clave inicial del administrador o respaldar la llave de credenciales:

```bash
grep -E '^KOMAINOS_(ADMIN_CLAVE_INICIAL|CREDENCIALES_LLAVE)=' ~/komainos/.env
```

Variables que deben quedar completas (una por línea, sin comillas y sin comentarios al final de la línea):

| Variable | Valor |
|---|---|
| `KOMAINOS_DB_URL` | `jdbc:postgresql://<endpoint-de-rds>:5432/komainos?sslmode=verify-full&sslrootcert=/opt/komainos/rds-ca/global-bundle.pem` (solo cambia el endpoint) |
| `KOMAINOS_DB_USUARIO` | El usuario maestro del paso 3, por ejemplo `komainos_admin` |
| `KOMAINOS_DB_CLAVE` | La contraseña del usuario maestro, tal cual (sin comillas ni escapes) |
| `KOMAINOS_JWT_SECRETO` | El primer `openssl rand -base64 32` |
| `KOMAINOS_CREDENCIALES_LLAVE` | El segundo `openssl rand -base64 32` |
| `KOMAINOS_ADMIN_CLAVE_INICIAL` | Contraseña del primer usuario `admin` (entre 8 y 72 caracteres) |
| `KOMAINOS_CORS_ORIGENES` | La URL con la que se abrirá el panel, sin barra final: `http://<IP-ELASTICA>` o `https://komainos.midominio.com` |

Deje `KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO=false` hasta terminar la sección 12. El resto de la plantilla tiene
valores por omisión razonables.

> **Respalde `KOMAINOS_CREDENCIALES_LLAVE` fuera del servidor** (por ejemplo, en AWS Secrets Manager o en un gestor
> de contraseñas). Cifra las cuentas de servicio y credenciales: si se pierde o cambia, no se pueden descifrar.
> Cambiar `KOMAINOS_JWT_SECRETO` solo cierra las sesiones abiertas.

## 9. Construir la imagen

```bash
cd ~/komainos
docker build -t komainos:latest .
```

En una `t3.small` con swap tarda unos 2 minutos la primera vez (descarga imágenes y dependencias); las siguientes,
menos. El aviso `useradd warning: komainos's uid 10001 is greater than SYS_UID_MAX` es normal. Compruebe la imagen y
libere espacio:

```bash
docker images komainos                                               # komainos:latest, unos 470 MB
docker run --rm --entrypoint ansible-playbook komainos:latest --version | head -1   # ansible-playbook [core 2.21.x]
docker image prune -f
```

## 10. Arrancar el contenedor

Cree el script de arranque, que también sirve para reiniciar después de cambiar `.env`:

```bash
cat > ~/komainos/iniciar.sh <<'EOF'
#!/bin/bash
# Arranca (o vuelve a crear) el contenedor de KomainOS con las variables de ~/komainos/.env
docker rm -f komainos 2>/dev/null
docker run -d --name komainos --restart unless-stopped \
  --env-file ~/komainos/.env \
  -p 80:8080 \
  -v komainos-ssh:/home/komainos/.ssh \
  --log-opt max-size=20m --log-opt max-file=5 \
  komainos:latest
EOF
chmod +x ~/komainos/iniciar.sh
~/komainos/iniciar.sh
```

`--restart unless-stopped` hace que el contenedor vuelva a arrancar solo si se reinicia la instancia. El volumen
`komainos-ssh` guarda las claves de host de los servidores gestionados entre versiones.

## 11. Verificar

```bash
docker logs -f komainos      # espere "Started KomainosApplication" y "Administrador inicial 'admin' creado"; salir: Ctrl+C
docker ps                    # la columna STATUS debe decir (healthy) tras uno o dos minutos
curl -s localhost/actuator/health    # {"status":"UP"}
```

Compruebe también desde su PC (PowerShell), que es lo que valida el grupo de seguridad:

```powershell
curl.exe -m 10 http://<IP-ELASTICA>/actuator/health    # {"status":"UP"}
```

Si responde dentro del servidor pero desde su PC se agota el tiempo, falta la regla HTTP del paso 2. Luego abra
`http://<IP-ELASTICA>` en el navegador: debe aparecer el inicio de sesión de KomainOS.

## 12. Primera puesta en marcha

1. Inicie sesión con `admin` y la clave de `KOMAINOS_ADMIN_CLAVE_INICIAL` (paso 8 muestra cómo consultarla).
   Cámbiela en **Configuración → Mi cuenta**.
2. Borre el valor de `KOMAINOS_ADMIN_CLAVE_INICIAL` en `.env` (ya no se usa) y ejecute `~/komainos/iniciar.sh`.
3. **Automatización → Sala de operaciones:** el diagnóstico debe decir «Ejecución con Ansible», con un controlador
   Linux y la versión de `ansible-core`. El único aviso esperado es el del proceso automático desactivado.
4. Configure catálogos (entornos, criticidades, sistemas operativos), **Cuentas de servicio**,
   **Configuración → Políticas de cambio** (quién aprueba en cada entorno) y **Umbrales** (con sus listas de
   referencia). Sin umbrales el análisis funciona, pero nunca activa tareas. Si prefiere partir de unos genéricos y
   ajustarlos después, ejecute una vez `04_umbrales_variables.sql` como en el paso 7 (`-f 04_umbrales_variables.sql`).
5. Prepare los servidores gestionados (sección 13), regístrelos en **Inventario** y use **Probar acceso** en cada
   ficha, con la cuenta de evaluación y con la de mantenimiento.
6. **Scripts:** registre el script de análisis de cada sistema operativo (los de referencia están en
   `Documentos/Scripts/`) y use **Probar en un servidor**: debe reconocer todas las métricas. Luego **Revisar ahora**
   en la ficha del servidor.
7. Haga un **Lanzamiento inmediato** sobre un servidor de pruebas y sígalo hasta el cierre de la orden.
8. Con todo correcto, active el proceso automático:

   ```bash
   sed -i 's/^KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO=.*/KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO=true/' ~/komainos/.env
   ~/komainos/iniciar.sh
   ```

## 13. Servidores gestionados

- **Red:** el grupo de seguridad (o el firewall) de cada servidor debe aceptar desde la instancia EC2 el puerto
  **22** (Linux) o el **5986** (Windows). Si están en AWS, use como origen el grupo `komainos-ec2`.
- **Linux (SSH):** la verificación de la clave de host está activa. Registre cada servidor y compare la huella con la
  del propio servidor (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`):

  ```bash
  docker exec komainos sh -c 'ssh-keyscan -H <IP-del-servidor> >> ~/.ssh/known_hosts'
  docker exec komainos sh -c 'ssh-keygen -lf ~/.ssh/known_hosts'
  ```

- **Windows (WinRM):** escucha HTTPS en 5986, transporte NTLM. Si el certificado lo emite una CA interna, agregue esa
  CA a la imagen; `KOMAINOS_ANSIBLE_WINRM_VALIDAR_CERTIFICADO=false` es solo para laboratorio.
- **Cuentas de servicio:** usuario con contraseña o llave SSH (sin frase de contraseña) y, aparte, la elevación que
  aplicará Ansible: ninguna, `sudo` (con la contraseña del usuario o NOPASSWD), `su` con la contraseña de root, o
  `runas` en Windows. El formulario muestra al final la configuración de Ansible que resulta. Con `sudo`, sudoers debe
  permitir una shell como root, no una lista de comandos. Para el mantenimiento use una cuenta que llegue a root o a
  Administradores; para la evaluación basta una sin elevación. **Probar acceso** muestra con qué usuario y privilegios
  se ejecutarán los scripts. Detalle en
  [Backend/AUTOMATIZACION.md](Backend/AUTOMATIZACION.md#puesta-en-marcha-con-servidores-reales).

## 14. Actualizar a una versión nueva

Hágalo cuando la **Sala de operaciones** no tenga mantenimientos en ejecución: al arrancar, lo que el reinicio dejó en
curso se registra como fallido (las tareas, con su incidencia) y nunca se reintenta.

```bash
cd ~/komainos
git pull
docker build -t komainos:latest .
./iniciar.sh
docker image prune -f
```

`.env` no se toca (git lo ignora). Si la versión nueva trae scripts en `Backend/scripts/bd/cambios/`, tome antes una
instantánea de RDS (**RDS → Instantáneas → Tomar instantánea**) y aplíquelos en orden de fecha con `psql -f`.

## 15. HTTPS (recomendado en producción)

Con `http://IP` las contraseñas y el token viajan sin cifrar: sirve para probar, no para operar. Dos opciones:

**A. Balanceador de AWS.** Un *Application Load Balancer* con un certificado de AWS Certificate Manager escucha en
443 y envía a la instancia por HTTP al puerto 80; comprobación de estado `GET /actuator/health`. Restrinja el puerto
80 del grupo `komainos-ec2` al grupo del balanceador y cambie `KOMAINOS_CORS_ORIGENES` a `https://<su-dominio>`.

**B. Caddy en la misma instancia** (necesita un dominio cuyo registro DNS apunte a la IP elástica y el puerto 443
abierto). Caddy obtiene y renueva el certificado solo:

```bash
docker network create komainos-red 2>/dev/null || true    # puede existir ya si usó la sección 18
# En iniciar.sh: reemplace la línea "-p 80:8080 \" por "--network komainos-red \" y ejecútelo
docker run -d --name caddy --restart unless-stopped --network komainos-red \
  -p 80:80 -p 443:443 -v caddy-datos:/data \
  caddy:2 caddy reverse-proxy --from komainos.midominio.com --to komainos:8080
```

Luego `KOMAINOS_CORS_ORIGENES=https://komainos.midominio.com` en `.env` y `~/komainos/iniciar.sh`. En ambas opciones
el perfil `prod` toma la IP real del cliente de `X-Forwarded-For` (solo si la petición llega desde una dirección
privada, como el balanceador o Caddy); el limitador de inicios de sesión depende de ella.

## 16. Comandos útiles

| Acción | Comando |
|---|---|
| Ver el registro en vivo | `docker logs -f komainos` |
| Últimas 200 líneas | `docker logs --tail 200 komainos` |
| Estado y salud | `docker ps` |
| Aplicar cambios de `.env` | `~/komainos/iniciar.sh` (`docker restart` no vuelve a leer `.env`) |
| Detener / arrancar | `docker stop komainos` / `docker start komainos` |
| Uso de disco de Docker | `docker system df` |
| Consola de la base | `psql "host=$RDS_HOST dbname=komainos user=$RDS_USER sslmode=require"` |

**Respaldo lógico de la base** (antes de aplicar un script de `cambios/`, por ejemplo). `pg_dump` debe ser de la misma
versión mayor que RDS o posterior, y el `psql` 15 de Amazon Linux no lo es; un contenedor de PostgreSQL lo resuelve:

```bash
mkdir -p ~/respaldos && chmod 700 ~/respaldos
PGPASSWORD="$(grep '^KOMAINOS_DB_CLAVE=' ~/komainos/.env | cut -d= -f2-)" docker run --rm --user "$(id -u):$(id -g)" \
  -e PGPASSWORD -v ~/respaldos:/r postgres:18-alpine \
  pg_dump "host=$RDS_HOST dbname=komainos user=$RDS_USER sslmode=require" -Fc -f /r/komainos_$(date +%Y%m%d_%H%M).dump
chmod 600 ~/respaldos/*.dump
```

Use la imagen `postgres` de la versión de su RDS (aquí, 18). Para restaurar: `pg_restore` con la misma imagen.

## 17. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| El navegador no carga y `curl.exe` desde su PC agota el tiempo, aunque `curl -s localhost/actuator/health` responde `UP` en el servidor | Falta la regla de entrada HTTP (80) en el grupo de seguridad de la instancia (paso 2) |
| `psql` o el backend se quedan esperando la base (`timed out`) | El grupo de seguridad de RDS no acepta a la instancia, o están en VPC distintas (paso 3.5) |
| `password authentication failed` | Usuario o contraseña de RDS incorrectos en `.env`. Pruébelos con `psql` (paso 7) y vuelva a guardarlos con el bloque del paso 8; si no recuerda la contraseña, cámbiela en **RDS → Modificar → Contraseña maestra** |
| `The server does not support SSL` o error de certificado | `KOMAINOS_DB_URL` sin `sslmode=verify-full&sslrootcert=...` o con otro endpoint |
| `Schema-validation: missing table` | No se ejecutó `instalar_base.sql`, o la URL apunta a otra base |
| `Could not resolve placeholder 'KOMAINOS_…'` | Falta esa variable en `.env` |
| La compilación termina con `Killed` o el código 137 | Falta memoria: agregue la swap del paso 5 |
| `no space left on device` | `docker system prune -f` o amplíe el volumen EBS |
| El panel responde `403 Invalid CORS request` | `KOMAINOS_CORS_ORIGENES` no coincide exactamente con la URL del navegador (esquema, IP o dominio, sin barra final) |
| No se puede iniciar sesión con `admin` | `KOMAINOS_ADMIN_CLAVE_INICIAL` estaba vacía en el primer arranque: complétela y ejecute `iniciar.sh` |
| Todas las operaciones remotas fallan con «Configuración del controlador» | El contenedor no es el de este `Dockerfile` o no encuentra `ansible-playbook` |
| Error de comunicación en **Probar acceso** | Puerto 22/5986 cerrado entre la instancia y el servidor gestionado |
| `Host key verification failed` | Falta registrar ese servidor en `known_hosts` (sección 13) |

## 18. Funcionar sin RDS mientras tanto (opcional)

Si la instancia ya está lista pero RDS todavía no, la aplicación puede funcionar con un PostgreSQL 17 en un contenedor
de la misma instancia. Sirve para comprobar la instalación y conocer el panel. **No cargue datos reales:** esta base no
tiene copias de seguridad y no se traslada a RDS. Haga antes los pasos 1 a 6, 8 y 9 (en el paso 8 omita el bloque de
los datos de RDS).

**18.1. Base temporal.** La clave se genera en el servidor y queda solo en `base-temporal.env`:

```bash
cd ~/komainos
CLAVE=$(openssl rand -hex 16)
printf "POSTGRES_DB=komainos\nPOSTGRES_PASSWORD=%s\nKOMAINOS_DB_URL=jdbc:postgresql://komainos-pg-temporal:5432/komainos\nKOMAINOS_DB_USUARIO=postgres\nKOMAINOS_DB_CLAVE=%s\n" "$CLAVE" "$CLAVE" > base-temporal.env
chmod 600 base-temporal.env; unset CLAVE
docker network create komainos-red
docker run -d --name komainos-pg-temporal --restart unless-stopped --network komainos-red \
  --env-file base-temporal.env -p 127.0.0.1:5432:5432 \
  -v komainos-pg-temporal:/var/lib/postgresql/data postgres:17
until docker exec komainos-pg-temporal pg_isready -U postgres -d komainos; do sleep 2; done
```

**18.2. Esquema** (el mismo script del paso 7, contra la base temporal):

```bash
cd ~/komainos/Backend/scripts/bd
PGPASSWORD=$(grep '^POSTGRES_PASSWORD=' ~/komainos/base-temporal.env | cut -d= -f2-) \
  psql "host=127.0.0.1 port=5432 dbname=komainos user=postgres sslmode=disable" -f instalar_base.sql
cd ~/komainos
```

**18.3. Script de arranque temporal.** Igual que `iniciar.sh`, pero con `base-temporal.env` encima de `.env` (el
segundo archivo tiene prioridad) y en la red de la base:

```bash
cat > ~/komainos/iniciar-base-temporal.sh <<'EOF'
#!/bin/bash
# Solo mientras no exista RDS: KomainOS contra la base PostgreSQL temporal (contenedor komainos-pg-temporal).
# Con RDS listo se usa iniciar.sh y se elimina la base temporal (DESPLIEGUE.md, sección 18.4).
docker rm -f komainos 2>/dev/null
docker run -d --name komainos --restart unless-stopped \
  --env-file ~/komainos/.env --env-file ~/komainos/base-temporal.env \
  --network komainos-red \
  -p 80:8080 \
  -v komainos-ssh:/home/komainos/.ssh \
  --log-opt max-size=20m --log-opt max-file=5 \
  komainos:latest
EOF
chmod +x ~/komainos/iniciar-base-temporal.sh
~/komainos/iniciar-base-temporal.sh
```

Verifique como en el paso 11. Mientras use la base temporal, aplique cualquier cambio de `.env` con
`iniciar-base-temporal.sh`, no con `iniciar.sh`.

**18.4. Pasar a RDS** cuando esté creada (paso 3):

1. Paso 7 completo: comprobar la conexión e instalar `instalar_base.sql` en RDS (53 tablas).
2. Paso 8, solo el bloque de los datos de RDS (endpoint, usuario y contraseña en `.env`).
3. Arrancar contra RDS y eliminar la base temporal:

```bash
~/komainos/iniciar.sh           # el contenedor pasa a usar RDS
sleep 40; docker ps             # komainos (healthy)
docker logs komainos 2>&1 | grep "Administrador inicial"   # 'admin' creado
docker rm -f komainos-pg-temporal
docker volume rm komainos-pg-temporal
docker image rm postgres:17     # libera unos 450 MB
rm ~/komainos/base-temporal.env ~/komainos/iniciar-base-temporal.sh
```

En RDS el administrador se crea de nuevo con la misma `KOMAINOS_ADMIN_CLAVE_INICIAL`; lo que haya registrado en la base
temporal no se traslada. Después siga con la sección 12. La red `komainos-red` puede quedarse: la usa la opción B de la
sección 15.

## 19. Si la instancia se reinicia o cambia la IP pública

Sin IP elástica, AWS asigna otra IP pública cada vez que la instancia se detiene y vuelve a arrancar (por ejemplo, al
terminar y reanudar una sesión de laboratorio). Un reinicio desde el sistema operativo la conserva. Con una IP
elástica asociada, la IP ya no cambia y esta sección solo sirve para comprobar el arranque.

**No cambia y no hay que tocar:**

- El endpoint de RDS. Es un nombre DNS y `KOMAINOS_DB_URL` sigue sirviendo, aunque RDS también se haya reiniciado.
- La IP privada de la instancia, los grupos de seguridad (la regla HTTP es para `0.0.0.0/0` y la de RDS apunta al
  grupo de la instancia, no a una IP), los datos y la llave de credenciales.
- El arranque. Docker, la swap y el contenedor `komainos` arrancan solos. Si RDS tarda más que la instancia, el
  contenedor se reinicia hasta que la base responde (`docker inspect -f '{{.RestartCount}}' komainos`).

**Sí cambia (con la IP nueva):**

1. `KOMAINOS_CORS_ORIGENES` en `.env`, y luego reiniciar el contenedor:

   ```bash
   sed -i 's|^KOMAINOS_CORS_ORIGENES=.*|KOMAINOS_CORS_ORIGENES=http://<IP-NUEVA>|' ~/komainos/.env
   ~/komainos/iniciar.sh
   ```

   El panel funciona aunque no se haga, porque lo sirve el mismo contenedor y el navegador no aplica CORS a su propio
   origen. Aun así, conviene quitar la IP anterior: AWS puede asignarla a otra cuenta y seguiría siendo un origen
   permitido.
2. La IP en el comando `ssh` y en la dirección del navegador. En la primera conexión SSH, `ssh` pide confirmar la
   clave del host: debe tener la misma huella que antes (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` en el
   servidor).
3. Los cortafuegos de los servidores gestionados que solo aceptan SSH o WinRM desde la IP pública de la instancia.
   Los que están en la misma VPC ven la IP privada, que no cambia.
4. Con un dominio (sección 15), el registro DNS `A`.

**Comprobación tras el arranque:**

```bash
docker ps                                  # komainos (healthy)
curl -s localhost/actuator/health          # {"status":"UP"}
grep '^KOMAINOS_CORS_ORIGENES=' ~/komainos/.env
```

Y desde su PC: `curl.exe -m 10 http://<IP>/actuator/health`.
