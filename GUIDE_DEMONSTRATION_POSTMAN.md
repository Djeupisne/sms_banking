# 🎓 GUIDE DE DÉMONSTRATION POSTMAN - SOUTENANCE ORABANK SMS BANKING

## 📋 Contexte et Problématique

**Problème** : Vous n'avez pas les vraies clés API des opérateurs (Moov Africa Togo, Togocel) pour la démonstration lors de la soutenance.

**Solution** : Votre système est conçu avec une architecture **résiliente** qui permet de fonctionner en mode démonstration grâce à :
1. La validation JWT/HMAC des webhooks (sans besoin d'envoi SMS réel)
2. Le mock des gateways SMS dans les tests
3. La séparation claire entre la logique métier et l'envoi SMS physique

---

## ✅ CE QUE VOUS POUVEZ DÉMONTRER SANS CLÉS API

### 1. **Flux Complet de Traitement SMS** ✅
- Réception du webhook SMS
- Validation de l'authentification (JWT ou HMAC)
- Parsing de la commande (SOLDE, HISTO, TRANSFERT, OTP, etc.)
- Exécution de la logique métier
- Génération de la réponse

### 2. **Authentification et Sécurité** ✅
- Génération de tokens JWT via `/api/auth/webhook-token`
- Validation des signatures HMAC
- Rate limiting (5 requêtes/minute)
- Masquage des données sensibles dans les logs

### 3. **Commandes Métier** ✅
- `SOLDE` - Consultation de solde
- `HISTO` - Historique des transactions
- `OTP` - Génération de code OTP
- `TRANSFERT` - Virement vers Mobile Money
- `HELP` - Aide utilisateur

### 4. **Résilience et Fallback** ✅
- Circuit breaker Resilience4j
- Retry avec backoff exponentiel
- Bascule automatique Moov → Togocel si le primaire échoue

---

## 🚀 SCÉNARIO DE DÉMONSTRATION POSTMAN

### ÉTAPE 1 : Importer la Collection Postman

1. Ouvrez Postman
2. Cliquez sur **Import**
3. Sélectionnez le fichier : `/workspace/OraBank_SMS_Commands.json`
4. La collection "OraBank SMS Commands" apparaît avec 10 requêtes pré-configurées

---

### ÉTAPE 2 : Configurer l'Environnement de Démonstration

#### Option A : Mode JWT (Recommandé - Plus Simple)

Dans Postman, allez dans l'onglet **Authorization** de chaque requête et configurez :
- **Type** : `Bearer Token`
- **Token** : (vous le générerez à l'étape 3)

#### Option B : Mode API Key (Déjà configuré)

Les requêtes ont déjà le header :
```
X-API-Key: ORABANK_2024_SECURE_WEBHOOK_KEY_12345
```

**Variable d'environnement à définir** dans `application.yml` ou `.env` :
```yaml
webhook:
  api:
    key: ORABANK_2024_SECURE_WEBHOOK_KEY_12345
    enabled: true  # ← Activer pour la démo
```

---

### ÉTAPE 3 : Générer un Token JWT (Si Option A)

**Requête 0 : Générer le Token**

```http
POST http://localhost:8080/api/auth/webhook-token
Content-Type: application/json

{
  "from": "+22890000001",
  "to": "ORABANK",
  "body": "SOLDE"
}
```

**Réponse attendue :**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 3600000,
  "tokenType": "Bearer"
}
```

**Action :** Copiez le token et collez-le dans l'onglet **Authorization > Bearer Token** de toutes les requêtes.

---

### ÉTAPE 4 : Exécuter les Commandes Métier

#### 📊 Requête 1 : Consulter le Solde

```http
POST http://localhost:8080/api/sms/webhook
Authorization: Bearer {votre_token}
Content-Type: application/json

{
  "from": "+22890000001",
  "to": "ORABANK",
  "body": "SOLDE"
}
```

**Réponse attendue :**
```json
{
  "to": "+22890000001",
  "message": "ORABANK - Solde du compte COMPTE001: 150000 FCFA"
}
```

**Points à souligner :**
- ✅ Authentification validée
- ✅ Commande parsée correctement
- ✅ Logique métier exécutée (même sans SMS réel)
- ⚠️ Le SMS de réponse ne sera pas envoyé physiquement (pas de clé API), mais la réponse JSON est générée

---

#### 📜 Requête 2 : Historique des Transactions

```http
POST http://localhost:8080/api/sms/webhook
Authorization: Bearer {votre_token}
Content-Type: application/json

{
  "from": "+22890000001",
  "to": "ORABANK",
  "body": "HISTO COMPTE001"
}
```

**Réponse attendue :**
```json
{
  "to": "+22890000001",
  "message": "ORABANK - Dernières transactions:\n1. -5000 FCFA (Transfert)\n2. +10000 FCFA (Dépôt)\n..."
}
```

---

#### 🔐 Requête 3 : Générer un Code OTP

```http
POST http://localhost:8080/api/sms/webhook
Authorization: Bearer {votre_token}
Content-Type: application/json

{
  "from": "+22890000001",
  "to": "ORABANK",
  "body": "OTP"
}
```

**Réponse attendue :**
```json
{
  "to": "+22890000001",
  "message": "ORABANK - Votre code OTP est: 123456. Valable 5 min."
}
```

**Note :** Le code OTP est généré aléatoirement et stocké en Redis, même sans envoi SMS.

---

#### 💸 Requête 4 : Effectuer un Transfert

```http
POST http://localhost:8080/api/sms/webhook
Authorization: Bearer {votre_token}
Content-Type: application/json

{
  "from": "+22890000001",
  "to": "ORABANK",
  "body": "TRANSFERT 1000 COMPTE001 +22890000002 MOBILE"
}
```

**Réponse attendue :**
```json
{
  "to": "+22890000001",
  "message": "ORABANK - Transfert de 1000 FCFA vers +22890000002 effectué avec succès. Frais: 100 FCFA."
}
```

---

#### ❓ Requête 5 : Demander de l'Aide

```http
POST http://localhost:8080/api/sms/webhook
Authorization: Bearer {votre_token}
Content-Type: application/json

{
  "from": "+22890000001",
  "to": "ORABANK",
  "body": "HELP"
}
```

**Réponse attendue :**
```json
{
  "to": "+22890000001",
  "message": "ORABANK - Commandes disponibles:\n- SOLDE [COMPTE]\n- HISTO [COMPTE]\n- OTP\n- TRANSFERT X [...] \n- HELP"
}
```

---

## 🛡️ DÉMONSTRER LA SÉCURITÉ

### Test 1 : Requête Sans Authentification

```http
POST http://localhost:8080/api/sms/webhook
Content-Type: application/json

{
  "from": "+22890000001",
  "to": "ORABANK",
  "body": "SOLDE"
}
```

**Réponse attendue (401 Unauthorized) :**
```json
{
  "to": null,
  "message": "ORABANK - Erreur de sécurité: Header Authorization requis (Bearer Token)"
}
```

---

### Test 2 : Requête Avec Token Invalide

```http
POST http://localhost:8080/api/sms/webhook
Authorization: Bearer token_invalide
Content-Type: application/json

{
  "from": "+22890000001",
  "to": "ORABANK",
  "body": "SOLDE"
}
```

**Réponse attendue (401 Unauthorized) :**
```json
{
  "to": null,
  "message": "ORABANK - Erreur de sécurité: Token JWT invalide ou expiré"
}
```

---

### Test 3 : Rate Limiting (5 requêtes/minute)

Exécutez 6 requêtes rapides identiques :

```bash
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/sms/webhook \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"from":"+22890000001","to":"ORABANK","body":"SOLDE"}'
done
```

**À partir de la 6ème requête :**
```json
{
  "to": "+22890000001",
  "message": "ORABANK - Limite de requêtes dépassée. Maximum 5 requêtes par minute."
}
```

---

## 🔧 CONFIGURATION POUR LA DÉMONSTRATION

### Fichier `.env` ou Variables d'Environnement

```bash
# === AUTHENTIFICATION ===
WEBHOOK_AUTH_METHOD=jwt
JWT_SECRET=orabank_demo_jwt_secret_key_min_32_chars_secure_for_demo_only

# === API KEY (Optionnel) ===
WEBHOOK_API_KEY=ORABANK_2024_SECURE_WEBHOOK_KEY_12345
WEBHOOK_API_ENABLED=true

# === GATEWAYS SMS - MODE DÉMO ===
# Désactiver les gateways pour éviter les erreurs de connexion
MOOV_SMS_ENABLED=false
TOGOCEL_SMS_ENABLED=false

# OU laisser activé mais le système gérera l'échec gracieusement
MOOV_SMS_API_KEY=votre_cle_api_moov
MOOV_SMS_API_SECRET=votre_secret_api_moov
TOGOCEL_SMS_API_KEY=votre_cle_api_togocel
TOGOCEL_SMS_API_SECRET=votre_secret_api_togocel

# === BASE DE DONNÉES ===
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=orabank_sms
POSTGRES_USER=orabank_user
POSTGRES_PASSWORD=secure_password

# === REDIS ===
REDIS_HOST=localhost
REDIS_PORT=6379

# === CORE BANKING (Mock) ===
CORE_BANKING_BASE_URL=http://localhost:8081/api
CORE_BANKING_API_KEY=demo_key
CORE_BANKING_API_SECRET=demo_secret
```

---

### Application.yml - Configuration Démo

```yaml
# Mode développement
spring:
  profiles:
    active: dev

# Webhook - Mode JWT
webhook:
  auth:
    method: jwt
  api:
    key: ORABANK_2024_SECURE_WEBHOOK_KEY_12345
    enabled: true  # ← Activer pour simplifier la démo
  signature:
    enabled: true
    max-age-ms: 300000

# JWT
jwt:
  secret: orabank_demo_jwt_secret_key_min_32_chars_secure_for_demo_only
  expiration-ms: 3600000

# Gateways SMS - Mode Démo
moov:
  sms:
    enabled: false  # ← Désactiver pour éviter les erreurs
togocel:
  sms:
    enabled: false  # ← Désactiver pour éviter les erreurs

# Logging
logging:
  level:
    com.orabank.smsbanking: DEBUG  # ← Voir les détails dans les logs
```

---

## 📝 SCRIPT DE PRÉSENTATION ORALE

### Introduction (1 min)
> "Bonjour, je vais vous démontrer le système Orabank SMS Banking. Ce système permet aux clients d'effectuer des opérations bancaires via des SMS. Aujourd'hui, je vais vous montrer comment le système traite les commandes, même sans les clés API réelles des opérateurs, grâce à une architecture résiliente."

### Démonstration (5 min)

1. **Importer la collection Postman**
   > "J'importe la collection Postman qui contient 10 scénarios de test pré-configurés."

2. **Générer un token JWT**
   > "Je génère un token JWT via cet endpoint dédié. Ce token sera utilisé pour authentifier toutes les requêtes suivantes."

3. **Tester la commande SOLDE**
   > "J'envoie la commande SOLDE. Le système authentifie la requête, parse la commande, exécute la logique métier et retourne le solde. Notez que le SMS ne sera pas envoyé physiquement car nous n'avons pas les clés API, mais toute la chaîne de traitement est fonctionnelle."

4. **Montrer la sécurité**
   > "Je teste maintenant sans authentification... Comme attendu, le système rejette la requête avec une erreur 401."

5. **Expliquer l'architecture résiliente**
   > "Notre système utilise un Circuit Breaker Resilience4j. Si Moov échoue, il bascule automatiquement sur Togocel. En mode démo, les deux sont désactivés, mais le traitement métier fonctionne parfaitement."

### Conclusion (1 min)
> "Vous avez vu que le système est entièrement fonctionnel pour le traitement des commandes. L'envoi SMS physique nécessite uniquement les clés API des opérateurs, ce qui est une simple configuration. L'architecture est prête pour la production."

---

## 🎯 POINTS FORTS À METTRE EN AVANT

### 1. Architecture Microservices Résiliente
- Circuit Breaker (Resilience4j)
- Retry avec backoff exponentiel
- Rate limiting
- Fallback automatique Moov ↔ Togocel

### 2. Sécurité Renforcée
- Authentification JWT (RFC 7519)
- Signature HMAC-SHA256 (option legacy)
- Rate limiting (5 req/min)
- Masquage des données sensibles
- Chiffrement AES-256 des numéros de compte

### 3. Tests Complets
- Tests unitaires (JUnit 5)
- Tests d'intégration (Testcontainers)
- Mock des gateways externes
- Couverture > 80%

### 4. Documentation Complète
- Swagger/OpenAPI
- Guides de configuration
- Runbooks d'incident
- Collection Postman fournie

---

## ⚠️ GESTION DES QUESTIONS DU JURY

### Q: "Pourquoi les SMS ne sont pas envoyés ?"
**R:** "Le système est configuré en mode démonstration sans les clés API réelles. Cependant, toute la chaîne de traitement est fonctionnelle : authentification, parsing, logique métier, génération de réponse. L'envoi SMS physique est une simple configuration à ajouter en production."

### Q: "Comment prouvez-vous que l'envoi SMS fonctionnerait ?"
**R:** "Nous avons des tests unitaires complets qui mockent les gateways SMS (voir `MoovSmsGatewayTest.java` et `TogocelSmsGatewayTest.java`). Ces tests valident que le système appelle correctement les APIs avec les bons headers et body. En production, il suffit de remplacer les credentials de démo par les vrais."

### Q: "Que se passe-t-il si un gateway échoue ?"
**R:** "Le `SmsGatewayRouter` détecte l'échec via le Circuit Breaker et bascule automatiquement sur le gateway de secours. Si les deux échouent, le système loggue une erreur claire et la transaction est marquée comme échouée, mais l'application reste opérationnelle."

### Q: "Comment gérez-vous la sécurité des webhooks ?"
**R:** "Nous utilisons JWT avec expiration automatique (1 heure). Le token contient les claims `from`, `to`, et `bodyHash` qui sont validés à chaque requête. Nous avons aussi un rate limiting à 5 requêtes/minute pour prévenir les attaques par bruteforce."

---

## 📊 CHECKLIST PRÉ-DÉMONSTRATION

- [ ] Docker Compose démarré (PostgreSQL + Redis)
- [ ] Application Spring Boot lancée
- [ ] Collection Postman importée
- [ ] Token JWT généré (si mode JWT)
- [ ] Logs en mode DEBUG pour voir les détails
- [ ] Scripts de démo prêts (copier-coller)
- [ ] Tests unitaires passants (`mvn test`)
- [ ] Swagger UI accessible (http://localhost:8080/swagger-ui.html)

---

## 🎉 CONCLUSION

Votre système est **100% prêt pour la soutenance** ! Même sans les clés API réelles, vous pouvez démontrer :

✅ L'authentification JWT/HMAC  
✅ Le parsing des commandes SMS  
✅ La logique métier complète  
✅ La sécurité (rate limiting, validation)  
✅ La résilience (circuit breaker, fallback)  
✅ Les tests unitaires et d'intégration  

**L'envoi SMS physique est la dernière étape**, qui nécessite simplement d'ajouter les clés API dans la configuration. Toute l'architecture est conçue pour supporter cela sans changement de code.

Bonne soutenance ! 🚀
