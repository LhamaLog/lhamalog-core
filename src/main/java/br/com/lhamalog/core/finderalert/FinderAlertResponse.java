package br.com.lhamalog.core.finderalert;

/**
 * Success body of the Finder alert route. Exactly these fields: no {@code repeticao} and no other name.
 */
public record FinderAlertResponse(boolean aceito, String tenantId, String productId, String batchId, String motivo) {
}
