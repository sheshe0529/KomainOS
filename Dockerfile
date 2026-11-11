# KomainOS — imagen de despliegue (EC2 + Amazon RDS for PostgreSQL)
#
# Una sola imagen con el panel compilado, la API de Spring Boot y el controlador Ansible que ejecuta los scripts en
# los servidores (SSH para Linux, WinRM para Windows). El panel se sirve desde el mismo origen que la API.
#
#   docker build -t komainos:1.0 .
#   docker run -d --name komainos --env-file .env -p 80:8080 komainos:1.0
#
# Guía completa: DESPLIEGUE.md

ARG NODE_IMAGE=node:22-bookworm-slim
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-noble

# ---------------------------------------------------------------------------------------------------------------------
# 1. Panel (React + Vite)
# ---------------------------------------------------------------------------------------------------------------------
FROM ${NODE_IMAGE} AS panel
WORKDIR /panel
COPY Frontend/package.json Frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY Frontend/ ./
# Sin VITE_API_URL el panel llama a /api en el mismo origen que lo sirve
RUN npm run build

# ---------------------------------------------------------------------------------------------------------------------
# 2. Backend (Spring Boot), con el panel dentro del JAR
# ---------------------------------------------------------------------------------------------------------------------
FROM ${MAVEN_IMAGE} AS backend
WORKDIR /backend
COPY Backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY Backend/src ./src
COPY --from=panel /panel/dist ./src/main/resources/static
# Las pruebas necesitan PostgreSQL: corren en desarrollo y en integración continua, no al construir la imagen
RUN mvn -B -q package -DskipTests \
    && cp target/komainos-backend-*.jar /backend/komainos.jar

# ---------------------------------------------------------------------------------------------------------------------
# 3. Ejecución: JRE 21 + Ansible
# ---------------------------------------------------------------------------------------------------------------------
FROM ${JRE_IMAGE}

# ansible-core 2.21 (controlador con Python 3.12, el de Ubuntu 24.04) reconoce la solicitud de contraseña de sudo-rs,
# el sudo de Ubuntu 25.10 en adelante: con 2.18, sudo con contraseña se quedaba esperándola (DEC-57). El playbook solo
# usa ansible.builtin.script y las elevaciones sudo, su y runas, que no necesitan Python en el servidor gestionado ni
# colecciones adicionales
ARG ANSIBLE_CORE_VERSION=2.21.*
ARG PYWINRM_VERSION=0.5.*

# python3-venv: Ansible en un entorno aislado (Ubuntu 24.04 no admite pip sobre el Python del sistema)
# openssh-client y sshpass: SSH con llave o con contraseña; curl: HEALTHCHECK y certificados de RDS
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv openssh-client sshpass curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && python3 -m venv /opt/ansible \
    && /opt/ansible/bin/pip install --no-cache-dir --upgrade pip \
    && /opt/ansible/bin/pip install --no-cache-dir "ansible-core==${ANSIBLE_CORE_VERSION}" "pywinrm==${PYWINRM_VERSION}" \
    && /opt/ansible/bin/ansible-playbook --version

# Certificados de Amazon RDS para conectar con sslmode=verify-full (DESPLIEGUE.md, sección «Base de datos»)
RUN mkdir -p /opt/komainos/rds-ca \
    && curl -fsSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
        -o /opt/komainos/rds-ca/global-bundle.pem \
    && chmod 644 /opt/komainos/rds-ca/global-bundle.pem

# Usuario sin privilegios. ~/.ssh guarda el known_hosts de los servidores gestionados: se monta como volumen para que
# las claves de host verificadas sobrevivan a una nueva versión de la imagen
RUN groupadd --system --gid 10001 komainos \
    && useradd --system --uid 10001 --gid komainos --create-home --home-dir /home/komainos --shell /usr/sbin/nologin komainos \
    && install -d -m 700 -o komainos -g komainos /home/komainos/.ssh

WORKDIR /opt/komainos
COPY --from=backend --chown=komainos:komainos /backend/komainos.jar ./komainos.jar

# La JVM usa como máximo la mitad de la memoria: el resto queda para los procesos de ansible-playbook (uno por
# operación remota en curso, hasta KOMAINOS_AUTOMATIZACION_MAX_CONEXIONES)
ENV PATH="/opt/ansible/bin:${PATH}" \
    TZ=UTC \
    LANG=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    KOMAINOS_PUERTO=8080 \
    KOMAINOS_ANSIBLE_EJECUTABLE=/opt/ansible/bin/ansible-playbook \
    KOMAINOS_ANSIBLE_SSH_KNOWN_HOSTS=/home/komainos/.ssh/known_hosts \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"

# Versión que publica /actuator/info. El despliegue automático (despliegue/desplegar.sh) las pasa al construir; se
# declaran al final para que cambiar de commit no invalide las capas anteriores (DEC-58)
ARG KOMAINOS_COMMIT=desconocido
ARG KOMAINOS_CONSTRUCCION=desconocida
ENV KOMAINOS_COMMIT=${KOMAINOS_COMMIT} \
    KOMAINOS_CONSTRUCCION=${KOMAINOS_CONSTRUCCION}

USER komainos
VOLUME ["/home/komainos/.ssh"]
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${KOMAINOS_PUERTO}/actuator/health" > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/opt/komainos/komainos.jar"]
