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
public class CanonicalPageController {

    private static final MediaType HTML_UTF_8 = new MediaType("text", "html", StandardCharsets.UTF_8);
    private final Resource homePage = new ClassPathResource("static/index.html");
    private final Resource methodologyPage = new ClassPathResource("static/metodologia.html");

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> serveCanonicalHome() {
        return ResponseEntity.ok()
                .contentType(HTML_UTF_8)
                .header(HttpHeaders.CONTENT_LANGUAGE, "it-IT")
                .body(homePage);
    }

    @GetMapping("/index.html")
    ResponseEntity<Void> redirectIndexToCanonicalHome() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/"))
                .build();
    }

    @GetMapping(value = "/metodologia", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> serveMethodologyPage() {
        return ResponseEntity.ok()
                .contentType(HTML_UTF_8)
                .header(HttpHeaders.CONTENT_LANGUAGE, "it-IT")
                .body(methodologyPage);
    }

    @GetMapping({"/metodologia/", "/metodologia.html"})
    ResponseEntity<Void> redirectMethodologyAliases() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/metodologia"))
                .build();
    }
}
