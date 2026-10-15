package com.flyingangels.backend.api;

import com.flyingangels.backend.athlete.AthleteService.AthleteNotFoundException;
import com.flyingangels.backend.event.EventService.EventNotFoundException;
import com.flyingangels.backend.event.EventService.EventResultNotFoundException;
import com.flyingangels.backend.event.EventService.DuplicateResultException;
import com.flyingangels.backend.meet.MeetService.MeetNotFoundException;
import com.flyingangels.backend.event.EventCategoryService.EventCategoryNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;
import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(AthleteNotFoundException.class)
	ResponseEntity<ApiErrorResponse> athleteNotFound(AthleteNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "ATHLETE_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler({EventNotFoundException.class, EventResultNotFoundException.class, MeetNotFoundException.class,
			EventCategoryNotFoundException.class})
	ResponseEntity<ApiErrorResponse> eventNotFound(Exception exception, HttpServletRequest request) {
		String code = exception instanceof EventCategoryNotFoundException
				? "CATEGORY_NOT_FOUND" : "EVENT_NOT_FOUND";
		return error(HttpStatus.NOT_FOUND, code, exception.getMessage(), request);
	}

	@ExceptionHandler(DuplicateResultException.class)
	ResponseEntity<ApiErrorResponse> duplicateResult(DuplicateResultException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "ATHLETE_ALREADY_ASSIGNED", exception.getMessage(), request);
	}

	@ExceptionHandler(com.flyingangels.backend.athlete.AthleteService.DuplicateAthleteException.class)
	ResponseEntity<ApiErrorResponse> duplicateAthlete(Exception exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "ATHLETE_ALREADY_EXISTS", exception.getMessage(), request);
	}

	@ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
			MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
	ResponseEntity<ApiErrorResponse> badRequest(Exception exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> validationFailure(MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
				.distinct()
				.collect(java.util.stream.Collectors.joining("; "));
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
	}

	@ExceptionHandler(IOException.class)
	ResponseEntity<ApiErrorResponse> importReadFailure(IOException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "IMPORT_READ_FAILED", "The uploaded file could not be read", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
			HttpServletRequest request) {
		return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", exception.getMessage(), request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
			HttpServletRequest request) {
		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI()));
	}
}