package com.orabank.smsbanking.util;

/**
 * Utilitaires pour le traitement des SMS.
 */
public class SmsUtils {

    /**
     * Normalise un numéro de téléphone au format E.164 (+225XXXXXXXXX).
     * Gère les formats suivants :
     * - +2250123456789
     * - 2250123456789
     * - 0123456789
     * - 01 23 45 67 89
     *
     * @param phoneNumber le numéro brut
     * @return le numéro normalisé ou null si invalide
     */
    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }

        // Supprimer tous les caractères non numériques sauf le '+'
        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");

        // Cas 1: Commence par '0' (ex: 0123456789) -> Ajouter +225
        if (cleaned.startsWith("0")) {
            return "+225" + cleaned.substring(1);
        }

        // Cas 2: Commence par '225' sans '+' (ex: 2250123456789) -> Ajouter '+'
        if (cleaned.startsWith("225") && !cleaned.startsWith("+")) {
            return "+" + cleaned;
        }

        // Cas 3: Commence déjà par '+' (ex: +2250123456789) -> Retourner tel quel
        if (cleaned.startsWith("+")) {
            return cleaned;
        }

        // Cas 4: Numéro court ou autre format inattendu -> Présumer Côte d'Ivoire
        // Si le numéro fait 8 ou 9 chiffres, on ajoute +225
        if (cleaned.length() <= 9) {
            return "+225" + cleaned;
        }

        // Fallback: retourner tel quel avec '+' si manquant
        return cleaned.startsWith("+") ? cleaned : "+" + cleaned;
    }
}
