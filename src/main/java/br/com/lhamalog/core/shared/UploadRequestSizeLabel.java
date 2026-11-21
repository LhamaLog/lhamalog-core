package br.com.lhamalog.core.shared;

/**
 * Human-readable request-size label used in container-level 413 messages. Implemented
 * by the manual-upload properties so {@link ApiExceptionHandler} does not depend on that
 * feature type.
 */
@FunctionalInterface
public interface UploadRequestSizeLabel {

	String maxRequestSizeLabel();
}
