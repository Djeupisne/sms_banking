# 📋 GUIDE DE CONFIGURATION DES CREDENTIALS SMS

## ✅ État actuel du système

Votre code est **100% prêt** pour la production. Les modifications suivantes ont été apportées :

### 1. Comportement des Gateways SMS

**AVANT (Problème)** :
- Quand `MOOV_SMS_API_KEY` n'était pas configurée, le système retournait `true` (succès fictif)
- Aucun SMS n'était réellement envoyé
- Vous receviez une réponse JSON mais le numéro ne recevait rien

**MAINTENANT (Correction)** :
- Si les credentials ne sont PAS configurés → le gateway retourne `false`
- Le router bascule automatiquement vers le gateway de secours (Togocel)
- Si aucun gateway n'est configuré → erreur claire dans les logs avec instructions

### 2. Logs explicites

Vous verrez maintenant ces messages dans les logs :

```
WARN  MOOV NOT CONFIGURED - Identifiants API manquants pour Moov. 
       Aucun SMS envoyé à +2289XXXXXXX. 
       Configurez MOOV_SMS_API_KEY et MOOV_SMS_API_SECRET

WARN  Gateway primaire Moov non disponible (credentials manquants ou désactivé). 
       Tentative avec le fallback Togocel.

ERROR ÉCHEC TOTAL - Aucun SMS envoyé vers +2289XXXXXXX. 
      Les deux gateways (Moov et Togocel) ont échoué ou ne sont pas configurés.

ERROR ACTION REQUISE: Configurez les credentials API pour au moins un gateway SMS.
ERROR   - Pour Moov: Définissez MOOV_SMS_API_KEY et MOOV_SMS_API_SECRET
ERROR   - Pour Togocel: Définissez TOGOCEL_SMS_API_KEY et TOGOCEL_SMS_API_SECRET
```

---

## 🔧 CONFIGURATION REQUISE

### Option 1 : Configurer Moov Africa Togo (Recommandé - Gateway Primaire)

1. **Obtenez vos credentials** auprès de Moov Africa Togo :
   - Rendez-vous sur : https://developer.moov.tg/
   - Créez un compte développeur
   - Générez une clé API et un secret

2. **Définissez les variables d'environnement** :

```bash
# Dans votre fichier .env ou variables d'environnement
MOOV_SMS_ENABLED=true
MOOV_SMS_API_KEY=votre_vraie_cle_api_moov
MOOV_SMS_API_SECRET=votre_vrai_secret_api_moov
MOOV_SMS_API_URL=https://api.moov.tg/sms/v1/send
MOOV_SMS_SENDER_ID=ORABANK
```

3. **Redémarrez l'application** :
```bash
docker-compose restart sms-banking
# OU
mvn spring-boot:run
```

---

### Option 2 : Configurer Togocel (Gateway de Secours)

1. **Obtenez vos credentials** auprès de Togocel :
   - Rendez-vous sur : https://api.togocel.com/docs/
   - Créez un compte développeur
   - Générez une clé API et un secret

2. **Définissez les variables d'environnement** :

```bash
TOGOCEL_SMS_ENABLED=true
TOGOCEL_SMS_API_KEY=votre_vraie_cle_api_togocel
TOGOCEL_SMS_API_SECRET=votre_vrai_secret_api_togocel
TOGOCEL_SMS_API_URL=https://api.togocel.com/sms/v1/send
TOGOCEL_SMS_SENDER_ID=ORABANK
```

---

### Option 3 : Configurer les deux (Recommandé pour la redondance)

Configurez **Moov en primaire** et **Togocel en secours** :

```bash
# Gateway Primaire - Moov
MOOV_SMS_ENABLED=true
MOOV_SMS_API_KEY=votre_cle_moov
MOOV_SMS_API_SECRET=votre_secret_moov

# Gateway de Secours - Togocel
TOGOCEL_SMS_ENABLED=true
TOGOCEL_SMS_API_KEY=votre_cle_togocel
TOGOCEL_SMS_API_SECRET=votre_secret_togocel

# Préférence (true = Moov en premier)
SMS_GATEWAY_PREFER_MOOV=true
```

**Avantage** : Si Moov échoue, le système bascule automatiquement sur Togocel !

---

## 🧪 TESTER LA CONFIGURATION

### 1. Vérifiez que les variables sont définies

```bash
echo $MOOV_SMS_API_KEY
echo $MOOV_SMS_API_SECRET
```

Si vous voyez `votre_cle_api_moov_africa_togo`, ce n'est pas configuré !

### 2. Envoyez un SMS test

```bash
curl -X POST http://localhost:8080/api/sms/webhook \
  -H "Content-Type: application/json" \
  -H "X-Signature: votre_signature_hmac" \
  -H "X-Timestamp: $(date +%s)" \
  -d '{
    "from": "+22890000001",
    "to": "+22801010101",
    "body": "OTP"
  }'
```

### 3. Vérifiez les logs

Recherchez ces messages :

**✅ SUCCÈS** :
```
INFO  Sending SMS via Moov Togo to +2289000**** with sender ORABANK
INFO  SMS sent successfully via Moov Togo to +2289000****
```

**❌ ÉCHEC - Credentials manquants** :
```
WARN  MOOV NOT CONFIGURED - Identifiants API manquants pour Moov.
WARN  Gateway primaire Moov non disponible. Tentative avec le fallback Togocel.
```

**❌ ÉCHEC TOTAL** :
```
ERROR ÉCHEC TOTAL - Aucun SMS envoyé vers +2289000****.
ERROR ACTION REQUISE: Configurez les credentials API...
```

---

## 📊 TABLEAU RÉCAPITULATIF

| Situation | Comportement | SMS envoyé ? |
|-----------|-------------|--------------|
| **Moov configuré** | Utilise Moov | ✅ OUI |
| **Moov non configuré + Togocel configuré** | Bascule sur Togocel | ✅ OUI |
| **Les deux configurés** | Utilise Moov (primaire) | ✅ OUI |
| **Aucun configuré** | Retourne erreur claire | ❌ NON |
| **Moov échoue + Togocel configuré** | Bascule sur Togocel | ✅ OUI |

---

## 🚀 PRODUCTION CHECKLIST

Avant de passer en production, vérifiez :

- [ ] Variables `MOOV_SMS_API_KEY` et `MOOV_SMS_API_SECRET` définies avec de VRAIES valeurs
- [ ] OU variables `TOGOCEL_SMS_API_KEY` et `TOGOCEL_SMS_API_SECRET` définies
- [ ] Testé l'envoi d'un SMS réel et confirmé la réception
- [ ] Configuré les deux gateways pour la redondance (recommandé)
- [ ] Défini `MOOV_SMS_ENABLED=true` et/ou `TOGOCEL_SMS_ENABLED=true`
- [ ] Vérifié que les logs montrent "SMS sent successfully"

---

## 🆘 DÉPANNAGE

### Le SMS ne part toujours pas ?

1. **Vérifiez les logs** :
```bash
docker logs sms-banking | grep -i "sms\|moov\|togocel"
```

2. **Testez la connectivité** :
```bash
curl -I https://api.moov.tg/sms/v1/send
curl -I https://api.togocel.com/sms/v1/send
```

3. **Vérifiez les credentials** :
```bash
docker exec sms-banking env | grep MOOV_SMS
docker exec sms-banking env | grep TOGOCEL_SMS
```

4. **Activez le mode DEBUG** :
Dans `application.yml`, ajoutez :
```yaml
logging:
  level:
    com.orabank.smsbanking.gateway: DEBUG
```

---

## 📞 CONTACT

Pour obtenir vos credentials API :

- **Moov Africa Togo** : https://developer.moov.tg/ ou contact@moov.tg
- **Togocel** : https://api.togocel.com/docs/ ou support@togocel.com

---

**🎉 Votre système est prêt ! Il suffit maintenant d'ajouter vos credentials API réels.**
