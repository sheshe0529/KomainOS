package com.komainos.seguridad.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Separado para que la entidad no implemente UserDetails */
public record UsuarioAutenticado(Usuario usuario) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(usuario.getRol().autoridad()));
    }

    @Override
    public String getPassword() {
        return usuario.getHashContrasena();
    }

    @Override
    public String getUsername() {
        return usuario.getCodigo();
    }

    /** RNF01: una cuenta inactiva no puede abrir sesión */
    @Override
    public boolean isEnabled() {
        return usuario.isActivo();
    }

    public Integer id() {
        return usuario.getId();
    }

    public Rol rol() {
        return usuario.getRol();
    }
}
