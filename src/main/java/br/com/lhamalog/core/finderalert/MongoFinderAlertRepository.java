package br.com.lhamalog.core.finderalert;

import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;

/**
 * Inserts one alert per {@code (tenantId, productId, batchId, motivo)} without a multi-document transaction. The loser
 * of a race rereads the stored document and reports a duplicate.
 */
@Repository
public class MongoFinderAlertRepository implements FinderAlertRepository {

	private final MongoTemplate mongo;

	public MongoFinderAlertRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public boolean insert(FinderAlertCommand command) {
		FinderAlertDocument document = new FinderAlertDocument(UUID.randomUUID().toString(), command.tenantId(),
				command.productId(), command.batchId(), command.motivo());
		try {
			mongo.insert(document);
			return true;
		}
		catch (RuntimeException failure) {
			if (!duplicate(failure)) throw failure;
			FinderAlertDocument stored = mongo.findOne(key(command), FinderAlertDocument.class);
			if (stored == null) throw failure;
			return false;
		}
	}

	private static Query key(FinderAlertCommand command) {
		return Query.query(Criteria.where("tenantId").is(command.tenantId())
			.and("productId").is(command.productId())
			.and("batchId").is(command.batchId())
			.and("motivo").is(command.motivo()));
	}

	private static boolean duplicate(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof DuplicateKeyException) return true;
			if (current instanceof MongoWriteException write
					&& ErrorCategory.fromErrorCode(write.getError().getCode()) == ErrorCategory.DUPLICATE_KEY) {
				return true;
			}
		}
		return false;
	}

}
