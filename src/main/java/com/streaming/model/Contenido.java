package com.streaming.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * Modelo de Dominio principal: Contenido (Películas/Series).
 * 
 * ¿Por qué MongoDB (Documental) y no SQL (Relacional) para este catálogo?
 * En una base de datos relacional (como MySQL o PostgreSQL), una película con múltiples
 * géneros, un elenco de varios actores y configuraciones de metadatos variables
 * requeriría como mínimo 4 tablas altamente normalizadas:
 * 1. Tabla 'contenidos'
 * 2. Tabla 'generos'
 * 3. Tabla intermedia 'r_contenidos_generos' para la relación Many-To-Many
 * 4. Tabla 'metadatos' (o un bloque JSON enorme si la DB lo soporta)
 * 5. Tabla 'elenco'
 * 
 * Reconstruir UNA sola película en la web obligaría al motor de base de datos a ejecutar
 * costosas operaciones JOIN, arrastrando pesadamente el tiempo de lectura.
 * 
 * EN MONGODB (Diseño Embebido):
 * Todo reside en UN SOLO DOCUMENTO BSON (colección "contenidos"). Al recuperar
 * la película, nos traemos los arrays y sub-objetos anidados instantáneamente
 * garantizando lecturas O(1) en disco duro, ideal para catálogos web masivos.
 */
@Document(collection = "contenidos")
public class Contenido {

    @Id
    private String id;

    @Field("titulo")
    private String titulo;

    @Field("tipo")
    private String tipo;

    /**
     * RELACIÓN EMBEBIDA (Array de Strings).
     * En SQL requeriría una tabla intermedia para One-To-Many. Aquí simplemente es
     * una matriz BSON dentro del documento madre. Mongo permite crear índices
     * multikey para buscar rápidamente contenidos iterando superficialmente el array.
     */
    @Field("generos")
    private List<String> generos;

    @Field("director")
    private String director;

    @Field("anioEstreno")
    private Integer anioEstreno;

    @Field("elenco")
    private List<Actor> elenco;

    /**
     * RELACIÓN EMBEBIDA (Sub-documento BSON 1-a-1).
     * Al encapsularlo aquí, evitamos lecturas a colecciones terciarias.
     */
    @Field("metadatos")
    private Metadatos metadatos;

    public Contenido() {}

    public Contenido(String titulo, String tipo, List<String> generos, 
                     String director, Integer anioEstreno) {
        this.titulo = titulo;
        this.tipo = tipo;
        this.generos = generos;
        this.director = director;
        this.anioEstreno = anioEstreno;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public List<String> getGeneros() { return generos; }
    public void setGeneros(List<String> generos) { this.generos = generos; }
    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }
    public Integer getAnioEstreno() { return anioEstreno; }
    public void setAnioEstreno(Integer anioEstreno) { this.anioEstreno = anioEstreno; }
    public List<Actor> getElenco() { return elenco; }
    public void setElenco(List<Actor> elenco) { this.elenco = elenco; }
    public Metadatos getMetadatos() { return metadatos; }
    public void setMetadatos(Metadatos metadatos) { this.metadatos = metadatos; }

    public static class Actor {
        private String nombre;
        private String papel;

        public Actor() {}

        public Actor(String nombre, String papel) {
            this.nombre = nombre;
            this.papel = papel;
        }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getPapel() { return papel; }
        public void setPapel(String papel) { this.papel = papel; }
    }

    public static class Metadatos {
        private String calidad;
        private List<String> idiomas;
        private List<String> subtitulos;

        public Metadatos() {}

        public Metadatos(String calidad, List<String> idiomas, List<String> subtitulos) {
            this.calidad = calidad;
            this.idiomas = idiomas;
            this.subtitulos = subtitulos;
        }

        public String getCalidad() { return calidad; }
        public void setCalidad(String calidad) { this.calidad = calidad; }
        public List<String> getIdiomas() { return idiomas; }
        public void setIdiomas(List<String> idiomas) { this.idiomas = idiomas; }
        public List<String> getSubtitulos() { return subtitulos; }
        public void setSubtitulos(List<String> subtitulos) { this.subtitulos = subtitulos; }
    }
}