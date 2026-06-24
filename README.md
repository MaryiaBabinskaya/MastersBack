# Teatralna Plotka — Backend

> Part of **Krakowska Lornetka** — one place for the entire Kraków theater scene.

Krakow has 13 major theaters with a permanent repertoire and many more beyond that. Finding a specific play, checking if it's suitable for kids, or discovering what's on this weekend used to mean visiting each website separately. **Krakowska Lornetka** solves that! One place for the entire Krakow theater scene :)

Since theaters didn't want to share data via JSON or provide public APIs, custom parsers had to be written for each of Kraków's major theaters with a permanent repertoire.

**Current database stats:**
- 🎭 959 performances across all theaters
- 🏛️ 13 theaters covered
- 👤 User accounts with favorites and reviews

---

## Tech Stack

- **Java 17** + **Spring Boot**
- **SQLite** with JPA/Hibernate — lightweight database, no external server needed
- **Jsoup** — HTML scraping library used to parse theater websites
- **BCrypt** — all user passwords are hashed, never stored in plain text

---

## Getting Started

```bash
./mvnw spring-boot:run
```

App runs at `http://localhost:8080`.

---

## Project Structure

```
src/main/java/com/krakow/theaters/
├── controller/    # REST endpoints
├── service/       # business logic + theater parsers
├── model/         # database entities (Play, Theatre, User, Review, Ticket)
├── repository/    # Spring Data interfaces
└── dto/           # data transfer objects
```

> **Entities** are Java classes that map directly to database tables — each field becomes a column, each object becomes a row.

---

## Supported Theaters

| ID | Theater |
|----|---------|
| TH-KRAKOW | Teatr w Krakowie (Słowacki Theater) |
| TH-BAGATELA | Teatr Bagatela |
| TH-STARY | Stary Teatr |
| TH-AST | Teatr AST |
| TH-LAZNIA-NOWA | Teatr Łaźnia Nowa |
| TH-NOWY | Teatr Nowy Proxima |
| TH-SCENA-STU | Scena STU |
| TH-OPERA-KRAKOW | Opera Krakowska |
| TH-BARAKAH | Teatr Barakah |
| TH-KTO | Teatr KTO |
| TH-LUDOWY | Teatr Ludowy |
| TH-GROTESKA | Teatr Groteska |
| TH-VARIETE | Teatr Variete |

---

## API Endpoints

### Plays

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/plays` | All plays grouped by title |
| GET | `/api/v1/plays/count-by-theatre` | Number of performances per theater |

### Import — title, scene, dates

Each theater has an import endpoint that fetches the repertoire (title, scene, showtimes):

```
GET /api/v1/teatr-w-krakowie/import
GET /api/v1/bagatela/import
GET /api/v1/kto/import
GET /api/v1/groteska/import
...
```

### Enrich — description, cast, and more

After import, a second pass fetches full details for each play (description, cast, duration, gallery, YouTube trailer):

```
GET /api/v1/{theater}/enrich
```

### Users

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/users/register` | Register |
| POST | `/api/v1/users/login` | Login |
| GET | `/api/v1/users/{id}/favorites` | Get favorites |
| POST | `/api/v1/users/{id}/favorites/{source}` | Add to favorites |
| DELETE | `/api/v1/users/{id}/favorites/{source}` | Remove from favorites |

### Reviews

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/reviews/{playSource}` | Get reviews for a play |
| POST | `/api/v1/reviews` | Add a review |

---

## Security & Best Practices

- Passwords hashed with **BCrypt**
- DTO pattern — internal entities never exposed directly to the API
- `@Transactional` on all write operations
- Lazy loading handled correctly within transaction scope
- CORS configured for frontend integration
