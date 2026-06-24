package com.krakow.theaters.service;

import com.krakow.theaters.dto.LoginRequest;
import com.krakow.theaters.dto.RegisterRequest;
import com.krakow.theaters.dto.UserDto;
import com.krakow.theaters.model.Play;
import com.krakow.theaters.model.User;
import com.krakow.theaters.repository.PlayRepository;
import com.krakow.theaters.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PlayRepository playRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDto register(RegisterRequest request) {
        log.info("Rejestracja użytkownika: {}", request.getNickname());

        String password = request.getPassword() != null ? request.getPassword().trim() : null;
        String nickname = request.getNickname() != null ? request.getNickname().trim() : null;
        String email = request.getEmail() != null ? request.getEmail().trim() : null;

        if (password == null || password.length() < 6) {
            throw new RuntimeException("Hasło musi mieć minimum 6 znaków");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email jest już zajęty");
        }

        if (userRepository.existsByNickname(nickname)) {
            throw new RuntimeException("Nickname jest już zajęty");
        }

        User newUser = new User();
        newUser.setNickname(nickname);
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(password));

        User savedUser = userRepository.save(newUser);
        log.info("Użytkownik zarejestrowany: {}", savedUser.getNickname());

        return mapToDto(savedUser);
    }

    @Transactional(readOnly = true)
    public UserDto login(LoginRequest request) {
        log.info("Próba logowania użytkownika - nickname: {}, email: {}", request.getNickname(), request.getEmail());

        String nickname = request.getNickname() != null ? request.getNickname().trim() : null;
        String email = request.getEmail() != null ? request.getEmail().trim() : null;
        String password = request.getPassword() != null ? request.getPassword().trim() : null;

        if (email == null || email.isEmpty()) {
            throw new RuntimeException("Email jest wymagany");
        }
        if (nickname == null || nickname.isEmpty()) {
            throw new RuntimeException("Nickname jest wymagany");
        }
        if (password == null || password.isEmpty()) {
            throw new RuntimeException("Hasło jest wymagane");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.warn("Użytkownik z emailem {} nie istnieje - wymaga rejestracji", email);
            throw new RuntimeException("EMAIL_NOT_FOUND");
        }

        User user = userOpt.get();

        if (!user.getNickname().equals(nickname)) {
            log.warn("Nieprawidłowy nickname dla email: {}", email);
            throw new RuntimeException("Nieprawidłowy nickname lub email");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Nieprawidłowe hasło dla użytkownika: {}", user.getNickname());
            throw new RuntimeException("Nieprawidłowe hasło");
        }

        log.info("Użytkownik zalogowany: {} ({})", user.getNickname(), user.getEmail());
        return mapToDto(user);
    }

    public Optional<UserDto> getUserByEmail(String email) {
        return userRepository.findByEmail(email).map(this::mapToDto);
    }

    public Optional<UserDto> getUserById(Long id) {
        return userRepository.findById(id).map(this::mapToDto);
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    public void addFavorite(Long userId, String playId) {
        log.info("Dodawanie ulubionego - userId: {}, source: {}", userId, playId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Play> plays = playRepository.findBySource(playId);
        if (plays.isEmpty()) {
            throw new RuntimeException("Play not found with source: " + playId);
        }

        Play play = plays.get(0);
        user.getFavoritePlays().add(play);
        userRepository.save(user);

        log.info("Ulubiony dodany: {} dla użytkownika {}", play.getTitle(), user.getNickname());
    }

    @Transactional
    public void removeFavorite(Long userId, String playId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Play> plays = playRepository.findBySource(playId);
        if (plays.isEmpty()) {
            throw new RuntimeException("Play not found with source: " + playId);
        }

        Play play = plays.get(0);
        user.getFavoritePlays().remove(play);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Set<Play> getUserFavorites(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Play> favorites = user.getFavoritePlays();
        org.hibernate.Hibernate.initialize(favorites);
        return favorites;
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, String playId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return user.getFavoritePlays().stream()
                .anyMatch(play -> play.getSource().equals(playId));
    }

    @Transactional
    public UserDto updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setAvatarUrl(avatarUrl);
        User savedUser = userRepository.save(user);

        return mapToDto(savedUser);
    }

    private UserDto mapToDto(User user) {
        return new UserDto(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getAvatarUrl()
        );
    }
}