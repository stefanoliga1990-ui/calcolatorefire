package com.example.calcolatorefire.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class GuidePageController {

    private static final MediaType HTML_UTF_8 = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final Pattern VALID_SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    @GetMapping(value = "/guide", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> serveGuideIndex() {
        Resource index = new ClassPathResource("static/guide.html");
        return ResponseEntity.ok()
                .contentType(HTML_UTF_8)
                .header(HttpHeaders.CONTENT_LANGUAGE, "it-IT")
                .body(index);
    }

    @GetMapping({"/guide/", "/guide.html"})
    ResponseEntity<Void> redirectGuideIndexAliases() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/guide"))
                .build();
    }

    @GetMapping(value = "/guida/{slug:[a-z0-9-]+}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> serveCanonicalGuide(@PathVariable String slug) {
        if (!guideExists(slug)) {
            return ResponseEntity.notFound().build();
        }
        Resource guide = new ClassPathResource("static/guida/" + slug + ".html");
        return ResponseEntity.ok()
                .contentType(HTML_UTF_8)
                .header(HttpHeaders.CONTENT_LANGUAGE, "it-IT")
                .body(guide);
    }

    @GetMapping({"/guida/{slug:[a-z0-9-]+}/", "/guida/{slug:[a-z0-9-]+}.html"})
    ResponseEntity<Void> redirectGuideAliases(@PathVariable String slug) {
        if (!guideExists(slug)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/guida/" + slug))
                .build();
    }

    private boolean guideExists(String slug) {
        return VALID_SLUG.matcher(slug).matches()
                && new ClassPathResource("static/guida/" + slug + ".html").exists();
    }
}
