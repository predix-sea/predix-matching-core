package com.predix.matching.controller;

import com.predix.matching.controller.dto.ApiResponse;
import com.predix.matching.controller.dto.ExecutionTaskResponse;
import com.predix.matching.controller.dto.PageResponse;
import com.predix.matching.domain.enums.ExecutionTaskStatus;
import com.predix.matching.domain.enums.ExecutionTaskType;
import com.predix.matching.service.ExecutionTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/execution-tasks")
@RequiredArgsConstructor
@Tag(name = "Execution Tasks", description = "On-chain execution task management")
public class ExecutionTaskController {

    private final ExecutionTaskService executionTaskService;

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry execution task")
    public ApiResponse<ExecutionTaskResponse> retry(@PathVariable UUID id) {
        return ApiResponse.success(executionTaskService.retry(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get execution task")
    public ApiResponse<ExecutionTaskResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(executionTaskService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List execution tasks")
    public ApiResponse<PageResponse<ExecutionTaskResponse>> list(
            @RequestParam(required = false) ExecutionTaskStatus status,
            @RequestParam(required = false) ExecutionTaskType taskType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(executionTaskService.list(status, taskType, page, size));
    }
}
