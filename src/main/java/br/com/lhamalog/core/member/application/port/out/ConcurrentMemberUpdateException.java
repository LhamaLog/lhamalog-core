package br.com.lhamalog.core.member.application.port.out;

/** A conditional member write found a newer state than the one it was based on; the event must be retried. */
public class ConcurrentMemberUpdateException extends RuntimeException {

	public ConcurrentMemberUpdateException(String memberId) {
		super("Member " + memberId + " changed concurrently");
	}

}
