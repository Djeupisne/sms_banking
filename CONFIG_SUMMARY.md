# ✅ Configuration Mise à Jour - Orabank SMS Banking

## 📋 Résumé des Mises à Jour

Tous les fichiers de configuration ont été synchronisés avec `application-dev.yml`.

---

## 🔑 Valeurs de Configuration (application-dev.yml)

### Base de Données PostgreSQL
- **Host**: `localhost`
- **Port**: `5432`
- **Database**: `orabank_sms_dev`
- **Username**: `orabank_user`
- **Password**: `secure_password`

### Redis
- **Host**: `redis-11133.c8.us-east-1-4.ec2.cloud.redislabs.com`
- **Port**: `11133`
- **Password**: `WKJdeilasGOWkXJWOHwqcRV7X5uWwQgF`
- **SSL**: `true`

### Sécurité & Secrets
| Clé | Valeur |
|-----|--------|
| `webhook.secret.key` | `Doz8yeyMKoscnvpcOSZKGFgeUbV6AdUnPXnlalGwnNBXdT4vS7YdIYNbWVcY8HYp` |
| `otp.secret` | `O7Z7S7N7T7E7N7C7R7Y7P7T7K7E7Y` |
| `encryption.key` | `01234567890123456789012345678901` |

### Authentification
| Rôle | Username | Password |
|------|----------|----------|
| **Admin** | `admin` | `admin123` |
| **User** | `user` | `user123` |

### Twilio (Développement)
- **Account SID**: `ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX`
- **Auth Token**: `your_dev_auth_token`
- **Phone Number**: `+1234567890`

### Core Banking API
- **Base URL**: `http://localhost:8082/api`

---

## 📁 Fichiers Mis à Jour

### 1. `.env`
✅ Synchronisé avec `application-dev.yml`

### 2. `GUIDE_COMPLET_POSTMAN.md`
✅ Variables Postman mises à jour avec les vraies valeurs
✅ Scripts de signature HMAC corrigés
✅ Exemples de tests actualisés

### 3. `postman-collection.json`
✅ Variables d'environnement pré-configurées
✅ Nouvelles variables ajoutées :
   - `user_username` / `user_password`
   - `otp_master_secret`
   - `encryption_key`

---

## 🚀 Démarrage Rapide

### 1. Démarrer les Services
```bash
docker-compose up -d postgres redis
```

### 2. Lancer l'Application
```bash
cd /workspace
mvn spring-boot:run
```

### 3. Importer la Collection Postman
1. Ouvrez Postman
2. Cliquez sur **"Import"**
3. Sélectionnez `/workspace/postman-collection.json`
4. Activez l'environnement `Orabank SMS Banking API`

### 4. Vérifier la Configuration
```bash
curl http://localhost:8080/health
```

---

## 🧪 Tests Rapides avec Postman

### Health Check (Sans Auth)
- **Endpoint**: `GET {{base_url}}/health`
- **Attendu**: Status 200, tous les services UP

### Consulter Solde (Webhook Signé)
- **Endpoint**: `POST {{base_url}}/api/sms/webhook`
- **Body**: 
```json
{
  "from": "{{test_phone}}",
  "to": "+22501010101",
  "body": "SOLDE?"
}
```
- La signature HMAC est générée automatiquement

### Liste Clients (Admin Auth)
- **Endpoint**: `GET {{base_url}}/api/admin/clients`
- **Auth**: Basic (`admin` / `admin123`)

---

## 🔍 Vérification des Secrets

Pour vérifier que les secrets correspondent :

```bash
# Dans application-dev.yml
grep -A2 "webhook:" src/main/resources/application-dev.yml
grep "secret:" src/main/resources/application-dev.yml
grep "password:" src/main/resources/application-dev.yml

# Dans .env
grep "WEBHOOK_SECRET_KEY" .env
grep "OTP_MASTER_SECRET" .env
grep "ENCRYPTION_KEY" .env
```

---

## 📊 Tableau de Correspondance

| Variable | .env | application-dev.yml | Postman |
|----------|------|---------------------|---------|
| Webhook Secret | ✅ | ✅ | ✅ |
| OTP Secret | ✅ | ✅ | ✅ |
| Encryption Key | ✅ | ✅ | ✅ |
| Admin Password | ✅ | ✅ | ✅ |
| User Password | ✅ | ✅ | ✅ |
| DB Password | ✅ | ✅ | N/A |
| Redis Password | ✅ | ✅ | N/A |

---

## ✨ Tout est Prêt !

Votre système est maintenant correctement configuré pour :
- ✅ Développement local
- ✅ Tests avec Postman
- ✅ Webhooks sécurisés avec HMAC
- ✅ Authentification Admin/User
- ✅ Connexion PostgreSQL & Redis

**Prochaine étape** : Lancez l'application et testez avec Postman !
