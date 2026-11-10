# Despliegue de KomainOS en AWS (EC2 + RDS)

KomainOS se despliega como **un contenedor Docker en una instancia EC2** y una base **Amazon RDS for PostgreSQL**.
La imagen trae el panel compilado, la API de Spring Boot y el controlador Ansible que ejecuta los scripts en los
servidores gestionados. No hay modo simulado: cada análisis, prueba de acceso y tarea se ejecuta de verdad (DEC-56).

```
Usuarios ──HTTPS──▶ ALB o proxy ──HTTP──▶ EC2: contenedor komainos (panel + API + Ansible)
                                              │                 │
                                   TLS 5432   ▼                 ▼  SSH 22 / WinRM 5986
                                   Amazon RDS PostgreSQL     Servidores gestionados
```

| Archivo | Para qué |
|---|---|
| `Dockerfile` | Imagen en tres etapas: panel (Node 22), backend (Maven + JDK 21) y ejecución (JRE 21 + `ansible-core` 2.18 + `pywinrm` + `sshpass`) |
| `docker-compose.yml` | El servicio en EC2: variables, puerto, reinicio, volumen de `known_hosts` y rotación de registros |
| `.env.produccion.example` | Plantilla de las variables; se copia a `.env` en el servidor |
| `Backend/scripts/bd/instalar_base.sql` | Crea la base: DDL (`01_esquema.sql`), datos del sistema (`02`) y umbrales iniciales (`04`) |
| `Backend/src/main/resources/application-prod.yml` | Perfil `prod`, que la imagen activa: pool para RDS, apagado ordenado, cabeceras del balanceador, Swagger desactivado |

**Una sola instancia.** El limitador de inicios de sesión y las sesiones cerradas viven en la memoria del proceso, y
el ciclo de automatización se coordina con bloqueos de PostgreSQL pensados para un único backend: no ponga dos
contenedores contra la misma base ni un grupo de autoescalado con más de una instancia.

## 1. Red y recursos

- **VPC:** EC2 y RDS en la misma VPC. RDS sin acceso público.
- **EC2:** Amazon Linux 2023 o Ubuntu 24.04, `t3.medium` (4 GiB) recomendada; `t3.small` alcanza con
  `KOMAINOS_AUTOMATIZACION_MAX_CONEXIONES=4`. La JVM usa como máximo la mitad de la memoria y cada operación remota
  en curso es un proceso `ansible-playbook` aparte. 20 GiB de disco.
- **RDS:** PostgreSQL **15 o superior** (16 o 17 recomendado), `db.t4g.small` o mayor, copias automáticas con al
  menos 7 días de retención.
- **Grupos de seguridad:**

  | Grupo | Entrada | Salida |
  |---|---|---|
  | `komainos-ec2` | 80/443 desde Internet o desde el grupo del ALB; 22 solo desde la IP del administrador | 5432 hacia `komainos-rds`; 22 y 5986 hacia los servidores gestionados; 443 hacia Internet (imágenes y paquetes) |
  | `komainos-rds` | 5432 solo desde `komainos-ec2` | — |
  | Servidores gestionados | 22 (Linux) o 5986 (Windows) desde `komainos-ec2` | — |

## 2. Base de datos

1. Cree la instancia RDS con el nombre de base inicial **`komainos`** (en minúsculas: evita comillas en las
   herramientas). El esquema sigue llamándose `"KomainOS"`. El backend trabaja en UTC, como RDS por omisión.
2. Desde la instancia EC2, que está dentro de la VPC, instale el cliente y cree el esquema:

   ```bash
   sudo dnf install -y postgresql16 git        # Amazon Linux 2023 (Ubuntu: sudo apt install postgresql-client git)
   git clone <url-del-repositorio> komainos && cd komainos/Backend/scripts/bd
   psql "host=<endpoint-rds> port=5432 dbname=komainos user=<usuario> sslmode=require" -f instalar_base.sql
   ```

   `instalar_base.sql` ejecuta `01_esquema.sql` y `02_datos_sistema.sql` en una sola transacción y después
   `04_umbrales_variables.sql`. Termina mostrando `53` tablas. Si el esquema ya existe, no hace nada. No cargue
   `03_datos_prueba.sql` ni `05_datos_prueba_umbrales.sql`: son datos de demostración.
3. **TLS.** RDS para PostgreSQL 15 o superior exige conexiones cifradas por omisión (`rds.force_ssl=1`). La imagen
   incluye el paquete de certificados de RDS en `/opt/komainos/rds-ca/global-bundle.pem`, así que la URL usa
   `sslmode=verify-full`, que además comprueba que el servidor es el endpoint de RDS:

   ```
   jdbc:postgresql://<endpoint-rds>:5432/komainos?sslmode=verify-full&sslrootcert=/opt/komainos/rds-ca/global-bundle.pem
   ```

El usuario de la aplicación puede ser el maestro de RDS. Si prefiere otro, necesita leer y escribir en todas las
tablas y secuencias del esquema `"KomainOS"`; el backend no crea ni modifica tablas (Hibernate solo las valida).

## 3. Contenedor

En la instancia EC2, con Docker y el complemento `compose` instalados:

```bash
cd ~/komainos
cp .env.produccion.example .env
chmod 600 .env
openssl rand -base64 32      # una vez para KOMAINOS_JWT_SECRETO y otra para KOMAINOS_CREDENCIALES_LLAVE
nano .env                    # completar base, secretos, clave inicial del administrador y URL pública
docker compose up -d --build
docker compose ps            # «healthy» tras uno o dos minutos
docker compose logs -f komainos
```

Variables imprescindibles (detalle y valores por omisión en `.env.produccion.example`):

| Variable | Valor |
|---|---|
| `KOMAINOS_DB_URL`, `KOMAINOS_DB_USUARIO`, `KOMAINOS_DB_CLAVE` | Conexión a RDS (sección 2) |
| `KOMAINOS_JWT_SECRETO` | 32 bytes aleatorios en base64; cambiarlo cierra todas las sesiones |
| `KOMAINOS_CREDENCIALES_LLAVE` | 32 bytes aleatorios en base64. **Guárdela fuera del servidor** (por ejemplo, en AWS Secrets Manager): si se pierde o cambia, las cuentas de servicio registradas no se pueden descifrar |
| `KOMAINOS_ADMIN_CLAVE_INICIAL` | Crea el administrador `admin` solo si la base no tiene ninguno. Bórrela del `.env` después del primer inicio |
| `KOMAINOS_CORS_ORIGENES` | La URL pública exacta del panel, con esquema y sin barra final |

Sin alguna de las cinco primeras la aplicación no arranca, a propósito. El panel y la API comparten origen, así que
el navegador no necesita CORS; la variable cubre las peticiones que el navegador marca con `Origin`.

Si prefiere no compilar en la instancia, construya la imagen en otro equipo, súbala a Amazon ECR y cambie `build: .`
por `image: <cuenta>.dkr.ecr.<región>.amazonaws.com/komainos:<versión>` en `docker-compose.yml`.

## 4. HTTPS

El token viaja en cada petición: no publique el puerto 80 a Internet sin TLS.

- **Con balanceador (recomendado):** un Application Load Balancer con un certificado de AWS Certificate Manager
  escucha en 443 y envía a la instancia por HTTP al puerto 80. Comprobación de estado: `GET /actuator/health`,
  código 200. Restrinja el grupo `komainos-ec2` para que el puerto 80 solo acepte al ALB.
- **Sin balanceador:** un proxy inverso en la misma instancia (Caddy o Nginx con Let's Encrypt) delante del
  contenedor; publique el contenedor solo en `127.0.0.1` (`KOMAINOS_PUERTO_PUBLICO=127.0.0.1:8080`).

El perfil `prod` toma la IP del cliente y el esquema de `X-Forwarded-For` y `X-Forwarded-Proto` solo cuando la
petición llega desde una dirección privada (el ALB o el proxy). El limitador de inicios de sesión depende de esa IP.

## 5. Servidores gestionados

- **Linux (SSH).** La verificación de la clave de host está activa: registre cada servidor en el `known_hosts` del
  contenedor, que vive en el volumen `komainos-ssh` y sobrevive a las actualizaciones. Compare la huella con la que
  muestra el propio servidor (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`):

  ```bash
  docker compose exec komainos sh -c 'ssh-keyscan -H 10.0.1.25 >> ~/.ssh/known_hosts'
  docker compose exec komainos sh -c 'ssh-keygen -lf ~/.ssh/known_hosts'
  ```

- **Windows (WinRM).** Escucha HTTPS en 5986, transporte NTLM por omisión. Si el certificado lo emite una CA interna,
  agréguela a la imagen (una capa con `update-ca-certificates` y `REQUESTS_CA_BUNDLE`);
  `KOMAINOS_ANSIBLE_WINRM_VALIDAR_CERTIFICADO=false` es solo para laboratorio.
- **Cuentas de servicio** en **Configuración → Cuentas de servicio**: Linux, root o sudo sin contraseña (o la clave de
  `su` como credencial); Windows, miembro de Administradores. Detalle en
  [Backend/AUTOMATIZACION.md](Backend/AUTOMATIZACION.md#puesta-en-marcha-con-servidores-reales).

## 6. Primera puesta en marcha

Con `KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO=false`, como deja la plantilla:

1. Inicie sesión como `admin` con la clave inicial y cámbiela en **Configuración → Mi cuenta**. Quite
   `KOMAINOS_ADMIN_CLAVE_INICIAL` del `.env`.
2. **Automatización → Sala de operaciones:** el diagnóstico debe mostrar «Ejecución con Ansible», un controlador
   Linux y la versión de `ansible-core`. El único aviso esperado es el del proceso automático desactivado.
3. Catálogos, ventanas, cuentas de servicio y **Configuración → Políticas de cambio** (quién aprueba en cada entorno).
4. Registre los servidores y, en cada ficha, **Probar acceso** con la cuenta de evaluación y con la de mantenimiento.
5. **Scripts:** registre el análisis de `Documentos/Scripts/` para cada sistema operativo y use **Probar en un
   servidor**: la revisión debe reconocer todas las métricas. Después, **Revisar ahora** en la ficha del servidor.
6. Un **Lanzamiento inmediato** sobre un servidor de pruebas hasta el cierre de la orden.
7. Active el proceso automático: `KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO=true` en `.env` y
   `docker compose up -d`.

## 7. Actualizaciones

```bash
cd ~/komainos && git pull
# Si la versión trae scripts nuevos en Backend/scripts/bd/cambios: instantánea de RDS y aplicarlos en orden de fecha
docker compose up -d --build
```

Conserve `KOMAINOS_JWT_SECRETO` y, sobre todo, `KOMAINOS_CREDENCIALES_LLAVE` entre versiones. Actualice cuando la
**Sala de operaciones** no tenga mantenimientos en ejecución: al arrancar, lo que el reinicio dejó en curso se registra
como fallido (las tareas, con su incidencia) y nunca se reintenta, porque no se sabe si llegó a cambiar el servidor.

## 8. Problemas frecuentes

| Síntoma | Causa habitual |
|---|---|
| `Could not resolve placeholder 'KOMAINOS_…'` al arrancar | Falta esa variable en `.env` |
| `Connection timed out` hacia la base | Grupos de seguridad o RDS en otra VPC |
| `The server does not support SSL` o error de certificado | URL sin `sslmode` o con un `sslrootcert` distinto del de la imagen |
| `Schema-validation: missing table` | La base no se instaló con `instalar_base.sql`, o la URL apunta a otra base |
| El panel responde `403 Invalid CORS request` | `KOMAINOS_CORS_ORIGENES` no coincide exactamente con la URL que usa el navegador |
| Todas las operaciones remotas fallan con «Configuración del controlador» | El contenedor no encuentra `ansible-playbook` (imagen distinta de la del `Dockerfile`) |
| Error de comunicación en **Probar acceso** | Grupo de seguridad o firewall del servidor gestionado (22/5986) |
| `Host key verification failed` | Falta el servidor en `known_hosts` (sección 5) |
