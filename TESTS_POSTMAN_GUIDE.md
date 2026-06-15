# 📋 Guide Complet des Tests Postman

## 1. Importer la Collection

### Fichier de collection
- **Emplacement** : `/workspace/postman-collection.json`
- **Import** : Dans Postman → Click sur "Import" → Sélectionner le fichier

---

## 2. Configuration Requise

### Variables d'environnement (optionnel)
```
base_url = http://localhost:8080
admin_username = admin
admin_password = admin123
```

### Credentials par défaut
| Rôle | Username | Password |
|------|----------|----------|
| Admin | `admin` | `admin123` |
| User | `user` | `user123` |

---

## 3. Tests à Exécuter (Ordre Recommandé)

### 🔹 A. Vérification de santé
```http
GET {{base_url}}/health
```
**Résultat attendu** : `{"status":"UP"}`

---

### 🔹 B. Authentification Admin
```http
GET {{base_url}}/api/admin/clients
Authorization: Basic admin admin123
```
**Résultat attendu** : Liste des clients (JSON array)

---

### 🔹 C. Test des Webhooks SMS
```http
POST {{base_url}}/api/sms/webhook
Content-Type: application/json

{
  "sender": "+22890000001",
  "message": "SOLDE?"
}
```
**Résultat attendu** : Réponse avec le solde du compte

---

### 🔹 D. ⭐ TRANSACTION ADMIN POUR CLIENT (Nouveau)

#### Endpoint
```http
POST {{base_url}}/api/admin/transfers/mobile-money
Authorization: Basic admin admin123
Content-Type: application/json

{
  "accountNumber": "COMPTE001",
  "amount": 1000,
  "recipientPhone": "+22890000002"
}
```

#### Champs requis :
- `accountNumber` : Numéro de compte du client (ex: COMPTE001, COMPTE002)
- `amount` : Montant entre 1 et 500 000 FCFA
- `recipientPhone` : Téléphone destinataire au format international

#### Résultats possibles :
✅ **Succès** :
```json
{"status": "success", "message": "Virement effectué"}
```

❌ **Erreurs courantes** :
```json
{"status": "error", "message": "Compte source non trouvé"}
{"status": "error", "message": "Solde insuffisant"}
{"status": "error", "message": "Numéro de téléphone invalide"}
```

---

### 🔹 E. Consultation des Transactions
```http
GET {{base_url}}/api/transactions
Authorization: Basic admin admin123
```
**Résultat attendu** : Historique complet des transactions

---

### 🔹 F. Test Dashboard API
```http
GET {{base_url}}/api/dashboard/clients
GET {{base_url}}/api/dashboard/accounts
GET {{base_url}}/api/dashboard/transactions
Authorization: Basic admin admin123
```

---

## 4. Scénarios de Test Complets

### Scénario 1 : Virement réussi
1. Vérifier le solde initial du client
2. Effectuer un virement de 1000 FCFA
3. Vérifier que le statut est "success"
4. Consulter l'historique des transactions

### Scénario 2 : Gestion d'erreurs
1. Tester avec un compte inexistant → Erreur attendue
2. Tester avec un montant > 500 000 → Erreur attendue
3. Tester avec un téléphone invalide → Erreur attendue

### Scénario 3 : Flux SMS + Admin
1. Envoyer SMS "SOLDE?" pour vérifier le solde
2. Effectuer un virement via l'endpoint admin
3. Renvoyer SMS "SOLDE?" pour confirmer la déduction

---

## 5. Interface Frontend (Dashboard)

### Comment tester dans le dashboard :
1. **Se connecter** en tant qu'admin (`admin` / `admin123`)
2. **Cliquer** sur le bouton "Virement Mobile Money"
3. **Remplir** le formulaire :
   - Numéro de compte client : `COMPTE001`
   - Téléphone destinataire : `+22890000002`
   - Montant : `1000`
4. **Valider** et vérifier le message de succès

---

## 6. Codes de Réponse HTTP

| Code | Signification | Action |
|------|---------------|--------|
| 200 | Succès | Transaction effectuée |
| 400 | Bad Request | Vérifier les données envoyées |
| 401 | Non autorisé | Vérifier les credentials |
| 403 | Interdit | Utilisateur non admin |
| 500 | Erreur serveur | Contacter le support |

---

## 7. Astuces Postman

### Utiliser les Collections
- Organiser les requêtes par dossier (Auth, SMS, Admin, Dashboard)
- Utiliser les variables d'environnement pour changer facilement de server

### Tests automatisés
```javascript
// Dans l'onglet "Tests" de Postman
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has success status", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.status).to.eql("success");
});
```

### Pré-request Script pour Auth
```javascript
// Dans l'onglet "Pre-request Script"
const authHeader = 'Basic ' + btoa('admin:admin123');
pm.request.headers.add({key: 'Authorization', value: authHeader});
```

---

## 8. Dépannage

### Problème : "Compte non trouvé"
**Solution** : Vérifier que le compte existe avec `GET /api/admin/clients`

### Problème : "Solde insuffisant"
**Solution** : Créditer le compte d'abord ou utiliser un montant inférieur

### Problème : "401 Unauthorized"
**Solution** : 
- Vérifier le format : `Authorization: Basic admin admin123`
- Ou encoder en Base64 : `admin:admin123` → `YWRtaW46YWRtaW4xMjM=`

---

## 9. Données de Test Disponibles

### Comptes clients existants
- `COMPTE001` - Client : +22890000001
- `COMPTE002` - Client : +22890000002

### Numéros de téléphone valides
- `+22890000001`
- `+22890000002`
- `+22890000003`

---

## 10. Export des Résultats

Pour partager les résultats de test :
1. Dans Postman → Click droit sur la collection
2. "Export" → Choisir le format JSON
3. Inclure les réponses pour documentation

---

**Support** : En cas de problème, consulter les logs backend :
```bash
docker logs sms-banking-app
```
