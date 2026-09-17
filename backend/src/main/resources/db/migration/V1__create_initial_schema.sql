CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('REQUESTER', 'ADMIN')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE request_types (
    type_id BIGSERIAL PRIMARY KEY,
    type_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE requests (
    request_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    type_id BIGINT NOT NULL,
    priority VARCHAR(20) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status VARCHAR(30) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'WAITING_USER', 'COMPLETED', 'REJECTED')),
    created_by BIGINT NOT NULL,
    assigned_to BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_requests_type
        FOREIGN KEY (type_id)
        REFERENCES request_types(type_id),

    CONSTRAINT fk_requests_creator
        FOREIGN KEY (created_by)
        REFERENCES users(user_id),

    CONSTRAINT fk_requests_assignee
        FOREIGN KEY (assigned_to)
        REFERENCES users(user_id)
);

CREATE TABLE comments (
    comment_id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    comment_text TEXT NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_comments_request
        FOREIGN KEY (request_id)
        REFERENCES requests(request_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);

CREATE TABLE status_history (
    history_id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    old_status VARCHAR(30)
        CHECK (old_status IS NULL OR old_status IN ('NEW', 'IN_PROGRESS', 'WAITING_USER', 'COMPLETED', 'REJECTED')),
    new_status VARCHAR(30) NOT NULL
        CHECK (new_status IN ('NEW', 'IN_PROGRESS', 'WAITING_USER', 'COMPLETED', 'REJECTED')),
    changed_by BIGINT NOT NULL,
    change_note TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_status_history_request
        FOREIGN KEY (request_id)
        REFERENCES requests(request_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_status_history_user
        FOREIGN KEY (changed_by)
        REFERENCES users(user_id)
);
