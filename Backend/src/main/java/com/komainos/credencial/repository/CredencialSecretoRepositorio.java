package com.komainos.credencial.repository;

import com.komainos.credencial.model.CredencialSecreto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredencialSecretoRepositorio extends JpaRepository<CredencialSecreto, Integer> {
}
