package com.komainos.mantenimiento;

import com.komainos.PruebaIntegracion;
import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.service.ServicioCredenciales;
import com.komainos.inventario.model.CalendarioSemanal;
import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.DiaSemana;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.inventario.model.NivelCriticidad;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.inventario.service.ServicioGrupo;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.EstadoAutorizacion;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.ReglaPoliticaCambio;
import com.komainos.mantenimiento.service.PuertoProgramacionCambio;
import com.komainos.mantenimiento.service.ServicioAutorizaciones;
import com.komainos.mantenimiento.service.ServicioConsultaOrdenes;
import com.komainos.mantenimiento.service.ServicioDemostracionAutomatizacion;
import com.komainos.mantenimiento.service.ServicioEvaluacionPrevia;
import com.komainos.mantenimiento.service.ServicioOrquestador;
import com.komainos.mantenimiento.service.ServicioPoliticasCambio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.seguridad.service.ServicioUsuario;
import com.komainos.shared.model.Actor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Escenario común del componente de automatización en modo simulado: un entorno con criticidad «Alta», Ubuntu 24.04,
 * una cuenta de servicio de prueba, el servidor srv-prueba con ventana toda la semana y los datos de demostración. El
 * comportamiento de cada servidor lo decide su hostname (application-test.yml, EscenarioSimulado).
 *
 * <p>Con la demostración, la evaluación previa activa dos tareas: «Liberar archivos temporales» (afectación PARCIAL,
 * prioridad 10) y «Revisar consumo de memoria» (SIN_AFECTACION, prioridad 20). La política exige al responsable en
 * PARCIAL y a ambos en TOTAL.</p>
 */
abstract class BaseAutomatizacionIT extends PruebaIntegracion {

    protected static final CalendarioSemanal.IntervaloSemanal TODA_LA_SEMANA = new CalendarioSemanal.IntervaloSemanal(
            DiaSemana.LUNES, LocalTime.MIDNIGHT, DiaSemana.DOMINGO, LocalTime.of(23, 59));

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ServicioUsuario usuarios;
    @Autowired
    protected ServicioCatalogos catalogos;
    @Autowired
    protected ServicioServidor servidores;
    @Autowired
    protected ServicioGrupo grupos;
    @Autowired
    protected ServicioCredenciales credenciales;
    @Autowired
    protected ServicioDemostracionAutomatizacion demostracion;
    @Autowired
    protected PuertoProgramacionCambio programacion;
    @Autowired
    protected ServicioEvaluacionPrevia evaluacion;
    @Autowired
    protected ServicioAutorizaciones autorizaciones;
    @Autowired
    protected ServicioOrquestador orquestador;
    @Autowired
    protected ServicioPoliticasCambio politicas;
    @Autowired
    protected ServicioConsultaOrdenes consulta;

    protected Usuario admin;
    protected Usuario responsable;
    protected Usuario operador;
    protected Actor actor;
    protected Integer entorno;
    protected Integer criticidad;
    protected Integer versionUbuntu;
    protected Integer cuenta;
    protected Servidor servidor;

    @BeforeEach
    void prepararEscenario() {
        limpiarDatos();
        Actor sistema = Actor.sistema("PRUEBA");
        admin = usuarios.crear("admin.auto", "Administrador de pruebas", "Clave-prueba-1", Rol.ADMINISTRADOR, sistema);
        responsable = usuarios.crear("resp.auto", "Responsable de pruebas", "Clave-prueba-2", Rol.RESPONSABLE, sistema);
        operador = usuarios.crear("oper.auto", "Operador de pruebas", "Clave-prueba-3", Rol.OPERADOR, sistema);
        actor = Actor.usuario(admin.getId());
        entorno = catalogos.crearEntorno("Pruebas", null, actor).getId();
        criticidad = catalogos.crearCriticidad(new NivelCriticidad.DatosNivelCriticidad("Alta", 1, 7, 30, 24, 48), actor).getId();
        var ubuntu = catalogos.crearSistemaOperativo("Ubuntu", FamiliaSistemaOperativo.LINUX, actor);
        versionUbuntu = catalogos.agregarVersion(ubuntu.getId(), "24.04", actor).getVersiones().getFirst().getId();
        cuenta = credenciales.registrarCuenta(new ServicioCredenciales.DatosCredencial("Cuenta de pruebas", "svc_komainos",
                null, new ServicioCredenciales.Secreto(TipoAutenticacion.PASSWORD, null, "secreto-solo-para-pruebas", null)),
                actor).getId();
        servidor = crear("srv-prueba", "10.90.0.1");
        demostracion.cargar(actor);
    }

    @AfterEach
    void limpiarEscenario() {
        limpiarDatos();
    }

    protected Servidor crear(String hostname, String ip) {
        return crear(hostname, ip, TODA_LA_SEMANA);
    }

    protected Servidor crear(String hostname, String ip, CalendarioSemanal.IntervaloSemanal ventana) {
        return crear(hostname, ip, versionUbuntu, ventana);
    }

    protected Servidor crear(String hostname, String ip, Integer idVersionSistemaOperativo,
                             CalendarioSemanal.IntervaloSemanal ventana) {
        DatosServidor datos = new DatosServidor(hostname, ip, null, null, null, null, null, idVersionSistemaOperativo, null, entorno,
                criticidad, responsable.getId(), null, List.of(), null, null, null);
        return servidores.registrar(datos, new ServicioServidor.DatosAltaMantenimiento(
                new ServicioServidor.DatosConfiguracion(7, 30, ModalidadPlanificacion.BAJO_DEMANDA, cuenta),
                List.of(ventana), null), actor);
    }

    protected GrupoMantenimiento grupo(String nombre, ModoEjecucion modo, Servidor... integrantes) {
        GrupoMantenimiento g = grupos.crear(nombre, null, actor);
        grupos.reemplazarIntegrantes(g.getId(), List.of(integrantes).stream().map(Servidor::getId).toList(), actor);
        grupos.configurar(g.getId(), new ServicioGrupo.DatosConfiguracionGrupo(7, 30, ModalidadPlanificacion.BAJO_DEMANDA,
                modo, cuenta), actor);
        return g;
    }

    /** Lanzamiento inmediato y evaluación previa completa (RF31, RF38) */
    protected Integer lanzar(Servidor s) {
        Integer id = programacion.inmediata(s.getId(), null, false, "Mantenimiento de prueba", null, actor).getId();
        evaluacion.evaluar(id, actor);
        return id;
    }

    protected Integer lanzarGrupo(GrupoMantenimiento g) {
        Integer id = programacion.inmediata(null, g.getId(), false, "Mantenimiento grupal de prueba", null, actor).getId();
        evaluacion.evaluar(id, actor);
        return id;
    }

    /** Lanza y aprueba como responsable, que es lo que exige la política para la afectación PARCIAL */
    protected Integer lanzarAutorizada(Servidor s) {
        Integer id = lanzar(s);
        aprobarComoResponsable(id);
        return id;
    }

    protected void aprobarComoResponsable(Integer id) {
        autorizaciones.decidir(id, EstadoAutorizacion.APROBADA, "Conforme con la MOP", null,
                new AlcanceUsuario(responsable.getId(), Rol.RESPONSABLE));
    }

    /** Cambia la regla de una celda de la política conservando sus notificaciones */
    protected void exigir(NivelAfectacion afectacion, boolean responsableAprueba, boolean administradorAprueba) {
        ReglaPoliticaCambio regla = politicas.listar(PageRequest.of(0, 100)).stream()
                .filter(r -> r.getEntorno().getId().equals(entorno) && r.getAfectacion() == afectacion)
                .findFirst().orElseThrow();
        politicas.guardar(regla.getId(), new ServicioPoliticasCambio.DatosRegla(entorno, afectacion, responsableAprueba,
                administradorAprueba, true, Set.of(), regla.getFechaActualizacion()), actor);
    }

    protected EstadoOrden estado(Integer idOrden) {
        return consulta.obtener(idOrden, new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR)).getEstado();
    }

    protected Integer contar(String sql, Object... argumentos) {
        return jdbc.queryForObject(sql, Integer.class, argumentos);
    }

    protected static RequestPostProcessor como(Usuario usuario) {
        return user(new UsuarioAutenticado(usuario));
    }

    /** Espera a que un proceso en segundo plano llegue al estado, con un límite para no colgar la prueba */
    protected void esperarEstado(Integer idOrden, EstadoOrden esperado) throws InterruptedException {
        for (int i = 0; i < 300 && estado(idOrden) != esperado; i++) {
            Thread.sleep(20);
        }
    }
}
