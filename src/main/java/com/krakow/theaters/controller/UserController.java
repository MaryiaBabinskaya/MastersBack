package com.krakow.theaters.controller;

import com.krakow.theaters.dto.ErrorResponse;
import com.krakow.theaters.dto.LoginRequest;
import com.krakow.theaters.dto.RegisterRequest;
import com.krakow.theaters.dto.UserDto;
import com.krakow.theaters.model.Play;
import com.krakow.theaters.service.FileStorageService;
import com.krakow.theaters.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024;

    private final UserService userService;
    private final FileStorageService fileStorageService;

    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            UserDto result = userService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message != null && message.contains("Nickname jest już zajęty")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new ErrorResponse("NICKNAME_TAKEN", message));
            } else if (message != null && message.contains("Email jest już użyewany")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new ErrorResponse("EMAIL_TAKEN", message));
            } else if (message != null && message.contains("Hasło musi mieć minimum 6 znaków")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        new ErrorResponse("PASSWORD_TOO_SHORT", message));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse("INTERNAL_ERROR", "Wystąpił błąd podczas rejestracji"));
        }
    }

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            UserDto result = userService.login(request);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if ("EMAIL_NOT_FOUND".equals(message)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ErrorResponse("EMAIL_NOT_FOUND", "Użytkownik z tym emailem nie istnieje. Zarejestruj się."));
            }
            if (message != null && (message.contains("Nieprawidłowy") || message.contains("Nieprawidłowe hasło"))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        new ErrorResponse("INVALID_CREDENTIALS", message));
            }
            if (message != null && (message.contains("wymagany") || message.contains("wymagane"))) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        new ErrorResponse("MISSING_FIELD", message));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse("INTERNAL_ERROR", "Wystąpił błąd podczas logowania"));
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/email/{email}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable String email) {
        return userService.getUserByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/{userId}/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> addFavorite(@PathVariable Long userId, @RequestParam String playId) {
        try {
            userService.addFavorite(userId, playId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping(value = "/{userId}/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> removeFavorite(@PathVariable Long userId, @RequestParam String playId) {
        try {
            userService.removeFavorite(userId, playId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/{userId}/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Set<Play>> getUserFavorites(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(userService.getUserFavorites(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/{userId}/favorites/{playId}/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Boolean> isFavorite(@PathVariable Long userId, @PathVariable String playId) {
        try {
            return ResponseEntity.ok(userService.isFavorite(userId, playId));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/{userId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadAvatar(
            @PathVariable Long userId,
            @RequestParam("avatar") MultipartFile file) {
        try {
            ErrorResponse validationError = validateAvatarFile(file);
            if (validationError != null) {
                return ResponseEntity.badRequest().body(validationError);
            }
            String avatarUrl = fileStorageService.storeFile(file);
            UserDto updatedUser = userService.updateAvatar(userId, avatarUrl);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("User not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ErrorResponse("USER_NOT_FOUND", "Użytkownik nie znaleziony"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse("UPLOAD_FAILED", "Nie udało się załadować avatara"));
        }
    }

    private ErrorResponse validateAvatarFile(MultipartFile file) {
        if (file.isEmpty()) {
            return new ErrorResponse("EMPTY_FILE", "Plik jest pusty");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            return new ErrorResponse("FILE_TOO_LARGE", "Plik jest za duży (max 5MB)");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            return new ErrorResponse("INVALID_FILE_TYPE", "Dozwolone tylko pliki JPG i PNG");
        }
        return null;
    }
}
