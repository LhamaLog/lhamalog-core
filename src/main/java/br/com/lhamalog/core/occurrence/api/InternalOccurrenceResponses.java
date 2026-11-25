package br.com.lhamalog.core.occurrence.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Response bodies of contract DN-009-2, with the JSON names of the contract. */
public final class InternalOccurrenceResponses {

	private InternalOccurrenceResponses() {
	}

	/** {@code ResultadoConsulta}: {@code estado} is written as {@code null} when there is no occurrence. */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ResultadoConsulta(EstadoOcorrencia estado) {}

	/** {@code EstadoOcorrencia}: instants as UTC strings ending in {@code Z}. */
	public record EstadoOcorrencia(long contagemAcumulada, String primeiraOcorrencia, String ultimaOcorrencia,
			String status, String revisao) {}

	/** {@code Confirmacao}: {@code execId} only when the summary had one. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Confirmacao(boolean aceito, String reciboId, String tenantId, String productId, String batchId,
			String execId, boolean repeticao) {}

	/** {@code Erro}. */
	public record Erro(String codigo) {}

}
