package com.example.calcolatorefire.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthorPageController {

    private static final MediaType HTML_UTF_8 = new MediaType("text", "html", StandardCharsets.UTF_8);
    private final Resource authorPage = new ClassPathResource("static/autore/stefano-liga.html");

    @GetMapping(value = "/autore/stefano-liga", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> serveCanonicalAuthorPage() {
        return ResponseEntity.ok()
                .contentType(HTML_UTF_8)
                .header(HttpHeaders.CONTENT_LANGUAGE, "it-IT")
                .body(authorPage);
    }

    @GetMapping({"/autore/stefano-liga/", "/autore/stefano-liga.html"})
    ResponseEntity<Void> redirectAuthorAliases() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/autore/stefano-liga"))
                .build();
    }

    @GetMapping({"/autore", "/autore/"})
    ResponseEntity<Void> redirectAuthorIndexToProfile() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/autore/stefano-liga"))
                .build();
    }
}
