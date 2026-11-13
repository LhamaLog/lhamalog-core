package br.com.lhamalog.core.company;

import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Company data (Empresa) of a tenant. It lives in the tenant document itself (ADR_121: the tenant is the company), next
 * to the fields owned by the tenant module (name, status). Writes must therefore be partial updates of data, context,
 * address and version only; never save this record as a whole document (see {@link CompanyService}).
 */
@Document(CompanyService.TENANT_COLLECTION)
public record Company(@Id String id, String clerkOrgId, @Valid Data data,
		@Valid Context context, @Valid Address address, Long version) {
	public record Data(@Size(max = 200) String legalName, @Size(max = 200) String tradeName,
			@Size(max = 200) String document, LocalDate registrationDate, @Size(max = 200) String size) {}
	public record Context(@Size(max = 200) String sector, @Size(max = 10000) String mission,
			@Size(max = 10000) String targetAudience, @Size(max = 10000) String differentiators,
			@Size(max = 200) String language) {}
	public record Address(@Size(max = 200) String street, @Size(max = 200) String number,
			@Size(max = 200) String complement, @Size(max = 200) String district,
			@Size(max = 200) String city, @Size(max = 200) String state,
			@Size(max = 200) String country, @Size(max = 200) String postalCode) {}

	/** Fills the parts never saved yet with empty values, keeping id, organization and version. */
	public Company withDefaults() {
		return new Company(id, clerkOrgId,
				data != null ? data : new Data("", "", "", null, ""),
				context != null ? context : new Context("", "", "", "", ""),
				address != null ? address : new Address("", "", "", "", "", "", "", ""), version);
	}
}
