# Migration JWT pour Webhooks SMS - Guide d'Utilisation

## 🎯 Résumé de l'Implémentation

Votre système de webhooks SMS a été migré avec succès de HMAC vers **JWT (JSON Web Tokens)**. Cette implémentation élimine le besoin de scripts pre-request dans Postman.

---

## 📦 Fichiers Créés/Modifiés

### Nouveaux Fichiers
1. **`JwtTokenService.java`** - Service de génération/validation JWT
2. **`WebhookTokenController.java`** - Endpoint pour générer des tokens
3. **`JwtTokenServiceTest.java`** - Tests unitaires complets

### Fichiers Modifiés
1. **`SmsWebhookController.java`** - Support dual JWT + HMAC
2. **`application.yml`** - Configuration JWT
3. **`pom.xml`** - Dépendances JWT (jjwt)

---

## 🔧 Configuration

### application.yml
```yaml
# Configuration JWT
jwt:
  secret: ${JWT_SECRET:orabank_sms_banking_jwt_secret_key_min_32_chars_secure}
  expiration-ms: 3600000  # 1 heure

# Méthode d'authentification
webhook:
  auth:
    method: ${WEBHOOK_AUTH_METHOD:jwt}  # "jwt" ou "hmac"
```

### Variables d'Environnement Requises
```bash
export JWT_SECRET="votre_clé_secrète_d_au_moins_32_caractères"
export WEBHOOK_AUTH_METHOD="jwt"
```

---

## 🚀 Utilisation dans Postman (SANS Script!)

### Étape 1 : Générer un Token JWT

**Requête :** `POST /api/auth/webhook-token`

**Headers :**
```
Content-Type: application/json
```

**Body (JSON) :**
```json
{
  "from": "+22891234567",
  "to": "ORABANK",
  "body": "SOLDE"
}
```

**Réponse :**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJib2R5SGFzaCI6IjcxMTIzMWNhNWVhNWM5NzIiLCJmcm9tIjoiKzIyODkxMjM0NTY3IiwidG8iOiJPUkFCQU5LIiwiaWF0IjoxNzc5OTYxOTc1LCJzdWIiOiJzbXMtd2ViaG9vayIsImV4cCI6MTc3OTk2NTU3NX0.1pwcwBsAW72OoH8rYjXrlmZ1BBQPTtfNbwYp81ivcow",
  "expiresIn": 3600000,
  "tokenType": "Bearer",
  "postmanInstructions": {
    "step1": "Copiez le token ci-dessus",
    "step2": "Dans Postman, allez dans l'onglet 'Authorization'",
    "step3": "Sélectionnez 'Bearer Token' comme type",
    "step4": "Collez le token dans le champ 'Token'",
    "step5": "Envoyez votre requête POST /api/sms/webhook"
  }
}
```

### Étape 2 : Tester le Webhook

**Requête :** `POST /api/sms/webhook`

**Onglet Authorization :**
- Type : `Bearer Token`
- Token : `{copiez le token généré}`

**Headers :** (automatiquement ajoutés par Postman)
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Body (JSON) :**
```json
{
  "from": "+22891234567",
  "to": "ORABANK",
  "body": "SOLDE"
}
```

⚠️ **Important :** Les données `from`, `to`, et `body` doivent correspondre EXACTEMENT à celles utilisées pour générer le token.

---

## 🔐 Claims JWT Inclus

Le token contient les claims suivants :

| Claim | Description | Exemple |
|-------|-------------|---------|
| `sub` | Sujet (toujours "sms-webhook") | `"sms-webhook"` |
| `iat` | Timestamp d'émission (secondes) | `1779961975` |
| `exp` | Timestamp d'expiration (secondes) | `1779965575` |
| `from` | Numéro émetteur du SMS | `"+22891234567"` |
| `to` | Numéro destinataire | `"ORABANK"` |
| `bodyHash` | Hash SHA-256 court du body | `"711231ca5ea5c972"` |

---

## 🔄 Mode Legacy HMAC

Pour maintenir la compatibilité avec l'ancien système HMAC :

```yaml
webhook:
  auth:
    method: hmac  # Active le mode HMAC legacy
```

Le contrôleur acceptera alors les headers :
- `X-Webhook-Signature`: Signature HMAC hexadécimale
- `X-Webhook-Timestamp`: Timestamp en millisecondes

---

## ✅ Tests Unitaires

Exécutez les tests :
```bash
mvn test -Dtest=JwtTokenServiceTest
```

**Résultats attendus :**
```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

---

## 🛡️ Sécurité

### Bonnes Pratiques Implémentées

1. **Clé JWT sécurisée** : Minimum 32 caractères requis
2. **Expiration automatique** : 1 heure par défaut
3. **Validation des claims** : from, to, bodyHash vérifiés
4. **Comparaison en temps constant** : Protection contre attaques timing
5. **Hash du body** : Le contenu complet n'est pas exposé dans le token

### Génération d'une Clé Sécurisée

```bash
# Linux/Mac
openssl rand -base64 32

# Ou utiliser un générateur en ligne
# https://generate-secret.vercel.app/32
```

---

## 📊 Comparaison HMAC vs JWT

| Fonctionnalité | HMAC (Legacy) | JWT (Nouveau) |
|----------------|---------------|---------------|
| Script Postman | ❌ Requis | ✅ Aucun |
| Standard | Custom | ✅ RFC 7519 |
| Claims embarqués | ❌ Non | ✅ Oui |
| Expiration | Via timestamp | ✅ Automatique |
| Outils supportés | Limités | ✅ Tous (Postman, curl, etc.) |
| Headers requis | 2 (Signature + Timestamp) | ✅ 1 (Authorization) |

---

## 🐛 Dépannage

### Erreur : "Token JWT invalide ou expiré"
- Vérifiez que le token n'a pas expiré (1 heure max)
- Régénérez un nouveau token via `/api/auth/webhook-token`

### Erreur : "Mismatch 'from'" / "Mismatch 'to'" / "Mismatch 'bodyHash'"
- Les données dans le body de la requête webhook doivent correspondre EXACTEMENT
- Attention aux espaces, majuscules/minuscules

### Erreur : "Header Authorization requis"
- Assurez-vous d'utiliser l'onglet **Authorization** de Postman
- Sélectionnez **Bearer Token** comme type
- Collez le token (sans le préfixe "Bearer ")

---

## 📝 Exemple curl

```bash
# 1. Générer un token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/webhook-token \
  -H "Content-Type: application/json" \
  -d '{
    "from": "+22891234567",
    "to": "ORABANK",
    "body": "SOLDE"
  }' | jq -r '.token')

echo "Token généré: $TOKEN"

# 2. Utiliser le token pour le webhook
curl -X POST http://localhost:8080/api/sms/webhook \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "from": "+22891234567",
    "to": "ORABANK",
    "body": "SOLDE"
  }'
```

---

## 🎉 Conclusion

Votre système de webhooks SMS est maintenant compatible JWT, offrant :
- ✅ **Aucun script pre-request** nécessaire dans Postman
- ✅ **Standard industriel** RFC 7519
- ✅ **Sécurité renforcée** avec validation des claims
- ✅ **Compatibilité ascendante** avec HMAC maintenu

Pour toute question, consultez la documentation ou les logs de l'application.
