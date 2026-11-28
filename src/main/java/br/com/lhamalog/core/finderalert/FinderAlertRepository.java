package br.com.lhamalog.core.finderalert;

/**
 * Durable store of Finder alerts. {@code true} when this call inserted the document.
 */
public interface FinderAlertRepository {

	boolean insert(FinderAlertCommand command);

}
