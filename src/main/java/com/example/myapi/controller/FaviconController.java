package com.example.myapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 브라우저 특수 요청 처리
 * favicon, .well-known 등의 요청에 대해 204 No Content 반환
 */
@Controller
public class FaviconController {

    @GetMapping("favicon.ico")
    @ResponseBody
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping(".well-known/**")
    @ResponseBody
    public ResponseEntity<Void> wellKnown() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("robots.txt")
    @ResponseBody
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok("User-agent: *\nAllow: /");
    }
}
