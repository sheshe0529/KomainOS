package com.komainos.credencial.repository;

import com.komainos.credencial.model.Credencial;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredencialRepositorio extends JpaRepository<Credencial, Integer> {

    @EntityGraph(attributePaths = {"secreto"})
    Optional<Credencial> findConSecretoById(Integer id);
}
