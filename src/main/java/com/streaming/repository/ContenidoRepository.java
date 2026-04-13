package com.streaming.repository;

import com.streaming.model.Contenido;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de Contenidos: La magia de Spring Data MongoDB.
 * 
 * En arquitecturas clásicas con SQL y JDBC, aquí escribiríamos un DAO 
 * manual repleto de sentencias restrictivas "SELECT * FROM contenidos c JOIN ... WHERE ...".
 * 
 * Con el paradigma declarativo "Repository" en Spring Data, delegamos el mapeo de los 
 * datos (O/RM) a contenedores automáticos. La interfaz MongoRepository ya trae provistos
 * más de 20 métodos básicos (save, findAll, deleteById) sin tener que escribir
 * ninguna query nativa expuesta a vulnerabilidades de Inyección SQL/NoSQL.
 */
@Repository
public interface ContenidoRepository extends MongoRepository<Contenido, String> {
    /**
     * Parseo Abstract Syntax Tree (AST).
     * 
     * Spring Data lee el nombre literal de este método ("findByGenerosContaining")
     * y genera automáticamente EN TIEMPO DE EJECUCIÓN la siguiente consulta MongoDB:
     * 
     *     db.contenidos.find({ "generos": { $in: [?0] } })
     * 
     * Jamás nos ensuciamos las manos escribiendo JSON queries explícitos ni
     * sentencias duras ("hard-coded"). El código es inmune a errores de tipografía
     * y portable a otras BD modificando sólo la firma de la extensión.
     */
    List<Contenido> findByGenerosContaining(String genero);
}