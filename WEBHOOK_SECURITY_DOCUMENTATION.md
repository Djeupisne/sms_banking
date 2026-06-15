# Documentation : Sécurisation des Webhooks SMS - Niveau Bancaire

## Vue d'ensemble

Cette documentation décrit l'implémentation de la sécurité par signature cryptographique HMAC pour les webhooks SMS dans le système Orabank.

## Problématique

Dans un environnement bancaire réel, l'endpoint `/api/sms/webhook` ne peut pas rester permissif. Les risques incluent :
- Usurpation d'identité (attaquant envoyant de faux SMS)
- Attaques par replay (réutilisation de requêtes interceptées)
- Fraude massive (transferts non autorisés)
- Déni de service

## Solution Implémentée

### 1. Signature HMAC-SHA256

Chaque requête webhook doit être signée avec une clé secrète partagée entre l'opérateur SMS et la banque.

**Fonctionnement :**
```
signature = HMAC-SHA256(clé_secrète, timestamp + ":" + payload)
```

Où :
- `timestamp` : Timestamp Unix en millisecondes
- `payload` : Concaténation des données `from|to|body|timestamp`

### 2. Validation du Timestamp

Pour prévenir les attaques par replay :
- Le timestamp doit être dans une fenêtre de temps acceptable (5 minutes par défaut)
- Les requêtes trop anciennes ou futures sont rejetées

### 3. Comparaison en Temps Constant

La validation de signature utilise une comparaison en temps constant pour éviter les attaques par timing.

## Configuration

### Fichiers de Configuration

#### Development (`application-dev.yml`)
```yaml
webhook:
  secret:
    key: dev_webhook_secret_key_for_testing_only_32c
  signature:
    enabled: false  # Désactivé en dev pour faciliter les tests
    max-age-ms: 300000
```

#### Production (`application-prod.yml`)
```yaml
webhook:
  secret:
    key: ${WEBHOOK_SECRET_KEY}  # Variable d'environnement
  signature:
    enabled: true  # Obligatoire en production
    max-age-ms: 300000
```

### Génération de Clé Secrète

Pour générer une clé sécurisée en production :
```bash
# Génère une chaîne aléatoire de 32 caractères
openssl rand -base64 32
```

## Utilisation

### Format de Requête Autorisée

L'opérateur SMS doit envoyer les headers suivants :

```http
POST /api/sms/webhook HTTP/1.1
Content-Type: application/json
X-Webhook-Signature: <signature_hmac_base64>
X-Webhook-Timestamp: <timestamp_unix_millis>

{
  "from": "+2250123456789",
  "to": "+225800000000",
  "body": "TRANSFER 50000 +2250708091011",
  "timestamp": 1673456789012
}
```

### Exemple de Génération de Signature (côté Opérateur SMS)

#### Java
```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class WebhookSigner {
    public static String generateSignature(String secretKey, long timestamp, 
                                           String from, String to, String body) {
        try {
            String payload = from + "|" + to + "|" + body + "|" + timestamp;
            String dataToSign = timestamp + ":" + payload;
            
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(
                secretKey.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            
            byte[] hash = sha256_HMAC.doFinal(dataToSign.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error generating signature", e);
        }
    }
}
```

#### Python
```python
import hmac
import hashlib
import base64
import time

def generate_signature(secret_key, from_num, to_num, body):
    timestamp = str(int(time.time() * 1000))
    payload = f"{from_num}|{to_num}|{body}|{timestamp}"
    data_to_sign = f"{timestamp}:{payload}"
    
    signature = hmac.new(
        secret_key.encode('utf-8'),
        data_to_sign.encode('utf-8'),
        hashlib.sha256
    ).digest()
    
    return base64.b64encode(signature).decode('utf-8'), timestamp

# Usage
secret = "votre_cle_secrete_webhook_tres_longue_et_securisee_32caracteres"
signature, timestamp = generate_signature(
    secret, 
    "+2250123456789", 
    "+225800000000", 
    "TRANSFER 50000 +2250708091011"
)

# Envoyer avec les headers X-Webhook-Signature et X-Webhook-Timestamp
```

#### Node.js
```javascript
const crypto = require('crypto');

function generateSignature(secretKey, from, to, body) {
    const timestamp = Date.now().toString();
    const payload = `${from}|${to}|${body}|${timestamp}`;
    const dataToSign = `${timestamp}:${payload}`;
    
    const signature = crypto
        .createHmac('sha256', secretKey)
        .update(dataToSign)
        .digest('base64');
    
    return { signature, timestamp };
}

// Usage
const { signature, timestamp } = generateSignature(
    'votre_cle_secrete_webhook_tres_longue_et_securisee_32caracteres',
    '+2250123456789',
    '+225800000000',
    'TRANSFER 50000 +2250708091011'
);

// Envoyer avec les headers X-Webhook-Signature et X-Webhook-Timestamp
```

## Réponses d'Erreur

### Signature Manquante
```json
HTTP/1.1 401 Unauthorized
{
  "to": null,
  "message": "ORABANK - Erreur de sécurité: Signature requise"
}
```

### Signature Invalide
```json
HTTP/1.1 401 Unauthorized
{
  "to": null,
  "message": "ORABANK - Erreur de sécurité: Signature invalide"
}
```

### Timestamp Expiré
```json
HTTP/1.1 401 Unauthorized
{
  "to": null,
  "message": "ORABANK - Erreur de sécurité: Request expired"
}
```

## Architecture de Sécurité Recommandée

Pour un déploiement en production chez Orabank :

### 1. DMZ (Zone Démilitarisée)
```
[Internet] → [Firewall] → [DMZ: Webhook Service] → [Firewall Interne] → [Réseau Bancaire]
```

### 2. Restriction IP
Configurer le firewall pour n'accepter que les IPs des opérateurs SMS officiels :
- Orange CI : `xxx.xxx.xxx.xxx/xx`
- MTN CI : `xxx.xxx.xxx.xxx/xx`
- Moov CI : `xxx.xxx.xxx.xxx/xx`

### 3. Rate Limiting
Déjà implémenté : 5 requêtes par minute par numéro.

### 4. Audit et Logging
Toutes les tentatives (succès et échecs) sont loguées avec :
- Timestamp
- Numéro expéditeur (masqué)
- Statut de validation
- Adresse IP source

## Migration depuis l'Ancien Système

### Phase 1 : Développement (Actuelle)
```yaml
webhook:
  signature:
    enabled: false
```
- Tests fonctionnels sans signature
- Logs de toutes les requêtes

### Phase 2 : Pré-production
```yaml
webhook:
  signature:
    enabled: true
```
- Activation de la validation
- Mode "shadow" : loguer les échecs sans rejeter
- Coordination avec l'opérateur SMS pour tests

### Phase 3 : Production
```yaml
webhook:
  signature:
    enabled: true
```
- Rejet strict des requêtes non signées
- Surveillance accrue des logs
- Alertes sur les échecs de signature

## Checklist de Déploiement

- [ ] Générer une clé secrète forte (32+ caractères)
- [ ] Configurer la clé dans les variables d'environnement
- [ ] Partager la clé avec l'opérateur SMS via canal sécurisé
- [ ] Configurer les restrictions IP au niveau firewall
- [ ] Tester avec l'opérateur SMS en pré-production
- [ ] Activer la validation en production
- [ ] Mettre en place la surveillance des logs
- [ ] Documenter la procédure de rotation de clé

## Rotation de Clé

En cas de compromission ou périodiquement (recommandé : tous les 90 jours) :

1. Générer une nouvelle clé
2. Configurer la nouvelle clé en parallèle de l'ancienne
3. Propager la nouvelle clé à l'opérateur SMS
4. Attendre la propagation complète (24-48h)
5. Désactiver l'ancienne clé

## Support

Pour toute question ou incident lié à la sécurité des webhooks :
- Email : security@orabank.ci
- Téléphone : +225 XX XX XX XX (équipe sécurité)
