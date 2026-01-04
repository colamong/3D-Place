package com.colombus.world.controller;

import com.colombus.world.dto.response.UrlResponse;
import com.colombus.world.service.WorldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/world")
@RequiredArgsConstructor
public class WorldController {
    private final WorldService worldService;

    @GetMapping("/{world}/{lod}/{chunkId}")
    public ResponseEntity<UrlResponse> get(
            @PathVariable String world,
            @PathVariable int lod,
            @PathVariable String chunkId) {

        UrlResponse response = worldService.get(world, lod, chunkId);
        return ResponseEntity.ok(response);
    }
}
