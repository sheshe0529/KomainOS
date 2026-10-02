package com.komainos.inventario.service.intercambio;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.FiltroServidores;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.EspecificacionesServidor;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.inventario.service.CredencialArchivo;
import com.komainos.inventario.service.PuertoCredencialesInventario;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.service.ServicioReautenticacion;
import com.komainos.shared.util.archivo.ColumnaArchivo;
import com.komainos.shared.util.archivo.ArchivoGenerado;
import com.komainos.shared.util.archivo.EscritorTabular;
import com.komainos.shared.util.archivo.FormatoArchivo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServicioExportacionInventario {

    private final ServidorRepositorio servidores;
    private final EscritorTabular escritor;
    private final PuertoCredencialesInventario credenciales;
    private final ServicioReautenticacion reautenticacion;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    /** Iguales para todos los roles: la credencial principal se agrega aparte, solo para el administrador (DEC-39) */
    public List<ColumnaInventario> columnasAutorizadas(AlcanceUsuario alcance) {
        return Arrays.asList(ColumnaInventario.values());
    }

    @Transactional
    public ArchivoGenerado exportar(FiltroServidores filtro, List<ColumnaInventario> columnas,
                                    FormatoArchivo formato, AlcanceUsuario alcance) {
        return generar(filtro, columnas, formato, alcance, false);
    }

    /** RF13: la credencial principal va al final, en claro, y exige reautenticar al administrador (HU05 CA3) */
    @Transactional
    public ArchivoGenerado exportarConCredencial(FiltroServidores filtro, List<ColumnaInventario> columnas,
                                                 FormatoArchivo formato, AlcanceUsuario alcance, String contrasena) {
        reautenticacion.exigir(alcance.usuarioId(), contrasena);
        return generar(filtro, columnas, formato, alcance, true);
    }

    private ArchivoGenerado generar(FiltroServidores filtro, List<ColumnaInventario> columnas,
                                    FormatoArchivo formato, AlcanceUsuario alcance, boolean conCredencial) {
        List<ColumnaInventario> elegidas = columnas == null || columnas.isEmpty()
                ? columnasAutorizadas(alcance)
                : columnas.stream().distinct().filter(columnasAutorizadas(alcance)::contains).toList();

        List<Servidor> lista = servidores.findAll(EspecificacionesServidor.con(filtro, alcance),
                Sort.by("hostname").ascending());
        Map<Integer, CredencialArchivo> principales = conCredencial
                ? credenciales.principales(lista.stream().map(Servidor::getId).toList()) : Map.of();
        List<Map<String, ?>> filas = new ArrayList<>(lista.size());
        for (Servidor servidor : lista) {
            Map<String, Object> fila = new HashMap<>();
            elegidas.forEach(c -> fila.put(c.clave(), c.valorDe(servidor)));
            if (conCredencial) {
                fila.putAll(ColumnaCredencial.valores(principales.get(servidor.getId())));
            }
            filas.add(fila);
        }
        List<ColumnaArchivo> encabezado = new ArrayList<>(elegidas.stream().map(ColumnaInventario::comoColumnaArchivo).toList());
        if (conCredencial) {
            Arrays.stream(ColumnaCredencial.values()).map(ColumnaCredencial::comoColumnaArchivo).forEach(encabezado::add);
        }
        byte[] contenido = escritor.escribir(formato, encabezado, filas, "Servidores");

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("formato", formato.name());
        detalle.put("columnas", elegidas.stream().map(ColumnaInventario::clave).toList());
        detalle.put("registros", lista.size());
        detalle.put("credencialPrincipal", conCredencial);
        detalle.put("credencialesExportadas", principales.size());
        detalle.put("filtro", filtro);
        auditoria.registrar(alcance.actor(), Operacion.de("EXPORTAR_INVENTARIO", "servidor", null)
                .valores(null, detalle));

        String nombre = "inventario_servidores_%s.%s".formatted(LocalDate.now(reloj), formato.extension());
        return new ArchivoGenerado(nombre, formato, contenido);
    }
}
