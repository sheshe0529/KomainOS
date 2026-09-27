package com.komainos.inventario.dominio;

import com.komainos.DatosPrueba;
import com.komainos.auditoria.dominio.ServicioAuditoria;
import com.komainos.inventario.dominio.ServicioServidor.DatosConfiguracion;
import com.komainos.inventario.infra.ConfiguracionServidorRepositorio;
import com.komainos.inventario.infra.GrupoMantenimientoRepositorio;
import com.komainos.inventario.infra.ServidorRepositorio;
import com.komainos.inventario.infra.SolicitudBajaRepositorio;
import com.komainos.seguridad.dominio.AlcanceUsuario;
import com.komainos.seguridad.dominio.Rol;
import com.komainos.seguridad.dominio.Usuario;
import com.komainos.seguridad.infra.UsuarioRepositorio;
import com.komainos.shared.dominio.Actor;
import com.komainos.shared.error.ConflictoException;
import com.komainos.shared.error.RecursoNoEncontradoException;
import com.komainos.shared.error.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inventario de servidores (RF09-RF11, RF17, RF72, RF73)")
class ServicioServidorTest {

    @Mock ServidorRepositorio servidores;
    @Mock ConfiguracionServidorRepositorio configuraciones;
    @Mock SolicitudBajaRepositorio solicitudesBaja;
    @Mock GrupoMantenimientoRepositorio grupos;
    @Mock UsuarioRepositorio usuarios;
    @Mock ServicioCatalogos catalogos;
    @Mock PuertoMantenimientos mantenimientos;
    @Mock ServicioAuditoria auditoria;
    @Mock ApplicationEventPublisher eventos;

    private ServicioServidor servicio;
    private final Actor admin = Actor.usuario(1);
    private final Usuario responsable = DatosPrueba.usuario(5, "m.herrera", Rol.RESPONSABLE);

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);
        servicio = new ServicioServidor(servidores, configuraciones, solicitudesBaja, grupos, usuarios, catalogos,
                mantenimientos, auditoria, eventos, reloj);
        lenient().when(catalogos.obtenerVersion(1)).thenReturn(DatosPrueba.version(1, "Ubuntu", "22.04"));
        lenient().when(catalogos.obtenerEntorno(1)).thenReturn(DatosPrueba.entorno(1, "Producción"));
        lenient().when(catalogos.obtenerCriticidad(1)).thenReturn(DatosPrueba.criticidad(1, "Alta", 1));
        lenient().when(usuarios.findById(5)).thenReturn(Optional.of(responsable));
    }

    private static DatosServidor datos(String hostname, String ip, int idResponsable) {
        return new DatosServidor(hostname, ip, "DC-Norte", null, null, null, null, 1, null, 1, 1, idResponsable, null);
    }

    @Nested
    @DisplayName("RF09: alta con detección de duplicados")
    class Alta {

        @Test
        @DisplayName("rechaza un hostname ya registrado")
        void rechazaHostnameDuplicado() {
            when(servidores.existsByHostnameIgnoreCase("srv-app-01")).thenReturn(true);
            assertThatThrownBy(() -> servicio.crear(datos("srv-app-01", "10.20.1.11", 5), admin))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("hostname srv-app-01");
            verify(servidores, never()).save(any());
        }

        @Test
        @DisplayName("rechaza una dirección IP ya registrada")
        void rechazaIpDuplicada() {
            when(servidores.existsByDireccionIp("10.20.1.11")).thenReturn(true);
            assertThatThrownBy(() -> servicio.crear(datos("srv-app-02", "10.20.1.11", 5), admin))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("10.20.1.11");
        }

        @Test
        @DisplayName("exige que el responsable tenga el rol Responsable")
        void exigeRolResponsable() {
            when(usuarios.findById(9)).thenReturn(Optional.of(DatosPrueba.usuario(9, "op", Rol.OPERADOR)));
            assertThatThrownBy(() -> servicio.crear(datos("srv-app-03", "10.20.1.13", 9), admin))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("rol Responsable");
        }

        @Test
        @DisplayName("HU06 CA3: nace pendiente de configuración y queda auditado")
        void nacePendienteDeConfiguracion() {
            Servidor creado = servicio.crear(datos(" srv-app-04 ", "10.20.1.14", 5), admin);

            assertThat(creado.getEstado()).isEqualTo(EstadoServidor.PENDIENTE_DE_CONFIGURACION);
            assertThat(creado.getHostname()).isEqualTo("srv-app-04");
            verify(servidores).save(creado);
            verify(auditoria).registrar(eq(admin), any());
        }
    }

    @Nested
    @DisplayName("RF17: configuración de mantenimiento")
    class Configuracion {

        @Test
        @DisplayName("HU11 CA2: copia las frecuencias de la criticidad y activa el servidor")
        void copiaFrecuenciasYActiva() {
            Servidor servidor = DatosPrueba.servidor(3, "srv-db-01", responsable);
            when(servidores.findConDetalleById(3)).thenReturn(Optional.of(servidor));
            when(configuraciones.findByServidorId(3)).thenReturn(Optional.empty());

            ConfiguracionServidor config = servicio.configurar(3,
                    new DatosConfiguracion(null, null, ModalidadPlanificacion.AUTOMATICA), admin);

            assertThat(config.getFrecuenciaRevisionDias()).isEqualTo(7);
            assertThat(config.getFrecuenciaMantenimientoDias()).isEqualTo(30);
            assertThat(servidor.getEstado()).isEqualTo(EstadoServidor.ACTIVO);
            verify(eventos).publishEvent(new ConfiguracionMantenimientoActualizada(3, null));
        }

        @Test
        @DisplayName("no permite configurar un servidor dado de baja")
        void rechazaServidorDadoDeBaja() {
            Servidor servidor = DatosPrueba.servidor(3, "srv-db-01", responsable);
            servidor.aplicarBaja();
            when(servidores.findConDetalleById(3)).thenReturn(Optional.of(servidor));

            assertThatThrownBy(() -> servicio.configurar(3,
                    new DatosConfiguracion(7, 30, ModalidadPlanificacion.AUTOMATICA), admin))
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    @Nested
    @DisplayName("RF72: baja de servidores")
    class Baja {

        @Test
        @DisplayName("sin mantenimiento en curso: retira pendientes, grupos y configuración y aplica la baja")
        void aplicaBaja() {
            Servidor servidor = DatosPrueba.servidor(3, "srv-db-01", responsable);
            GrupoMantenimiento grupo = GrupoMantenimiento.nuevo("Grupo DB", null);
            grupo.agregar(servidor, Instant.now());
            ConfiguracionServidor config = ConfiguracionServidor.nueva(servidor, 7, 30, ModalidadPlanificacion.AUTOMATICA);
            when(servidores.findConDetalleById(3)).thenReturn(Optional.of(servidor));
            when(usuarios.findById(1)).thenReturn(Optional.of(DatosPrueba.usuario(1, "admin", Rol.ADMINISTRADOR)));
            when(solicitudesBaja.save(any())).thenAnswer(i -> i.getArgument(0));
            when(mantenimientos.tieneMantenimientoEnCurso(3)).thenReturn(false);
            when(mantenimientos.retirarPendientes(eq(3), any(), anyString(), any())).thenReturn(2);
            when(grupos.findDelServidor(3)).thenReturn(List.of(grupo));
            when(configuraciones.findByServidorId(3)).thenReturn(Optional.of(config));

            var resultado = servicio.solicitarBaja(3, "Servidor retirado", admin);

            assertThat(resultado.aplicada()).isTrue();
            assertThat(resultado.ordenesRetiradas()).isEqualTo(2);
            assertThat(servidor.getEstado()).isEqualTo(EstadoServidor.DADO_DE_BAJA);
            assertThat(resultado.solicitud().getEstado()).isEqualTo(EstadoSolicitudBaja.APLICADA);
            assertThat(grupo.getIntegrantes()).isEmpty();
            verify(configuraciones).delete(config);
        }

        @Test
        @DisplayName("con mantenimiento en curso: la solicitud queda pendiente y el servidor no cambia")
        void quedaPendiente() {
            Servidor servidor = DatosPrueba.servidor(3, "srv-db-01", responsable);
            servidor.activarPorConfiguracion();
            when(servidores.findConDetalleById(3)).thenReturn(Optional.of(servidor));
            when(usuarios.findById(1)).thenReturn(Optional.of(DatosPrueba.usuario(1, "admin", Rol.ADMINISTRADOR)));
            when(solicitudesBaja.save(any())).thenAnswer(i -> i.getArgument(0));
            when(mantenimientos.tieneMantenimientoEnCurso(3)).thenReturn(true);

            var resultado = servicio.solicitarBaja(3, "Servidor retirado", admin);

            assertThat(resultado.aplicada()).isFalse();
            assertThat(servidor.getEstado()).isEqualTo(EstadoServidor.ACTIVO);
            assertThat(resultado.solicitud().getEstado()).isEqualTo(EstadoSolicitudBaja.PENDIENTE);
            verify(mantenimientos, never()).retirarPendientes(anyInt(), any(), anyString(), any());
        }

        @Test
        @DisplayName("RF73: reactivar deja el servidor pendiente de configuración")
        void reactiva() {
            Servidor servidor = DatosPrueba.servidor(3, "srv-db-01", responsable);
            servidor.aplicarBaja();
            when(servidores.findConDetalleById(3)).thenReturn(Optional.of(servidor));

            servicio.reactivar(3, admin);

            assertThat(servidor.getEstado()).isEqualTo(EstadoServidor.PENDIENTE_DE_CONFIGURACION);
        }
    }

    @Nested
    @DisplayName("RF11: alcance por rol")
    class Alcance {

        @Test
        @DisplayName("un servidor ajeno responde 'no encontrado' al responsable, no 'acceso denegado'")
        void servidorAjenoNoEncontrado() {
            // 404 y no 403: un 403 confirmaria que existe un servidor ajeno con ese id.
            Servidor ajeno = DatosPrueba.servidor(3, "srv-db-01", responsable);
            when(servidores.findConDetalleById(3)).thenReturn(Optional.of(ajeno));

            assertThatThrownBy(() -> servicio.obtener(3, new AlcanceUsuario(77, Rol.RESPONSABLE)))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("el responsable ve sus propios servidores")
        void servidorPropioVisible() {
            Servidor propio = DatosPrueba.servidor(3, "srv-db-01", responsable);
            when(servidores.findConDetalleById(3)).thenReturn(Optional.of(propio));

            assertThat(servicio.obtener(3, new AlcanceUsuario(5, Rol.RESPONSABLE))).isSameAs(propio);
        }
    }

    @Test
    @DisplayName("RF20: no cambia el responsable de un servidor que integra un grupo")
    void coherenciaConGrupos() {
        Servidor servidor = DatosPrueba.servidor(3, "srv-db-01", responsable);
        when(servidores.findConDetalleById(3)).thenReturn(Optional.of(servidor));
        when(grupos.findDelServidor(3)).thenReturn(List.of(GrupoMantenimiento.nuevo("Grupo DB", null)));

        Usuario otro = DatosPrueba.usuario(6, "j.paredes", Rol.RESPONSABLE);
        lenient().when(usuarios.findById(6)).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> servicio.actualizar(3, datos("srv-db-01", "10.0.0.3", 6), admin))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Grupo DB");
    }
}
