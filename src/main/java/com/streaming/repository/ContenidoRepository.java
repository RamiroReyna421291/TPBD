package com.streaming.repository;

import com.streaming.model.Contenido;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContenidoRepository extends MongoRepository<Contenido, String> {
    List<Contenido> findByGenerosContaining(String genero);
}