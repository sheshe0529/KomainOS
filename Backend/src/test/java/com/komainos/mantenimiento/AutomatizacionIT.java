package com.komainos.mantenimiento;
import com.komainos.PruebaIntegracion;
import com.komainos.mantenimiento.service.*;import com.komainos.mantenimiento.model.*;import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;import com.komainos.catalogo.model.*;import com.komainos.catalogo.repository.*;
import com.komainos.inventario.model.*;import com.komainos.inventario.service.*;import com.komainos.credencial.service.ServicioCredenciales;import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.seguridad.model.*;import com.komainos.seguridad.service.ServicioUsuario;import com.komainos.shared.model.Actor;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.test.web.servlet.MockMvc;import java.util.*;import java.time.*;
import static org.assertj.core.api.Assertions.*;import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@DisplayName("Objetivo 3: automatización y ejecución local sin Ansible real")
class AutomatizacionIT extends PruebaIntegracion {
 @Autowired com.komainos.alerta.service.ServicioAlertas servicioAlertas;
 @Autowired ServicioUsuario usuarios;@Autowired ServicioCatalogos catalogos;@Autowired ServicioServidor servidores;@Autowired ServicioGrupo grupos;@Autowired ServicioCredenciales credenciales;@Autowired ServicioDemostracionAutomatizacion demo;
 @Autowired ServicioCicloMantenimiento ciclo;@Autowired ServicioConsultaOrdenes consulta;@Autowired ServicioConsultaAutomatizacion informes;@Autowired ServicioPoliticasCambio politicas;@Autowired ServicioOrquestador orquestador;@Autowired PuertoProgramacionCambio programacion;@Autowired ServicioCatalogoAutomatizacion catalogo;@Autowired ScriptRepositorio scripts;@Autowired MockMvc mvc;
 Integer entorno,criticidad,version,cuenta;Usuario admin,responsable,operador;Actor actor;Servidor servidor;
 @BeforeEach void preparar(){limpiarDatos();Actor sistema=Actor.sistema("PRUEBA");admin=usuarios.crear("admin.auto","Administrador","Clave-prueba-1",Rol.ADMINISTRADOR,sistema);responsable=usuarios.crear("resp.auto","Responsable","Clave-prueba-2",Rol.RESPONSABLE,sistema);operador=usuarios.crear("oper.auto","Operador","Clave-prueba-3",Rol.OPERADOR,sistema);actor=Actor.usuario(admin.getId());entorno=catalogos.crearEntorno("Pruebas",null,actor).getId();criticidad=catalogos.crearCriticidad(new NivelCriticidad.DatosNivelCriticidad("Alta",1,7,30,24,48),actor).getId();var so=catalogos.crearSistemaOperativo("Ubuntu",FamiliaSistemaOperativo.LINUX,actor);version=catalogos.agregarVersion(so.getId(),"24.04",actor).getVersiones().getFirst().getId();cuenta=credenciales.registrarCuenta(new ServicioCredenciales.DatosCredencial("Cuenta simulada","prueba",null,new ServicioCredenciales.Secreto(TipoAutenticacion.PASSWORD,null,"secreto-solo-para-pruebas",null)),actor).getId();servidor=crear("srv-prueba","10.90.0.1");demo.cargar(actor);}
 @AfterEach void limpiar(){limpiarDatos();}
 private Servidor crear(String hostname,String ip){var d=new DatosServidor(hostname,ip,null,null,null,null,null,version,null,entorno,criticidad,responsable.getId(),null,List.of(),null,null,null);return servidores.registrar(d,new ServicioServidor.DatosAltaMantenimiento(new ServicioServidor.DatosConfiguracion(7,30,ModalidadPlanificacion.BAJO_DEMANDA,cuenta),List.of(new CalendarioSemanal.IntervaloSemanal(DiaSemana.LUNES,LocalTime.MIDNIGHT,DiaSemana.DOMINGO,LocalTime.of(23,59))),null),actor);}
 private Integer lanzar(Servidor s){var o=programacion.inmediata(s.getId(),null,false,"Mantenimiento de prueba",null,actor);ciclo.evaluarOrden(o.getId(),actor);return o.getId();}
 private EstadoOrden estado(Integer id){return consulta.obtener(id,new AlcanceUsuario(admin.getId(),Rol.ADMINISTRADOR)).getEstado();}
 @Test @DisplayName("RF37-RF57: evaluación, MOP, ejecución estática, reporte comparativo y conformidad cierran el ciclo")void flujoCompleto()throws Exception{
  Integer id=lanzar(servidor);assertThat(estado(id)).isEqualTo(EstadoOrden.AUTORIZADA);assertThat(orquestador.drenar()).isEqualTo(1);assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
  mvc.perform(get("/api/ordenes/{id}/automatizacion",id).with(user(new UsuarioAutenticado(responsable)))).andExpect(status().isOk()).andExpect(jsonPath("$.simulado").value(true)).andExpect(jsonPath("$.historial").isArray()).andExpect(jsonPath("$.orden.codigo").isNotEmpty()).andExpect(jsonPath("$.mops[0].tareas.length()").value(2)).andExpect(jsonPath("$.evaluaciones.length()").value(2)).andExpect(jsonPath("$..secreto").doesNotExist());
  mvc.perform(post("/api/ordenes/{id}/validacion",id).with(user(new UsuarioAutenticado(responsable))).contentType("application/json").content("{\"conforme\":true,\"observaciones\":\"Servicio operativo\"}")).andExpect(status().isNoContent());assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
  assertThat(jdbc.queryForObject("select count(*) from cierre_orden where id_orden=?",Integer.class,id)).isEqualTo(1);
 }
 @Test @DisplayName("RF44/RF75: responsable primero y administrador después, con un plazo compartido")void autorizacionSecuencial(){
  var r=politicas.listar(org.springframework.data.domain.PageRequest.of(0,100)).stream().filter(x->x.getAfectacion()==NivelAfectacion.SIN_AFECTACION).findFirst().orElseThrow();politicas.guardar(r.getId(),new ServicioPoliticasCambio.DatosRegla(entorno,NivelAfectacion.SIN_AFECTACION,true,true,true,Set.of(),r.getFechaActualizacion()),actor);
  Integer id=lanzar(servidor);assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
  assertThatThrownBy(()->ciclo.decidir(id,EstadoAutorizacion.APROBADA,"Admin anticipado",null,new AlcanceUsuario(admin.getId(),Rol.ADMINISTRADOR))).hasMessageContaining("rol");
  ciclo.decidir(id,EstadoAutorizacion.APROBADA,"Conforme",null,new AlcanceUsuario(responsable.getId(),Rol.RESPONSABLE));assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);ciclo.decidir(id,EstadoAutorizacion.APROBADA,"Aprobado",null,new AlcanceUsuario(admin.getId(),Rol.ADMINISTRADOR));assertThat(estado(id)).isEqualTo(EstadoOrden.AUTORIZADA);
 }
 @Test @DisplayName("RF26/RF42: publicar código nuevo no cambia la versión fijada en una MOP")void versionesInmutables(){
  Integer id=lanzar(servidor);var antes=informes.ficha(id,new AlcanceUsuario(admin.getId(),Rol.ADMINISTRADOR));Integer fijada=antes.mops().getFirst().getTareas().getFirst().getVersion().getId();Integer impl=antes.mops().getFirst().getTareas().getFirst().getVersion().getImplementacion().getId();catalogo.publicar(impl,"#!/bin/bash\necho nueva\n","Versión posterior",List.of(),actor);var despues=informes.ficha(id,new AlcanceUsuario(admin.getId(),Rol.ADMINISTRADOR));assertThat(despues.mops().getFirst().getTareas().getFirst().getVersion().getId()).isEqualTo(fijada);
 }
 @Test @DisplayName("RF59/RF62: un fallo detiene las pendientes y genera incidencia")void fallo(){Integer id=lanzar(crear("srv-fallo","10.90.0.2"));orquestador.drenar();assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);assertThat(jdbc.queryForObject("select count(*) from incidencia i join orden_detalle d using(id_orden_detalle) where d.id_orden=?",Integer.class,id)).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from mop_tarea t join mop m using(id_mop) join orden_detalle d using(id_orden_detalle) where d.id_orden=? and t.estado='NO_INICIADA'",Integer.class,id)).isEqualTo(1);}
 @Test @DisplayName("RF55: métricas posteriores fuera de umbral crean incidencia técnica")void noConforme(){Integer id=lanzar(crear("srv-no-conforme","10.90.0.3"));orquestador.drenar();assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);assertThat(jdbc.queryForObject("select count(*) from validacion_tecnica v join orden_detalle d using(id_orden_detalle) where d.id_orden=? and v.resultado='NO_CONFORME'",Integer.class,id)).isEqualTo(1);}
 @Test @DisplayName("RF58: un piloto fallido impide iniciar los demás servidores del grupo")void piloto(){var fallo=crear("srv-fallo","10.90.0.2");var segundo=crear("srv-segundo","10.90.0.3");var g=grupos.crear("Grupo piloto",null,actor);grupos.reemplazarIntegrantes(g.getId(),List.of(fallo.getId(),segundo.getId()),actor);grupos.configurar(g.getId(),new ServicioGrupo.DatosConfiguracionGrupo(7,30,ModalidadPlanificacion.BAJO_DEMANDA,ModoEjecucion.PARALELO,cuenta),actor);var o=programacion.inmediata(null,g.getId(),false,"Prueba piloto",null,actor);ciclo.evaluarOrden(o.getId(),actor);orquestador.drenar();assertThat(estado(o.getId())).isEqualTo(EstadoOrden.INCIDENCIA);assertThat(jdbc.queryForObject("select count(*) from orden_detalle where id_orden=? and fecha_real_inicio is not null",Integer.class,o.getId())).isEqualTo(1);}
 @Test @DisplayName("RF71: la conectividad local es simulada y el operador no puede ejecutar cambios")void permisosYConexion()throws Exception{mvc.perform(post("/api/servidores/{id}/conectividad",servidor.getId()).with(user(new UsuarioAutenticado(admin)))).andExpect(status().isOk()).andExpect(jsonPath("$.simulado").value(true)).andExpect(jsonPath("$.correcto").value(true));mvc.perform(post("/api/automatizacion/procesar").with(user(new UsuarioAutenticado(operador)))).andExpect(status().isForbidden());}
 @Test @DisplayName("RF24: no admite un segundo análisis activo para el mismo SO y versión")void analisisUnico(){var s=catalogo.guardarScript(null,"Otro análisis",TipoScript.ANALISIS,null,true,actor);var i=catalogo.implementar(s.getId(),version,actor);assertThatThrownBy(()->catalogo.publicar(i.getId(),"echo {}",null,List.of(),actor)).isInstanceOf(com.komainos.shared.exception.ConflictoException.class);}
 @Test @DisplayName("RF12/RF69: la demostración es idempotente y las alertas pertenecen a su destinatario")void demoYAlertas()throws Exception{demo.cargar(actor);assertThat(scripts.count()).isEqualTo(3);Integer id=lanzar(servidor);mvc.perform(get("/api/alertas").with(user(new UsuarioAutenticado(responsable)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(org.hamcrest.Matchers.greaterThan(0)));var ajeno=usuarios.crear("ajeno.auto","Otro responsable","Clave-prueba-4",Rol.RESPONSABLE,actor);mvc.perform(get("/api/ordenes/{id}/automatizacion",id).with(user(new UsuarioAutenticado(ajeno)))).andExpect(status().isNotFound());}

 @Test @DisplayName("RF50: al vencer la autorización se bloquea la ejecución y se cierra sin intervención")void vencimientoAutorizacion(){
  var r=politicas.listar(org.springframework.data.domain.PageRequest.of(0,100)).stream().filter(x->x.getAfectacion()==NivelAfectacion.SIN_AFECTACION).findFirst().orElseThrow();politicas.guardar(r.getId(),new ServicioPoliticasCambio.DatosRegla(entorno,NivelAfectacion.SIN_AFECTACION,true,true,true,Set.of(),r.getFechaActualizacion()),actor);
  Integer id=lanzar(servidor);jdbc.update("update solicitud_autorizacion set fecha_limite=? where id_orden=?",java.sql.Timestamp.from(AHORA.minusSeconds(1)),id);ciclo.vencerPlazos();assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);assertThat(orquestador.drenar()).isZero();
 }
 @Test @DisplayName("RF57: una validación vencida se cierra sin registrar conformidad")void vencimientoValidacion(){Integer id=lanzar(servidor);orquestador.drenar();jdbc.update("update validacion_funcional set fecha_solicitud=?,fecha_limite=? where id_orden=?",java.sql.Timestamp.from(AHORA.minusSeconds(7200)),java.sql.Timestamp.from(AHORA.minusSeconds(3600)),id);ciclo.vencerPlazos();assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);assertThat(jdbc.queryForObject("select estado::text from validacion_funcional where id_orden=?",String.class,id)).isEqualTo("VENCIDA");}
 @Test @DisplayName("RF60: una tarea en curso termina, las pendientes se omiten al finalizar la ventana")void finVentana() {
  Integer id=lanzar(servidor);java.util.concurrent.atomic.AtomicBoolean primera=new java.util.concurrent.atomic.AtomicBoolean(true);
  org.mockito.Mockito.doAnswer(inv->{var peticion=(com.komainos.ejecucion.model.SolicitudEjecucion)inv.getArgument(0);if(!peticion.analisis()&&primera.getAndSet(false))jdbc.update("update orden set inicio_ventana_aplicada=?,fin_ventana_aplicada=? where id_orden=?",java.sql.Timestamp.from(AHORA.minusSeconds(7200)),java.sql.Timestamp.from(AHORA.minusSeconds(1)),id);return inv.callRealMethod();}).when(transporte).ejecutar(org.mockito.ArgumentMatchers.any());
  orquestador.drenar();assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);assertThat(jdbc.queryForObject("select count(*) from mop_tarea t join mop m using(id_mop) join orden_detalle d using(id_orden_detalle) where d.id_orden=? and t.estado='FINALIZADA'",Integer.class,id)).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from mop_tarea t join mop m using(id_mop) join orden_detalle d using(id_orden_detalle) where d.id_orden=? and t.estado='NO_INICIADA'",Integer.class,id)).isEqualTo(1);
 }
 @Test @DisplayName("RF51: dos órdenes sobre un servidor conservan una sola ejecución simultánea")void exclusion()throws Exception{
  Integer primero=lanzar(servidor);Integer segundo=lanzar(servidor);var inicio=new java.util.concurrent.CountDownLatch(1);var liberar=new java.util.concurrent.CountDownLatch(1);
  org.mockito.Mockito.doAnswer(inv->{var p=(com.komainos.ejecucion.model.SolicitudEjecucion)inv.getArgument(0);if(!p.analisis()){inicio.countDown();if(!liberar.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Espera de prueba agotada");}return inv.callRealMethod();}).when(transporte).ejecutar(org.mockito.ArgumentMatchers.any());
  try{assertThat(orquestador.despachar()).isEqualTo(1);assertThat(inicio.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(orquestador.despachar()).isZero();assertThat(jdbc.queryForObject("select count(*) from orden_detalle where id_servidor=? and estado='EN_EJECUCION'",Integer.class,servidor.getId())).isEqualTo(1);}finally{liberar.countDown();}
  for(int i=0;i<200&&estado(primero)!=EstadoOrden.PENDIENTE_VALIDACION;i++)Thread.sleep(20);orquestador.drenar();assertThat(estado(segundo)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
 }
 @Test @DisplayName("RF61: atender y cerrar una incidencia conserva el historial sin reanudar tareas")void atenderIncidencia()throws Exception{
  Integer id=lanzar(crear("srv-fallo","10.90.0.2"));orquestador.drenar();Integer incidente=jdbc.queryForObject("select i.id_incidencia from incidencia i join orden_detalle d using(id_orden_detalle) where d.id_orden=?",Integer.class,id);
  mvc.perform(put("/api/incidencias/{id}",incidente).with(user(new UsuarioAutenticado(admin))).contentType("application/json").content("{\"diagnostico\":\"Fallo simulado\",\"acciones\":\"Revisado\",\"resultado\":\"Atendido\",\"cerrar\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CERRADA"));assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);assertThat(orquestador.drenar()).isZero();
 }

 @Test @DisplayName("RF42: una implementación deshabilitada bloquea la MOP y no ejecuta")void sinImplementacion(){var script=catalogo.scripts(org.springframework.data.domain.PageRequest.of(0,100)).stream().filter(x->x.getNombre().equals("Comprobar capacidad de disco")).findFirst().orElseThrow();catalogo.habilitar(script.getImplementaciones().getFirst().getVersiones().getFirst().getId(),false,actor);Integer id=lanzar(servidor);assertThat(estado(id)).isEqualTo(EstadoOrden.SIN_IMPLEMENTACION);assertThat(orquestador.drenar()).isZero();}
 @Test @DisplayName("RF40: una revisión crítica genera una orden de prioridad alta con su evaluación de origen")void condicionCritica(){var s=crear("srv-critico","10.90.0.4");ciclo.revisar(s.getId(),actor);assertThat(jdbc.queryForObject("select count(*) from orden where id_servidor=? and origen='CONDICION_CRITICA' and id_evaluacion_origen is not null",Integer.class,s.getId())).isEqualTo(1);}
 @Test @DisplayName("RF29/RF64: el cierre dispara la planificación del siguiente ciclo automático")void siguienteCiclo(){Integer id=lanzar(servidor);orquestador.drenar();servidores.configurar(servidor.getId(),new ServicioServidor.DatosConfiguracion(7,30,ModalidadPlanificacion.AUTOMATICA,cuenta),actor);ciclo.validar(id,true,"Conforme",new AlcanceUsuario(responsable.getId(),Rol.RESPONSABLE));assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);assertThat(jdbc.queryForObject("select count(*) from orden where id_servidor=? and origen='PLANIFICACION_AUTOMATICA'",Integer.class,servidor.getId())).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from cierre_orden where id_orden=? and id_orden_siguiente is not null",Integer.class,id)).isEqualTo(1);}

 @Test @DisplayName("RF66: los cinco KPI coinciden con los ciclos y respuestas del periodo")void indicadoresGestion()throws Exception{
  Integer correcto=lanzar(servidor);orquestador.drenar();ciclo.validar(correcto,true,"Conforme",new AlcanceUsuario(responsable.getId(),Rol.RESPONSABLE));
  Integer fallido=lanzar(crear("srv-fallo","10.90.0.2"));orquestador.drenar();
  Integer incidencia=jdbc.queryForObject("select i.id_incidencia from incidencia i join orden_detalle d using(id_orden_detalle) where d.id_orden=?",Integer.class,fallido);
  jdbc.update("update incidencia set fecha_apertura=? where id_incidencia=?",java.sql.Timestamp.from(AHORA.minusSeconds(14400)),incidencia);
  ciclo.atender(incidencia,"Fallo simulado","Control revisado","Resuelto",true,actor);
  // fecha_alta se asigna en PostgreSQL; la situamos en el periodo del reloj fijo de esta prueba.
  jdbc.update("update servidor set fecha_alta=?",java.sql.Timestamp.from(AHORA));
  mvc.perform(get("/api/automatizacion/kpis").with(user(new UsuarioAutenticado(operador))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.fechaActualizacion").value(AHORA.toString()))
   .andExpect(jsonPath("$.indicadores[0].valor").value(100.0))
   .andExpect(jsonPath("$.indicadores[1].valor").value(50.0))
   .andExpect(jsonPath("$.indicadores[2].valor").value(50.0))
   .andExpect(jsonPath("$.indicadores[3].valor").value(100.0))
   .andExpect(jsonPath("$.indicadores[4].valor").value(4.0))
   .andExpect(jsonPath("$.actividadDiaria[-1].mantenimientos").value(2))
   .andExpect(jsonPath("$.actividadDiaria[-1].altas").value(2))
   .andExpect(jsonPath("$.inventario[0].cantidad").value(2));
 }
 @Test @DisplayName("RF66: un periodo sin ejecuciones informa ausencia de datos y rechaza fechas invertidas")void indicadoresSinDatos()throws Exception{
  mvc.perform(get("/api/automatizacion/kpis").with(user(new UsuarioAutenticado(admin))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.indicadores[2].denominador").value(0)).andExpect(jsonPath("$.indicadores[2].valor").doesNotExist());
  mvc.perform(get("/api/automatizacion/kpis").param("desde",AHORA.toString()).param("hasta",AHORA.minusSeconds(1).toString()).with(user(new UsuarioAutenticado(admin)))).andExpect(status().isUnprocessableEntity());
 }

 @Test @DisplayName("RF48: aceptar una fecha propuesta comprueba la programación y abre una nueva ronda")void propuestaFecha(){
  var regla=politicas.listar(org.springframework.data.domain.PageRequest.of(0,100)).stream().filter(x->x.getAfectacion()==NivelAfectacion.SIN_AFECTACION).findFirst().orElseThrow();
  politicas.guardar(regla.getId(),new ServicioPoliticasCambio.DatosRegla(entorno,NivelAfectacion.SIN_AFECTACION,true,true,true,Set.of(),regla.getFechaActualizacion()),actor);
  Integer id=lanzar(servidor);Instant propuesta=AHORA.plusSeconds(172800);
  ciclo.decidir(id,EstadoAutorizacion.FECHA_PROPUESTA,"Otra fecha dentro de ventana",propuesta,new AlcanceUsuario(responsable.getId(),Rol.RESPONSABLE));
  ciclo.aceptarFecha(id,actor);var informe=informes.ficha(id,new AlcanceUsuario(admin.getId(),Rol.ADMINISTRADOR));
  assertThat(informe.orden().getFechaInicioProgramada()).isEqualTo(propuesta);assertThat(informe.autorizaciones()).hasSize(2);
  assertThat(informe.autorizaciones().getLast().getDecisiones()).allMatch(d->d.getEstado()==EstadoAutorizacion.PENDIENTE);
  assertThat(orquestador.drenar()).isZero();assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
 }
 @Test @DisplayName("RF58: el piloto conforme habilita los demás servidores del grupo paralelo")void pilotoConforme(){
  var segundo=crear("srv-segundo","10.90.0.2");var g=grupos.crear("Grupo conforme",null,actor);grupos.reemplazarIntegrantes(g.getId(),List.of(servidor.getId(),segundo.getId()),actor);
  grupos.configurar(g.getId(),new ServicioGrupo.DatosConfiguracionGrupo(7,30,ModalidadPlanificacion.BAJO_DEMANDA,ModoEjecucion.PARALELO,cuenta),actor);
  var o=programacion.inmediata(null,g.getId(),false,"Piloto conforme",null,actor);ciclo.evaluarOrden(o.getId(),actor);
  assertThat(orquestador.drenar()).isEqualTo(2);assertThat(estado(o.getId())).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
  assertThat(jdbc.queryForObject("select count(*) from orden_detalle where id_orden=? and es_servidor_piloto",Integer.class,o.getId())).isEqualTo(1);
 }
 @Test @DisplayName("RF57/RF62: el rechazo funcional conserva la respuesta y abre una incidencia")void rechazoFuncional(){
  Integer id=lanzar(servidor);orquestador.drenar();ciclo.validar(id,false,"El servicio aún requiere revisión",new AlcanceUsuario(responsable.getId(),Rol.RESPONSABLE));
  assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);assertThat(jdbc.queryForObject("select estado::text from validacion_funcional where id_orden=?",String.class,id)).isEqualTo("RECHAZADA");
  assertThat(jdbc.queryForObject("select count(*) from incidencia i join orden_detalle d using(id_orden_detalle) where d.id_orden=? and i.origen='RECHAZO_FUNCIONAL'",Integer.class,id)).isEqualTo(1);
 }

 @Test @DisplayName("RF42: sin análisis compatible se bloquea la orden y se notifica al administrador")void sinAnalisis(){
  var analisis=catalogo.scripts(org.springframework.data.domain.PageRequest.of(0,100)).stream().filter(s->s.getTipo()==TipoScript.ANALISIS).findFirst().orElseThrow();
  catalogo.habilitar(analisis.getImplementaciones().getFirst().getVersiones().getFirst().getId(),false,actor);Integer id=lanzar(servidor);
  assertThat(estado(id)).isEqualTo(EstadoOrden.SIN_IMPLEMENTACION);assertThat(orquestador.drenar()).isZero();
  assertThat(jdbc.queryForObject("select count(*) from alerta where id_orden=? and tipo='SIN_IMPLEMENTACION'",Integer.class,id)).isEqualTo(1);
 }

 @Test @DisplayName("RF59/RF61: una incidencia atendida durante otra tarea se cierra al terminar el trabajo en curso")void cierreConTrabajoEnCurso()throws Exception{
  var segundo=crear("srv-segundo","10.90.0.2");var fallo=crear("srv-fallo","10.90.0.3");
  var g=grupos.crear("Grupo concurrente",null,actor);grupos.reemplazarIntegrantes(g.getId(),List.of(servidor.getId(),segundo.getId(),fallo.getId()),actor);
  grupos.configurar(g.getId(),new ServicioGrupo.DatosConfiguracionGrupo(7,30,ModalidadPlanificacion.BAJO_DEMANDA,ModoEjecucion.PARALELO,cuenta),actor);
  var o=programacion.inmediata(null,g.getId(),false,"Incidencia con tarea en curso",null,actor);ciclo.evaluarOrden(o.getId(),actor);
  // La elección del piloto por empate usa el orden del grupo; se fija el servidor sano para este escenario.
  jdbc.update("update orden_detalle set es_servidor_piloto=(id_servidor=?) where id_orden=?",servidor.getId(),o.getId());
  var inicio=new java.util.concurrent.CountDownLatch(1);var liberar=new java.util.concurrent.CountDownLatch(1);
  org.mockito.Mockito.doAnswer(inv->{var p=(com.komainos.ejecucion.model.SolicitudEjecucion)inv.getArgument(0);if(!p.analisis()&&p.idServidor().equals(segundo.getId())){inicio.countDown();if(!liberar.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Espera agotada");}if(!p.analisis()&&p.idServidor().equals(fallo.getId())&&!inicio.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("No inició el servidor paralelo");return inv.callRealMethod();}).when(transporte).ejecutar(org.mockito.ArgumentMatchers.any());
  try{
   assertThat(orquestador.despachar()).isEqualTo(1);
   for(int i=0;i<250&&jdbc.queryForObject("select count(*) from orden_detalle where id_orden=? and es_servidor_piloto and estado='FINALIZADO'",Integer.class,o.getId())==0;i++)Thread.sleep(20);
   assertThat(orquestador.despachar()).isEqualTo(2);assertThat(inicio.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
   for(int i=0;i<250&&estado(o.getId())!=EstadoOrden.INCIDENCIA;i++)Thread.sleep(20);
   Integer incidente=jdbc.queryForObject("select i.id_incidencia from incidencia i join orden_detalle d using(id_orden_detalle) where d.id_orden=?",Integer.class,o.getId());
   ciclo.atender(incidente,"Fallo aislado","Revisado","Resuelto",true,actor);assertThat(estado(o.getId())).isEqualTo(EstadoOrden.INCIDENCIA);
  }finally{liberar.countDown();}
  for(int i=0;i<250&&estado(o.getId())!=EstadoOrden.CERRADA;i++)Thread.sleep(20);
  assertThat(estado(o.getId())).isEqualTo(EstadoOrden.CERRADA);
 }

 @Test @DisplayName("Incidencias: listado vacío, listado con datos y filtro de estado funcionan por HTTP")void consultaIncidencias()throws Exception{
  mvc.perform(get("/api/incidencias").with(user(new UsuarioAutenticado(admin)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
  Integer id=lanzar(crear("srv-fallo","10.90.0.2"));orquestador.drenar();
  mvc.perform(get("/api/incidencias").with(user(new UsuarioAutenticado(operador)))).andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].idOrden").value(id));
  mvc.perform(get("/api/incidencias").param("estado","ABIERTA").with(user(new UsuarioAutenticado(admin)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
  mvc.perform(get("/api/incidencias").param("texto","SRV-FALLO").with(user(new UsuarioAutenticado(admin)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
  Integer incidencia=jdbc.queryForObject("select i.id_incidencia from incidencia i join orden_detalle d using(id_orden_detalle) where d.id_orden=?",Integer.class,id);
  mvc.perform(get("/api/incidencias/{id}",incidencia).with(user(new UsuarioAutenticado(operador)))).andExpect(status().isOk()).andExpect(jsonPath("$.servidor").value("srv-fallo"));
  mvc.perform(get("/api/incidencias/{id}",incidencia).with(user(new UsuarioAutenticado(responsable)))).andExpect(status().isForbidden());
  mvc.perform(get("/api/incidencias").param("estado","CERRADA").with(user(new UsuarioAutenticado(admin)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
 }

 @Test @DisplayName("Alertas: detalle completo, contador y lectura respetan al destinatario")void detalleAlertas()throws Exception{
  servicioAlertas.emitir(com.komainos.alerta.model.TipoAlerta.ADVERTENCIA_UMBRAL,"Disco por encima del límite","Mensaje completo de la alerta\nRevise las métricas del servidor.",servidor.getId(),null,null,null,Set.of(responsable.getId()));
  Integer id=jdbc.queryForObject("select id_alerta_destinatario from alerta_destinatario where id_usuario=?",Integer.class,responsable.getId());
  mvc.perform(get("/api/alertas/{id}",id).with(user(new UsuarioAutenticado(responsable)))).andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("Mensaje completo de la alerta\nRevise las métricas del servidor.")).andExpect(jsonPath("$.idServidor").value(servidor.getId()));
  mvc.perform(get("/api/alertas/{id}",id).with(user(new UsuarioAutenticado(admin)))).andExpect(status().isNotFound());
  mvc.perform(get("/api/alertas/pendientes").with(user(new UsuarioAutenticado(responsable)))).andExpect(status().isOk()).andExpect(jsonPath("$.pendientes").value(1));
  mvc.perform(post("/api/alertas/{id}/lectura",id).with(user(new UsuarioAutenticado(responsable)))).andExpect(status().isNoContent());
  mvc.perform(get("/api/alertas/pendientes").with(user(new UsuarioAutenticado(responsable)))).andExpect(status().isOk()).andExpect(jsonPath("$.pendientes").value(0));
 }
 @Test @DisplayName("Matriz: modificar una celda conserva las demás combinaciones y los permisos")void edicionCeldaPolitica()throws Exception{
  var regla=politicas.aplicable(entorno,NivelAfectacion.PARCIAL);
  String cuerpo="{\"idEntorno\":"+entorno+",\"nivelAfectacion\":\"PARCIAL\",\"requiereResponsable\":true,\"requiereAdministrador\":false,\"activo\":true,\"notificaciones\":[\"OPERADOR\"],\"versionEsperada\":\""+regla.getFechaActualizacion()+"\"}";
  mvc.perform(put("/api/politicas-cambio/{id}",regla.getId()).with(user(new UsuarioAutenticado(operador))).contentType("application/json").content(cuerpo)).andExpect(status().isForbidden());
  mvc.perform(put("/api/politicas-cambio/{id}",regla.getId()).with(user(new UsuarioAutenticado(admin))).contentType("application/json").content(cuerpo)).andExpect(status().isOk()).andExpect(jsonPath("$.requiereAdministrador").value(false)).andExpect(jsonPath("$.notificaciones[0]").value("OPERADOR"));
  assertThat(politicas.aplicable(entorno,NivelAfectacion.TOTAL).isRequiereAdministrador()).isTrue();
  mvc.perform(get("/api/politicas-cambio").with(user(new UsuarioAutenticado(operador)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(3));
 }
 @Test @DisplayName("Gráficos: recursos y barras apiladas se calculan desde evaluaciones y tareas reales")void graficosReales()throws Exception{
  lanzar(servidor);lanzar(crear("srv-no-conforme","10.90.0.2"));lanzar(crear("srv-fallo","10.90.0.3"));orquestador.drenar();
  mvc.perform(get("/api/automatizacion/graficos").with(user(new UsuarioAutenticado(operador)))).andExpect(status().isOk())
   .andExpect(jsonPath("$.recursos.length()").value(24)).andExpect(jsonPath("$.recursos[-1].servidores").value(3)).andExpect(jsonPath("$.recursos[-1].cpu").isNumber())
   .andExpect(jsonPath("$.ejecuciones.length()").value(7)).andExpect(jsonPath("$.ejecuciones[-1].exito").value(2)).andExpect(jsonPath("$.ejecuciones[-1].advertencia").value(2)).andExpect(jsonPath("$.ejecuciones[-1].error").value(1));
 }
 @Test @DisplayName("Gráficos: las horas sin mediciones conservan valores ausentes y ejecuciones cero")void graficosSinDatos()throws Exception{
  mvc.perform(get("/api/automatizacion/graficos").with(user(new UsuarioAutenticado(admin)))).andExpect(status().isOk()).andExpect(jsonPath("$.recursos.length()").value(24)).andExpect(jsonPath("$.recursos[0].cpu").doesNotExist()).andExpect(jsonPath("$.recursos[0].servidores").value(0)).andExpect(jsonPath("$.ejecuciones[-1].exito").value(0));
 }
}
