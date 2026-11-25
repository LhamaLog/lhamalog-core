package br.com.lhamalog.core.occurrence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import br.com.lhamalog.core.occurrence.infrastructure.OccurrenceDocument;

/** Writes occurrences straight into the {@code Ocorrencia} collection, as the Occurrence Module would. */
final class OccurrenceFixtures {

	private final MongoTemplate mongo;

	OccurrenceFixtures(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	Builder occurrence(String tenantId, String productId) {
		return new Builder(tenantId, productId);
	}

	/** Raw document, for shapes the mapped record cannot express (unknown type or status, missing fields). */
	void insertRaw(Document document) {
		mongo.getCollection("Ocorrencia").insertOne(document);
	}

	final class Builder {
		private String id = UUID.randomUUID().toString();
		private final String tenantId;
		private final String productId;
		private String type = "ERROR";
		private String message = "message " + UUID.randomUUID();
		private String service = "svc";
		private String signature;
		private String title = "Título";
		private String summary;
		private String status = "ABERTA";
		private Boolean recurring = false;
		private long count = 1;
		private Instant firstSeenAt = Instant.parse("2026-11-01T10:00:00Z");
		private Instant lastSeenAt = Instant.parse("2026-11-01T10:00:00Z");
		private List<String> samples = new ArrayList<>();
		private String hostName = "host-1";
		private String traceId;
		private String correlationId;
		private String moduleId;
		private Integer lineNumber;
		private Map<String, String> extractedFields = new LinkedHashMap<>();
		private Boolean sensitiveData;
		private List<OccurrenceDocument.StatusChange> history = new ArrayList<>();

		private Builder(String tenantId, String productId) {
			this.tenantId = tenantId;
			this.productId = productId;
		}

		Builder id(String value) { this.id = value; return this; }
		Builder type(String value) { this.type = value; return this; }
		Builder message(String value) { this.message = value; return this; }
		Builder service(String value) { this.service = value; return this; }
		Builder signature(String value) { this.signature = value; return this; }
		Builder title(String value) { this.title = value; return this; }
		Builder summary(String value) { this.summary = value; return this; }
		Builder status(String value) { this.status = value; return this; }
		Builder recurring(boolean value) { this.recurring = value; return this; }
		Builder count(long value) { this.count = value; return this; }
		Builder seen(Instant first, Instant last) { this.firstSeenAt = first; this.lastSeenAt = last; return this; }
		Builder lastSeen(Instant value) { return seen(value, value); }
		Builder samples(List<String> value) { this.samples = new ArrayList<>(value); return this; }
		Builder hostName(String value) { this.hostName = value; return this; }
		Builder traceId(String value) { this.traceId = value; return this; }
		Builder correlationId(String value) { this.correlationId = value; return this; }
		Builder moduleId(String value) { this.moduleId = value; return this; }
		Builder lineNumber(Integer value) { this.lineNumber = value; return this; }
		Builder extracted(String name, String value) { this.extractedFields.put(name, value); return this; }
		Builder sensitiveData(Boolean value) { this.sensitiveData = value; return this; }

		Builder change(String from, String to, Instant at, String byType, String byId) {
			history.add(new OccurrenceDocument.StatusChange(from, to, at, new OccurrenceDocument.ChangedBy(byType, byId)));
			return this;
		}

		String insert() {
			String storedSignature = signature != null ? signature
					: type + "|" + message.replace("|", "\\|") + "|" + service + "|" + hostName;
			mongo.insert(new OccurrenceDocument(id, tenantId, productId, storedSignature, type, title, summary, status,
					recurring, count, firstSeenAt, lastSeenAt, samples, hostName, traceId, correlationId, moduleId,
					lineNumber, Map.of(), sensitiveData, "regras-teste-v1", id + "-r1", history, null, null));
			if (!extractedFields.isEmpty()) {
				// OTel names have dots (http.route): the mapped insert refuses them, an update writes them as they are,
				// like the local seeder and the Occurrence Module.
				mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), Update.update("extractedFields", extractedFields),
						"Ocorrencia");
			}
			return id;
		}
	}
}
