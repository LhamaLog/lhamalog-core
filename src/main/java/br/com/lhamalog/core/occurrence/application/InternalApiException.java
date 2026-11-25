package br.com.lhamalog.core.occurrence.application;

import org.springframework.http.HttpStatus;

/**
 * Refusal of an internal route, answered as {@code {"codigo": "..."}} (contract DN-009-2). The code never carries a
 * signature, log content or a token.
 */
public class InternalApiException extends RuntimeException {

	public static final String INVALID_REQUEST = "REQUISICAO_INVALIDA";

	public static final String INVALID_DATA = "DADOS_INVALIDOS";

	public static final String SUMMARY_TOO_LARGE = "RESUMO_GRANDE_DEMAIS";

	public static final String ACCESS_DENIED = "ACESSO_NEGADO";

	public static final String STALE_REVISION = "REVISAO_DESATUALIZADA";

	public static final String DELIVERY_IN_PROGRESS = "ENTREGA_EM_ANDAMENTO";

	public static final String INVALID_OCCURRENCE = "OCORRENCIA_INVALIDA";

	public static final String INTERNAL_ERROR = "ERRO_INTERNO";

	private final HttpStatus status;

	private final String code;

	public InternalApiException(HttpStatus status, String code) {
		super(code);
		this.status = status;
		this.code = code;
	}

	public static InternalApiException invalidData() {
		return new InternalApiException(HttpStatus.UNPROCESSABLE_CONTENT, INVALID_DATA);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

}
