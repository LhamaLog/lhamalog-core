package br.com.lhamalog.core.finderalert;

/**
 * Tells the screen that a Finder alert was stored for the first time. A repeat does not call this.
 */
public interface FinderAlertNotifier {

	void notifyAccepted(FinderAlertCommand command);

}
