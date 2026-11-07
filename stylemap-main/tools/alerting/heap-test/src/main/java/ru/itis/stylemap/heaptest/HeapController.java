package ru.itis.stylemap.heaptest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/heap")
final class HeapController {
    private final HeapEngine engine;

    HeapController(HeapEngine engine) { this.engine = engine; }

    private void localOnly(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remote) && !"::1".equals(remote) && !"0:0:0:0:0:0:0:1".equals(remote))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Loopback control only");
    }

    @GetMapping("/status")
    HeapEngine.Status status(HttpServletRequest request) {
        localOnly(request);
        return engine.status();
    }

    @PostMapping("/start")
    ResponseEntity<HeapEngine.Status> start(HttpServletRequest request) {
        localOnly(request);
        return ResponseEntity.status(engine.start() ? 202 : 409).body(engine.status());
    }

    @PostMapping("/release")
    HeapEngine.Status release(HttpServletRequest request) {
        localOnly(request);
        return engine.release();
    }
}
