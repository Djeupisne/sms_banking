package com.orabank.smsbanking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Service d'envoi d'emails via l'API REST Brevo (ex-Sendinblue).
 *
 * CORRECTIONS APPORTÉES :
 *  1. Le JSON est construit manuellement avec escapeJson() sur CHAQUE valeur
 *     (htmlContent inclus), ce qui évite que les guillemets/apostrophes du HTML
 *     cassent le payload JSON → Brevo ne rejette plus la requête.
 *  2. Le HTML n'utilise plus de guillemets doubles dans les attributs style=
 *     (remplacés par des guillemets simples HTML) pour limiter l'échappement.
 *  3. Meilleure lecture de la réponse d'erreur Brevo pour faciliter le debug.
 */
@Service
@Slf4j
public class EmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${brevo.api.key:}")
    private String apiKey;

    @Value("${brevo.sender.email:oualoumidjeupisne@gmail.com}")
    private String senderEmail;

    @Value("${brevo.sender.name:Orabank SMS Banking}")
    private String senderName;

    @Value("${app.frontend.url:http://localhost:3001}")
    private String frontendUrl;

    private boolean isEnabled = false;

    @PostConstruct
    public void initialize() {
        log.info("\n === INITIALISATION SERVICE EMAIL (Brevo) ===");

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("  BREVO_API_KEY manquant – service email désactivé");
            log.warn("   → Ajoutez BREVO_API_KEY dans votre .env ou application-dev.yml");
            return;
        }

        this.isEnabled = true;
        log.info("Brevo initialisé avec succès");
        log.info(" Expéditeur : {} <{}>", senderName, senderEmail);
        log.info(" === FIN INITIALISATION ===\n");
    }

    /**
     * Envoie l'email de réinitialisation de mot de passe.
     *
     * @param to    Adresse email du destinataire
     * @param name  Nom complet du client
     * @param token Token UUID de réinitialisation
     * @return true si l'email a été envoyé (ou simulé en mode dev)
     */
    public boolean sendResetPasswordEmail(String to, String name, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;

        log.info("\n Envoi email réinitialisation → {}", maskEmail(to));
        log.debug("   🔗 Lien : {}", resetLink);

        if (!isEnabled) {
            log.warn("  Email simulé (BREVO_API_KEY non configuré)");
            log.warn("   À     : {}", to);
            log.warn("   Objet : Réinitialisation de votre mot de passe – Orabank SMS Banking");
            log.warn("    LIEN : {}", resetLink);
            return true;
        }

        try {
            String subject = "Réinitialisation de votre mot de passe – Orabank SMS Banking";
            String htmlContent = buildForgotPasswordEmail(name, resetLink);
            String textContent = buildForgotPasswordText(name, resetLink);

            return sendEmailViaBrevo(to, subject, htmlContent, textContent);

        } catch (Exception e) {
            log.error(" Erreur inattendue lors de l'envoi email : {}", e.getMessage(), e);
            log.info(" LIEN DE RÉINITIALISATION (à copier manuellement) : {}", resetLink);
            return false;
        }
    }

    /**
     * Appel HTTP direct à l'API Brevo v3.
     *
     * CORRECTIF CLÉ : chaque valeur insérée dans le JSON est passée
     * par escapeJson(), y compris htmlContent. Cela évite que les
     * guillemets/backslashes du HTML corrompent le JSON.
     */
    private boolean sendEmailViaBrevo(String to, String subject, String htmlContent, String textContent) {
        try {
            URL url = new URL(BREVO_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("api-key", apiKey);
            conn.setDoOutput(true);

            // Construction du JSON avec échappement systématique de chaque valeur
            String jsonPayload = "{"
                    + "\"sender\":{"
                    +   "\"name\":\"" + escapeJson(senderName) + "\","
                    +   "\"email\":\"" + escapeJson(senderEmail) + "\""
                    + "},"
                    + "\"to\":[{\"email\":\"" + escapeJson(to) + "\"}],"
                    + "\"subject\":\"" + escapeJson(subject) + "\","
                    + "\"htmlContent\":\"" + escapeJson(htmlContent) + "\","
                    + "\"textContent\":\"" + escapeJson(textContent) + "\""
                    + "}";

            log.debug(" Payload JSON Brevo (taille : {} octets)", jsonPayload.getBytes(StandardCharsets.UTF_8).length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int httpCode = conn.getResponseCode();

            if (httpCode == 200 || httpCode == 201 || httpCode == 202) {
                String responseBody = readStream(conn.getInputStream());
                log.info(" Email envoyé avec succès – messageId: {}", extractMessageId(responseBody));
                return true;
            } else {
                String errorBody = readStream(conn.getErrorStream());
                log.error(" Brevo a rejeté la requête : HTTP {} – {}", httpCode, errorBody);
                return false;
            }

        } catch (Exception e) {
            log.error(" Erreur connexion Brevo : {}", e.getMessage(), e);
            return false;
        }
    }

    
    // Construction du contenu de l'email
    

    /**
     * Template HTML de l'email de réinitialisation.
     *
     * NOTE : les attributs style utilisent des guillemets simples (')
     * au lieu de doubles (") pour réduire l'échappement JSON.
     */
    private String buildForgotPasswordEmail(String name, String resetLink) {
        String safeName = escapeHtml(name);
        String safeLink = escapeHtml(resetLink);

        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head>"
                + "<body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:28px;"
                +   "border:1px solid #e0e0e0;border-radius:10px;'>"
                + "<h1 style='color:#00a651;text-align:center;'>Orabank SMS Banking</h1>"
                + "<div style='background:#fef9c3;padding:14px 18px;border-radius:8px;"
                +   "border-left:4px solid #f59e0b;margin:20px 0;'>"
                +   "<p style='margin:0;color:#92400e;font-weight:bold;'>"
                +     "Demande de réinitialisation de mot de passe"
                +   "</p>"
                + "</div>"
                + "<p>Bonjour <strong>" + safeName + "</strong>,</p>"
                + "<p>Vous avez demandé la réinitialisation de votre mot de passe. "
                +   "Cliquez sur le bouton ci-dessous pour définir un nouveau mot de passe :</p>"
                + "<div style='text-align:center;margin:32px 0;'>"
                +   "<a href='" + safeLink + "' "
                +      "style='display:inline-block;padding:14px 32px;background:#00a651;"
                +      "color:white;text-decoration:none;border-radius:8px;"
                +      "font-weight:bold;font-size:16px;'>"
                +     "Réinitialiser mon mot de passe"
                +   "</a>"
                + "</div>"
                + "<p style='color:#6b7280;font-size:14px;'>"
                +   "Ce lien expire dans <strong>30 minutes</strong>."
                + "</p>"
                + "<p style='color:#6b7280;font-size:14px;'>"
                +   "Si vous n'avez pas effectué cette demande, ignorez cet email. "
                +   "Votre mot de passe ne sera pas modifié."
                + "</p>"
                + "<hr style='border:none;border-top:1px solid #e5e7eb;margin:24px 0;'>"
                + "<p style='color:#9ca3af;font-size:12px;text-align:center;'>"
                +   "Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :<br>"
                +   "<a href='" + safeLink + "' style='color:#00a651;word-break:break-all;'>"
                +     resetLink
                +   "</a>"
                + "</p>"
                + "</body></html>";
    }

    private String buildForgotPasswordText(String name, String resetLink) {
        return "Bonjour " + name + ",\n\n"
                + "Vous avez demandé la réinitialisation de votre mot de passe Orabank SMS Banking.\n\n"
                + "Cliquez sur ce lien pour définir un nouveau mot de passe (valable 30 minutes) :\n"
                + resetLink + "\n\n"
                + "Si vous n'avez pas effectué cette demande, ignorez cet email.\n\n"
                + "L'équipe Orabank SMS Banking";
    }

    
    // Utilitaires
    

    private String readStream(InputStream is) {
        if (is == null) return "(réponse vide)";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (IOException e) {
            return "(erreur lecture réponse)";
        }
    }

    private String extractMessageId(String response) {
        int start = response.indexOf("\"messageId\":\"");
        if (start == -1) return "inconnu";
        start += "\"messageId\":\"".length();
        int end = response.indexOf("\"", start);
        return (end == -1) ? "inconnu" : response.substring(start, end);
    }

    /**
     * Échappe une chaîne pour l'insérer dans une valeur JSON string.
     * Doit être appliqué à TOUTES les valeurs dynamiques du payload.
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\f", "\\f")
                .replace("\b", "\\b");
    }

    /**
     * Échappe une chaîne pour l'insérer dans du HTML.
     */
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0];
        if (local.length() <= 2) return "***@" + parts[1];
        return local.substring(0, 2) + "***@" + parts[1];
    }
}