package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.AccessDeniedException;
import com.khs.weeklyreport.service.NotFoundException;
import com.khs.weeklyreport.service.StaleCardException;
import com.khs.weeklyreport.web.dto.KanbanDtos;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(e.getMessage(), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(e.getMessage(), null));
    }

    /** 내가 띄워둔 사이에 남이 먼저 고쳤다. 서버의 현재 값을 함께 돌려준다. */
    @ExceptionHandler(StaleCardException.class)
    public ResponseEntity<KanbanDtos.ConflictView> handleStale(StaleCardException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new KanbanDtos.ConflictView(e.getMessage(), e.current()));
    }

    /** JPA 가 버전 불일치를 먼저 잡은 경우. 현재 값까지는 알 수 없다. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("다른 사람이 먼저 수정했습니다. 새로고침한 뒤 다시 시도해주세요.", null));
    }

    /** 유일 제약 위반(중복 초대 등). 사용자에게는 원인을 짧게만 알린다. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("이미 처리된 요청이거나 중복된 값입니다.", null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(body(e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(body("입력값을 확인해주세요.", fields));
    }

    private Map<String, Object> body(String message, Map<String, String> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("message", message);
        if (fields != null) {
            payload.put("fields", fields);
        }
        return payload;
    }
}
