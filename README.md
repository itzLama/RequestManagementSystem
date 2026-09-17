# Masar | مسار

## Internal Service Request Management System

Masar is an internal service request management system developed as part of an IT internship project. It is intended to help employees submit and track internal service requests while allowing administrators to manage requests, assignments, statuses, comments, and reporting.

The current repository contains the Week 3 application foundation. Some request-management pages and business features are placeholders for future implementation.

## Main Users

- **Requester:** Intended to submit and track internal service requests.
- **Administrator:** Intended to manage requests, assignments, statuses, comments, and reporting.

## Tech Stack

### Main Technologies

- **Frontend:** Next.js 16.3.5, React 19.2.8, and TypeScript 5
- **Backend:** Java 21 and Spring Boot 4.1.1
- **Database:** PostgreSQL 16
- **Database migration:** Flyway

### Backend Libraries

- Spring Web MVC
- Spring Data JPA
- Spring Security
- Jakarta Bean Validation
- Lombok
- PostgreSQL JDBC driver

### Frontend Styling and Development

- Tailwind CSS 4
- ESLint 9

### Build and Local Infrastructure

- Maven 3.9.16 through Maven Wrapper 3.3.4
- npm with the committed `package-lock.json`
- Docker Compose for the local PostgreSQL service

## Project Structure

```text
RequestManagementSystem/
├── backend/
│   ├── .mvn/                         # Maven Wrapper configuration
│   ├── src/main/java/                # Spring Boot application
│   ├── src/main/resources/
│   │   ├── db/migration/             # Flyway migrations
│   │   └── application.properties    # Backend configuration
│   ├── src/test/                     # Backend tests
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml
├── frontend/
│   ├── app/                          # Next.js App Router pages
│   ├── components/                   # Shared UI components
│   ├── lib/                          # Frontend API utilities
│   ├── public/                       # Static assets and Masar logo
│   ├── package.json
│   └── package-lock.json
├── .env.example                      # Environment-variable reference
├── .gitignore
└── compose.yaml                      # PostgreSQL Docker service
```

## Prerequisites

Install the following software before running the project locally:

- **Java Development Kit 21**
- **Node.js and npm**
- **Docker with Docker Compose support**
- **Windows PowerShell**

A separate Maven installation is not required because the backend includes the Maven Wrapper.

## Environment Configuration

The backend and Docker Compose use the following PostgreSQL environment variables:

| Variable | Default or Required Value |
|---|---|
| `POSTGRES_HOST` | Defaults to `127.0.0.1` |
| `POSTGRES_PORT` | Defaults to `5432` |
| `POSTGRES_DB` | Defaults to `request_management` |
| `POSTGRES_USER` | Defaults to `request_user` |
| `POSTGRES_PASSWORD` | Required and must be supplied locally |

The repository contains `.env.example` as documentation only. It contains placeholder values and must not contain a real password.

Before starting PostgreSQL or the backend, set a local password in the relevant PowerShell terminal:

```powershell
$env:POSTGRES_PASSWORD = "choose-a-local-password"
```

Use the same password for Docker Compose and the Spring Boot backend.

If the other PostgreSQL settings are changed, set the same values for both processes:

```powershell
$env:POSTGRES_HOST = "127.0.0.1"
$env:POSTGRES_PORT = "5432"
$env:POSTGRES_DB = "request_management"
$env:POSTGRES_USER = "request_user"
$env:POSTGRES_PASSWORD = "choose-a-local-password"
```

Never commit a local `.env` file or any real credentials.

## Database Setup

PostgreSQL runs locally through Docker Compose using the official PostgreSQL 16 image. Database data is stored in the named Docker volume `postgres_data`.

Flyway manages the database schema and initial development data:

- `V1__create_initial_schema.sql` creates the five application tables.
- `V2__insert_initial_data.sql` inserts the initial users, request types, requests, comments, and status history.

The backend has Hibernate schema generation disabled with:

```properties
spring.jpa.hibernate.ddl-auto=none
```

Flyway is therefore responsible for database schema creation and migration. No manual SQL setup is required.

## Running the Project Locally

Run each application component in a separate PowerShell terminal.

### 1. Start PostgreSQL

From the repository root:

```powershell
$env:POSTGRES_PASSWORD = "choose-a-local-password"
docker compose up -d postgres
```

To check the container status:

```powershell
docker compose ps
```

### 2. Start the Spring Boot backend

Open another PowerShell terminal:

```powershell
cd backend
$env:POSTGRES_PASSWORD = "choose-a-local-password"
.\mvnw.cmd spring-boot:run
```

The backend starts at:

```text
http://localhost:8080
```

During startup, Flyway validates and applies any pending migrations.

### 3. Install the frontend dependencies

After cloning the repository, run this once from a new PowerShell terminal:

```powershell
cd frontend
npm.cmd ci
```

Run `npm.cmd ci` again only when dependencies need to be reinstalled or the lockfile changes.

### 4. Start the Next.js frontend

For normal frontend startup, open a PowerShell terminal and run:

```powershell
cd frontend
npm.cmd run dev
```

The frontend starts at:

```text
http://localhost:3000
```

The Next.js development server forwards `/api/*` requests to the backend at `http://localhost:8080`.

### 5. Open the application

Open the following address in a browser:

```text
http://localhost:3000/login
```

## Sample Development Accounts

The following accounts come from the Flyway development seed data. They are for local development and testing only.

| Name | Email | Role | Password |
|---|---|---|---|
| Sara Saad | `sara.saad@example.com` | Administrator | `Password123` |
| Nora Ahmed | `nora.ahmed@example.com` | Requester | `Password123` |
| Nouf Khaled | `nouf.khaled@example.com` | Administrator | `Password123` |

These are sample application credentials, not infrastructure or production credentials.

## Current Core Features

The following foundation functionality is currently implemented:

- Shared login page for administrators and requesters
- Authentication against users stored in PostgreSQL
- BCrypt password verification
- Rejection of invalid credentials and inactive users
- Server-side session authentication using Spring Security
- CSRF protection for state-changing requests
- Session restoration through `/api/auth/me`
- Logout with server-side session invalidation
- Protected application layout
- Role-based navigation and post-login redirects
- Masar branding and base application layout
- Flyway-managed database schema and development seed data
- JPA entities and repositories for:
  - Users
  - Request types
  - Requests
  - Comments
  - Status history

The request list, request creation form, request administration, assignments, status updates, comments, and reporting workflows are not yet implemented. Their current pages are Week 3 layout placeholders.

## Week 3 Foundation Status

The current Week 3 foundation includes:

- Repository and project setup
- Base frontend layout and navigation
- Environment-based configuration
- PostgreSQL connectivity
- Flyway schema and seed-data migrations
- JPA entities and repositories
- Spring Security authentication and authorization foundations
- Session and CSRF handling
- Lombok integration

## Notes

- The current setup is intended for local development.
- Production deployment and production SSO are outside the current project scope.
