package br.com.lhamalog.core.usage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.lhamalog.core.usage.api.UsageDomain;
import br.com.lhamalog.core.usage.api.UsageFactRequest;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoUsageFactRepositoryTest {

	@Test
	void propagatesWriteErrorsThatAreNotDuplicateKey11000() {
		MongoTemplate mongo = mock(MongoTemplate.class);
		MongoWriteException write = new MongoWriteException(
				new WriteError(121, "Document failed validation", new BsonDocument()), new ServerAddress());
		when(mongo.insert(any(UsageFactIdempotencyDocument.class))).thenThrow(write);

		MongoUsageFactRepository repository = new MongoUsageFactRepository(mongo);
		UsageFactRequest request = new UsageFactRequest("fact-1", "batch-1", "tenant-1", "product-1",
				UsageDomain.LOGS, List.of("record-1"), 1, 10, OffsetDateTime.parse("2026-11-09T12:00:00Z"), 1);
		UsageFactRepository.StoredUsageFact fact = new UsageFactRepository.StoredUsageFact(request,
				Instant.parse("2026-11-09T12:00:00Z"));

		assertThatThrownBy(() -> repository.saveIfAbsent(fact)).isSameAs(write);
		assertThat(MongoUsageFactRepository.isDuplicateKey(write)).isFalse();
	}
}
