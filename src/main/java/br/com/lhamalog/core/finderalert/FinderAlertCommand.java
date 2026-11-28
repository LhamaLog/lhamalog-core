package br.com.lhamalog.core.finderalert;

/**
 * Accepted body of {@code POST /internal/v1/alertas/finder}. The four fields are the durable key.
 */
public record FinderAlertCommand(String tenantId, String productId, String batchId, String motivo) {

	public static final String OPENOBSERVE_UNAVAILABLE = "consulta_openobserve_indisponivel";

}
