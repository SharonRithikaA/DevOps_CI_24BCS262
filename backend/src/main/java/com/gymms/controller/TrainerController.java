package com.gymms.controller;

import com.gymms.dto.TrainerDto;
import com.gymms.service.TrainerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trainers")
public class TrainerController {

    private final TrainerService trainerService;

    public TrainerController(TrainerService trainerService) {
        this.trainerService = trainerService;
    }

    @GetMapping
    public List<TrainerDto.Response> list() {
        return trainerService.list();
    }

    @GetMapping("/{id}")
    public TrainerDto.Response get(@PathVariable Long id) {
        return trainerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainerDto.Response create(@Valid @RequestBody TrainerDto.Request request) {
        return trainerService.create(request);
    }

    @PutMapping("/{id}")
    public TrainerDto.Response update(@PathVariable Long id, @Valid @RequestBody TrainerDto.Request request) {
        return trainerService.update(id, request);
    }

    @PutMapping("/{id}/members")
    public TrainerDto.Response assignMembers(@PathVariable Long id, @Valid @RequestBody TrainerDto.AssignRequest request) {
        return trainerService.assignMembers(id, request.memberIds());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        trainerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
