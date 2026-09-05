# OraBank SMS Banking - Liste des Commandes

## Configuration Postman

**Endpoint:** `POST http://localhost:8080/api/sms/webhook`

**Headers:**
```
Content-Type: application/json
```

**Body (JSON):**
```json
{
  "from": "+22890000001",
  "message": "COMMANDE_ICI",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

---

## 1. SOLDE ET INFORMATIONS COMPTES

### Consulter le solde
```json
{
  "from": "+22890000001",
  "message": "SOLDE",
  "timestamp": "2026-09-05T22:00:00Z"
}
```
*Affiche le solde du compte par défaut*

### Consulter le solde d'un compte spécifique
```json
{
  "from": "+22890000001",
  "message": "SOLDE COMPTE001",
  "timestamp": "2026-09-05T22:00:00Z"
}
```
*Remplacez COMPTE001 par le numéro de compte souhaité*

### Informations sur le compte (défaut)
```json
{
  "from": "+22890000001",
  "message": "INFO",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

### Informations sur un compte spécifique
```json
{
  "from": "+22890000001",
  "message": "INFO COMPTE001",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

---

## 2. HISTORIQUE DES TRANSACTIONS

### Historique global (tous les comptes)
```json
{
  "from": "+22890000001",
  "message": "HISTO",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

### Historique avec le mot-clé complet
```json
{
  "from": "+22890000001",
  "message": "HISTORIQUE",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

### Historique d'un compte spécifique
```json
{
  "from": "+22890000001",
  "message": "HISTO COMPTE001",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

### Historique avec le mot-clé complet pour un compte
```json
{
  "from": "+22890000001",
  "message": "HISTORIQUE COMPTE001",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

### Alias EXTRAIT
```json
{
  "from": "+22890000001",
  "message": "EXTRAIT",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

---

## 3. VIREMENTS ET TRANSFERTS

### Transfert vers un compte interne
```json
{
  "from": "+22890000001",
  "message": "TRANSFERT 50000 COMPTE001 +22890000002 OTP123456",
  "timestamp": "2026-09-05T22:00:00Z"
}
```
*Format: TRANSFERT [montant] [compte_source] [numéro_destinataire] [OTP]*

### Transfert vers Mobile Money (Moov - préfixe 90, 91)
```json
{
  "from": "+22890000001",
  "message": "TRANSFERT 50000 COMPTE001 +22890123456 OTP123456",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

### Transfert vers Mobile Money (Yas - préfixe 92-99)
```json
{
  "from": "+22890000001",
  "message": "TRANSFERT 50000 COMPTE001 +22892123456 OTP123456",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

### Transfert avec compte source auto-détecté
```json
{
  "from": "+22890000001",
  "message": "TRANSFERT 50000 +22890000002 OTP123456",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

---

## 4. SÉCURITÉ ET AUTHENTIFICATION

### Demander un code OTP
```json
{
  "from": "+22890000001",
  "message": "OTP",
  "timestamp": "2026-09-05T22:00:00Z"
}
```
*Envoie un code à 6 chiffres valable 5 minutes*

---

## 5. AIDE

### Afficher l'aide
```json
{
  "from": "+22890000001",
  "message": "AIDE",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

### Alias HELP
```json
{
  "from": "+22890000001",
  "message": "HELP",
  "timestamp": "2026-09-05T22:00:00Z"
}
```

---

## RÉCAPITULATIF DES COMMANDES

| Catégorie | Commande | Description |
|-----------|----------|-------------|
| **Solde** | `SOLDE` | Solde du compte par défaut |
| | `SOLDE [COMPTE]` | Solde d'un compte spécifique |
| **Info** | `INFO` | Détails du compte par défaut |
| | `INFO [COMPTE]` | Détails d'un compte spécifique |
| **Historique** | `HISTO` | Historique global (10 dernières transactions) |
| | `HISTORIQUE` | Identique à HISTO |
| | `HISTO [COMPTE]` | Historique d'un compte spécifique (5 dernières) |
| | `HISTORIQUE [COMPTE]` | Identique ci-dessus |
| | `EXTRAIT` | Alias pour historique |
| **Transfert** | `TRANSFERT [montant] [source] [dest] [OTP]` | Virement avec OTP |
| **Sécurité** | `OTP` | Demander un nouveau code OTP |
| **Aide** | `AIDE` / `HELP` | Afficher le menu d'aide |

---

## NOTES IMPORTANTES

1. **Numéros de compte:** Utilisez les formats `COMPTE001`, `COMPTE005`, etc.
2. **Numéros de téléphone:** Formats acceptés: `+22890000001`, `22890000001`, `90000001`
3. **OTP:** Code à 6 chiffres, valable 5 minutes
4. **Montants:** En FCFA (XOF), sans séparateur de milliers
5. **Mobile Money:** Détection automatique selon le préfixe:
   - Moov Africa: 92, 93, 94, 95, 96, 97, 98, 99
   - Yas (Togocom): 90, 91

---

## EXEMPLE DE COLLECTION POSTMAN

Importez ce JSON dans Postman pour tester rapidement:

```json
{
  "info": {
    "name": "OraBank SMS Banking",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "SOLDE",
      "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"from\": \"+22890000001\",\n  \"message\": \"SOLDE\",\n  \"timestamp\": \"{{$isoTimestamp}}\"\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/sms/webhook",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "sms", "webhook"]
        }
      }
    },
    {
      "name": "HISTO COMPTE001",
      "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"from\": \"+22890000001\",\n  \"message\": \"HISTO COMPTE001\",\n  \"timestamp\": \"{{$isoTimestamp}}\"\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/sms/webhook",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "sms", "webhook"]
        }
      }
    },
    {
      "name": "TRANSFERT",
      "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"from\": \"+22890000001\",\n  \"message\": \"TRANSFERT 50000 COMPTE001 +22890000002 OTP123456\",\n  \"timestamp\": \"{{$isoTimestamp}}\"\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/sms/webhook",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "sms", "webhook"]
        }
      }
    }
  ]
}
```
