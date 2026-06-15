CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    last_login TIMESTAMP
);

-- Créer un utilisateur par défaut
INSERT INTO users (username, password, full_name, email, role, active)
VALUES (
    'viewer',
    '$2a$12$ZySs/33V.lusmnDiQELCnOZioSBSCwg4K56pr23Ziux3eGIhusqbK', -- password: viewer123
    'Utilisateur Viewer',
    'oualoumidjeupisne@gmail.com',
    'USER',
    true
);