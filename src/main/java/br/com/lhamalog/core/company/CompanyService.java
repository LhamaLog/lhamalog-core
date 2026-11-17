package br.com.lhamalog.core.company;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.lhamalog.core.company.CompanyResponses.CompanyResponse;
import br.com.lhamalog.core.shared.FieldValidationException;
import tools.jackson.databind.JsonNode;

@Service
public class CompanyService {
	private final CompanyRepository companies;
	private final CompanyLogoRepository logos;
	private final CompanyContextRevisionRepository revisions;

	public CompanyService(CompanyRepository companies, CompanyLogoRepository logos,
			CompanyContextRevisionRepository revisions) {
		this.companies = companies;
		this.logos = logos;
		this.revisions = revisions;
	}

	public CompanyResponse get(String tenantId) {
		return present(load(tenantId));
	}

	public CompanyResponse save(String tenantId, String userId, JsonNode body) {
		Company current = load(tenantId);
		CompanyProfiles.Draft draft = CompanyProfiles.merge(current, body);
		if (!draft.errors().isEmpty()) {
			throw new FieldValidationException(draft.errors());
		}
		ensureVersion(current.version(), draft.requestedVersion());
		if (draft.logo().delete()) {
			logos.deleteById(tenantId);
		}
		if (draft.logo().store() != null) {
			logos.save(draft.logo().store());
		}
		Company saved = companies.save(audited(draft.company(), current.version(), userId));
		recordContextChange(current, saved, userId);
		return CompanyResponses.from(saved, draft.logo().delete() ? null : logos.findById(tenantId).orElse(null));
	}

	public List<CompanyContextRevision> contextRevisions(String tenantId) {
		return revisions.findByTenantIdOrderByRevisionDesc(tenantId);
	}

	public CompanyContextRevision contextRevision(String tenantId, long revision) {
		return revisions.findByTenantIdAndRevision(tenantId, revision)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Revisão de contexto não encontrada."));
	}

	public CompanyContextPrompt contextPrompt(String tenantId) {
		return CompanyContextPrompt.from(CompanyContextSnapshot.of(load(tenantId)));
	}

	public CompanyResponse saveLogo(String tenantId, String userId, Long version, String originalName, byte[] content) {
		Company current = load(tenantId);
		ensureVersion(current.version(), version);
		LogoFiles.Stored stored = LogoFiles.inspect(content, originalName);
		Instant now = Instant.now();
		String encoded = Base64.getEncoder().encodeToString(stored.content());
		logos.save(new CompanyLogo(tenantId, stored.contentType(), stored.fileName(), stored.content(), encoded, now));
		Company.Logo metadata = new Company.Logo(stored.fileName(), stored.contentType(), stored.content().length, now);
		CompanySettingsState settings = current.settings() == null ? null
				: current.settings().withLogo(new CompanySettingsState.Logo(stored.fileName(), stored.contentType()));
		Company updated = new Company(current.id(), current.clerkOrgId(), current.data(), current.context(), current.address(),
				current.preferences(), settings, metadata, now, userId, current.version());
		return CompanyResponses.from(companies.save(updated), logos.findById(tenantId).orElse(null));
	}

	public CompanyResponse deleteLogo(String tenantId, String userId, Long version) {
		Company current = load(tenantId);
		ensureVersion(current.version(), version);
		if (current.logo() == null && (current.settings() == null || current.settings().data().logo() == null)) {
			return present(current);
		}
		logos.deleteById(tenantId);
		CompanySettingsState settings = current.settings() == null ? null : current.settings().withLogo(null);
		Company updated = new Company(current.id(), current.clerkOrgId(), current.data(), current.context(), current.address(),
				current.preferences(), settings, null, Instant.now(), userId, current.version());
		return CompanyResponses.from(companies.save(updated), null);
	}

	/**
	 * Removes this organization's registration documents only.
	 * Products, product access, and the identity provider organization stay.
	 * No audit collection, metric, or event is written.
	 */
	public CompanyResponse deleteRegistration(String tenantId) {
		if (companies.findById(tenantId).isEmpty()) {
			return CompanyResponses.from(Company.empty(tenantId), null);
		}
		companies.deleteById(tenantId);
		logos.deleteById(tenantId);
		revisions.deleteByTenantId(tenantId);
		return CompanyResponses.from(Company.empty(tenantId), null);
	}

	public CompanyLogo logo(String tenantId) {
		Company current = companies.findById(tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "A empresa não tem logotipo."));
		if (current.logo() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "A empresa não tem logotipo.");
		}
		return logos.findById(tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "A empresa não tem logotipo."));
	}

	private CompanyResponse present(Company company) {
		return CompanyResponses.from(company, logos.findById(company.id()).orElse(null));
	}

	private Company load(String tenantId) {
		return companies.findById(tenantId).orElseGet(() -> Company.empty(tenantId));
	}

	private static Company audited(Company company, Long version, String userId) {
		return new Company(company.id(), company.clerkOrgId(), company.data(), company.context(), company.address(),
				company.preferences(), company.settings(), company.logo(), Instant.now(), userId, version);
	}

	private static void ensureVersion(Long current, Long requested) {
		if (!Objects.equals(current, requested)) {
			throw new OptimisticLockingFailureException("Company version conflict");
		}
	}

	private void recordContextChange(Company before, Company after, String userId) {
		CompanyContextSnapshot previous = CompanyContextSnapshot.of(before);
		CompanyContextSnapshot next = CompanyContextSnapshot.of(after);
		if (previous.equals(next)) {
			return;
		}
		long revision = revisions.findByTenantIdOrderByRevisionDesc(after.id()).stream()
				.mapToLong(CompanyContextRevision::revision).max().orElse(0L) + 1L;
		revisions.save(CompanyContextRevision.of(after.id(), revision, Instant.now(), userId, next));
	}
}
