package com.streaming.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document(collection = "contenidos")
public class Contenido {

    @Id
    private String id;

    @Field("titulo")
    private String titulo;

    @Field("tipo")
    private String tipo;

    @Field("generos")
    private List<String> generos;

    @Field("director")
    private String director;

    @Field("anioEstreno")
    private Integer anioEstreno;

    @Field("elenco")
    private List<Actor> elenco;

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