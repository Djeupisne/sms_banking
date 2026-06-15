# Orabank SMS Banking

Système de banque mobile par SMS pour Orabank, permettant aux clients d'effectuer des opérations bancaires via des messages SMS.

## Fonctionnalités

- **SOLDE?** - Consulter le solde du compte
- **HISTO** - Obtenir les 5 dernières transactions
- **OTP** - Générer et recevoir un code OTP à 6 chiffres
- **TRANSFER X** - Effectuer un virement de X FCFA vers Mobile Money
- **HELP** - Afficher la liste des commandes disponibles

## Technologies

- **Backend**: Spring Boot 3.1.5, Java 17
- **Base de données**: PostgreSQL 15
- **Cache**: Redis 7
- **SMS Gateway**: Twilio (avec fallback Orange)
- **Sécurité**: Rate limiting, OTP TOTP, chiffrement AES-256
- **Migration DB**: Flyway
- **Tests**: JUnit 5, Testcontainers

## Prérequis

- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- Redis 7+
- PostgreSQL 15+

## Configuration

Copiez le fichier `.env` et remplissez les variables d'environnement :

```bash
cp .env.example .env
```

Variables requises :
- `TWILIO_ACCOUNT_SID`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_PHONE_NUMBER`
- `ORANGE_API_KEY`
- `CORE_BANKING_API_URL`
- `ENCRYPTION_KEY` (32 caractères pour AES-256)

## Démarrage

### Avec Docker Compose

```bash
docker-compose up -d
mvn spring-boot:run
```

### Manuellement

```bash
# Démarrer PostgreSQL et Redis
docker-compose up -d postgres redis

# Lancer l'application
mvn clean install
mvn spring-boot:run
```

## API Endpoints

- `POST /api/sms/webhook` - Webhook pour réception SMS
- `GET /api/admin/clients` - Liste des clients (admin)
- `GET /health` - Health check
- `GET /swagger-ui.html` - Documentation OpenAPI

## Sécurité

- Rate limiting: 5 requêtes/minute par numéro
- OTP valable 5 minutes (TOTP RFC 6238)
- Chiffrement AES-256 pour les numéros de compte
- Logs masqués pour les données sensibles

## Tests

```bash
# Tests unitaires
mvn test

# Tests d'intégration
mvn verify -Pintegration
```

## Structure du projet

```
src/
├── main/
│   ├── java/com/orabank/smsbanking/
│   │   ├── controller/      # Contrôleurs REST
│   │   ├── service/         # Services métier
│   │   ├── repository/      # Repositories JPA
│   │   ├── entity/          # Entités JPA
│   │   ├── dto/             # DTOs request/response
│   │   ├── security/        # Sécurité (OTP, RateLimit, Encryption)
│   │   ├── gateway/         # Gateways externes (SMS, Core Banking)
│   │   ├── config/          # Configurations Spring
│   │   ├── exception/       # Exceptions personnalisées
│   │   ├── mapper/          # MapStruct mappers
│   │   └── util/            # Utilitaires
│   └── resources/
│       ├── db/migration/    # Scripts Flyway
│       └── application*.yml # Configurations Spring
└── test/
    └── java/com/orabank/smsbanking/
        ├── unit/            # Tests unitaires
        └── integration/     # Tests d'intégration
```

## Licence

Propriétaire - Orabank 2026