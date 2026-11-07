package com.komainos.auditoria.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** RNF11: defensa adicional de lectura; los productores siguen obligados a auditar solo datos no secretos. */
@Component
public class ProtectorValoresAuditoria {
    private static final Set<String> SENSIBLES = Set.of("password", "passwd", "contrasena", "contraseña",
            "secreto", "secret", "token", "hashcontrasena", "llaveprivada", "privatekey", "llavemaestra",
            "secretosu", "susecreto", "secretocifrado", "ivnonce", "tagautenticacion");
    private final ObjectMapper json;

    public ProtectorValoresAuditoria(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode leer(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            JsonNode nodo = json.readTree(valor);
            proteger(nodo);
            return nodo;
        } catch (JsonProcessingException ex) {
            // Un registro legado mal formado se identifica sin devolver su texto potencialmente sensible.
            return json.createObjectNode().put("detalle", "El valor histórico no tiene formato JSON válido");
        }
    }

    private void proteger(JsonNode nodo) {
        if (nodo == null) return;
        if (nodo.isObject()) {
            ObjectNode objeto = (ObjectNode) nodo;
            objeto.properties().forEach(campo -> {
                String clave = campo.getKey().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
                if (SENSIBLES.contains(clave)) objeto.put(campo.getKey(), "[PROTEGIDO]");
                else proteger(campo.getValue());
            });
        } else if (nodo.isArray()) {
            nodo.forEach(this::proteger);
        }
    }
}
