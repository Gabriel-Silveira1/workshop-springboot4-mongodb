# Workshop Mongo

A Spring Boot REST API showcasing how to build a small blogging domain (users, posts, comments) on top of **MongoDB** using **Spring Data MongoDB**. The project is intended as a learning exercise that walks through layered architecture, DTO mapping, exception handling, and custom MongoDB queries.

---

## Table of Contents

1. [Overview](#overview)
2. [Tech Stack](#tech-stack)
3. [Architecture](#architecture)
4. [Project Structure](#project-structure)
5. [Domain Model](#domain-model)
6. [Getting Started](#getting-started)
7. [Configuration](#configuration)
8. [Seed Data](#seed-data)
9. [API Reference](#api-reference)
10. [Search Examples](#search-examples)
11. [Error Format](#error-format)
12. [Roadmap / Possible Improvements](#roadmap--possible-improvements)

---

## Overview

The application exposes a REST API that allows clients to:

- Manage users (create, read, update, delete).
- Read posts authored by a user.
- Search posts by title or perform a full search across title, body, and comments within a date range.

Posts hold a denormalized list of comments (embedded documents) and reference their author through a lightweight `AuthorDTO` snapshot, while users keep a `@DBRef` list of their posts.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.x (Web MVC + Data MongoDB) |
| Build tool | Maven (wrapper included: `mvnw` / `mvnw.cmd`) |
| Database | MongoDB |
| Tests | Spring Boot Test starters |

## Architecture

The codebase follows a classic four-layer separation:

```
HTTP (resources)  ->  Service (business logic)  ->  Repository (data access)  ->  MongoDB
                              |
                              +-> DTOs cross the HTTP boundary
                              +-> Domain entities live in the service/repository layers
```

- **Resources** translate HTTP requests into service calls and shape the response.
- **Services** orchestrate use cases and own the business rules.
- **Repositories** are Spring Data interfaces with auto-generated and `@Query`-annotated finders.
- **DTOs** decouple the wire format from the persisted documents.
- **Exception handling** is centralized in a `@ControllerAdvice` that maps domain exceptions to a standard error payload.

## Project Structure

```
src/main/java/com/gabrielsilveira/workshopmongo
├── WorkshopmongoApplication.java     # Spring Boot entry point
├── config/
│   └── Instantiation.java            # CommandLineRunner that seeds sample data
├── domain/
│   ├── User.java                     # User document (collection: "user")
│   └── Post.java                     # Post document with embedded comments
├── dto/
│   ├── UserDTO.java                  # User payload for the API
│   ├── AuthorDTO.java                # Lightweight author snapshot (id + name)
│   └── CommentDTO.java               # Embedded comment payload
├── repository/
│   ├── UserRepository.java
│   └── PostRepository.java           # Custom @Query searches
├── service/
│   ├── UserService.java
│   ├── PostService.java
│   └── exception/
│       └── ObjectNotFoundException.java
└── resources/
    ├── UserResource.java             # /users endpoints
    ├── PostResource.java             # /posts endpoints
    ├── exception/
    │   ├── ResourceExceptionHandler.java
    │   └── StandardError.java
    └── util/
        └── URL.java                  # Query-string helpers (decode, parse date)
```

## Domain Model

```
User                                  Post
─────────                             ─────────
id        : String  (PK)              id        : String   (PK)
name      : String                    date      : Date
email     : String                    title     : String
posts     : List<Post>  @DBRef        body      : String
                                      author    : AuthorDTO    (denormalized)
                                      comments  : List<CommentDTO>  (embedded)
```

- `User.posts` is a lazy `@DBRef` list, so each user document stores references to its posts.
- `Post.author` and `Post.comments[].author` are denormalized snapshots (`AuthorDTO`) — they survive even if the original user is renamed.

## Getting Started

### Prerequisites

- **JDK 25** (or the version declared in `pom.xml`)
- **MongoDB** running locally on `mongodb://localhost:27017` (or override via configuration)
- The Maven wrapper requires no global Maven install

### Run MongoDB (Docker, optional)

```bash
docker run -d --name workshop-mongo -p 27017:27017 mongo:7
```

### Build & run

On Linux/macOS:

```bash
./mvnw spring-boot:run
```

On Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
```

The API starts on **http://localhost:8080**.

### Run the tests

```bash
./mvnw test
```

## Configuration

`src/main/resources/application.properties`

| Property | Default | Description |
| --- | --- | --- |
| `spring.mongodb.uri` | `mongodb://localhost:27017/workshop_mongo` | MongoDB connection string |

You can override any property via environment variables — for example:

```bash
SPRING_DATA_MONGODB_URI=mongodb://user:pass@host:27017/workshop_mongo \
  ./mvnw spring-boot:run
```

> ⚠️ The `Instantiation` runner **wipes the `user` and `post` collections on every startup** and re-inserts sample data. Disable or guard it with a profile before pointing the app at a non-development database.

## Seed Data

When the application boots, `Instantiation` populates three users and two posts authored by Maria, plus a few comments. This gives every endpoint something to return immediately after startup, with no manual seeding.

| User | Email |
| --- | --- |
| Maria Brown | maria@gmail.com |
| Alex Green | alex@gmail.com |
| Bob Grey | bob@gmail.com |

## API Reference

Base URL: `http://localhost:8080`

### Users — `/users`

| Method | Path | Description | Body | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/users` | List every user | – | `200 OK` — `List<UserDTO>` |
| `GET` | `/users/{id}` | Find user by id | – | `200 OK` — `UserDTO` |
| `POST` | `/users` | Create a user | `UserDTO` | `201 Created` — `Location` header |
| `PUT` | `/users/{id}` | Update name and email | `UserDTO` | `204 No Content` |
| `DELETE` | `/users/{id}` | Delete a user | – | `204 No Content` |
| `GET` | `/users/{id}/posts` | List posts authored by a user | – | `200 OK` — `List<Post>` |

#### `UserDTO` example

```json
{
  "id": "65f...",
  "name": "Maria Brown",
  "email": "maria@gmail.com"
}
```

### Posts — `/posts`

| Method | Path | Description | Response |
| --- | --- | --- | --- |
| `GET` | `/posts/{id}` | Find post by id | `200 OK` — `Post` |
| `GET` | `/posts/titlesearch?text=` | Find posts whose title matches a regex (case-insensitive) | `200 OK` — `List<Post>` |
| `GET` | `/posts/fullsearch?text=&minDate=&maxDate=` | Search title, body, and comments within a date range | `200 OK` — `List<Post>` |

#### Search parameters

- `text` — URL-encoded substring; matched as a case-insensitive regex.
- `minDate`, `maxDate` — `YYYY-MM-DD` (UTC). Defaults: `1970-01-01` and "today".

## Search Examples

```http
GET /posts/titlesearch?text=bom%20dia
```

```http
GET /posts/fullsearch?text=viagem&minDate=2018-03-01&maxDate=2018-03-31
```

The `fullsearch` query under the hood is:

```js
{
  $and: [
    { date: { $gte: <minDate> } },
    { date: { $lte: <maxDate + 1 day> } },
    { $or: [
        { title:          { $regex: <text>, $options: 'i' } },
        { body:           { $regex: <text>, $options: 'i' } },
        { 'comments.text':{ $regex: <text>, $options: 'i' } }
    ]}
  ]
}
```

## Error Format

Every handled exception is serialized as a `StandardError`:

```json
{
  "timestamp": 1709740800000,
  "status": 404,
  "error": "Não encontrado",
  "message": "Objeto não encontrado",
  "path": "/users/123"
}
```

Currently mapped:

| Exception | HTTP status |
| --- | --- |
| `ObjectNotFoundException` | `404 Not Found` |

## Roadmap / Possible Improvements

The following items are good follow-ups based on a code review against common best practices:

- Replace field injection with **constructor injection** across services and resources.
- Migrate `java.util.Date` to **`java.time`** types and use `DateTimeFormatter`.
- Replace `@RequestMapping(method=…)` with `@GetMapping`, `@PostMapping`, etc.
- Return **DTOs** (not domain entities) from `PostResource` and from `UserResource#findPosts`.
- Add **bean validation** (`@NotBlank`, `@Email`, `@Valid`) on DTOs and request bodies.
- Add **structured logging** (SLF4J) at service boundaries; keep emails out of logs.
- Guard the `Instantiation` seeder with `@Profile("dev")`.
- Expand the **exception handler** to cover validation errors and a generic fallback.
- Write **unit and integration tests** (target ≥75% branch coverage) for services, the URL utility, and search edge cases.
- Drop the duplicated `spring-boot-starter-data-mongodb` declaration in `pom.xml`.

---

Built as a learning project — feel free to fork it and use it as a starting point for your own MongoDB experiments.
