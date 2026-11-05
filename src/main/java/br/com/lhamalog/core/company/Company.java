package br.com.lhamalog.core.company;

import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("Tenant")
public record Company(@Id String id, String clerkOrgId, @Valid Data data,
		@Valid Context context, @Valid Address address, @Version Long version) {
	public record Data(@Size(max = 200) String legalName, @Size(max = 200) String tradeName,
			@Size(max = 200) String document, LocalDate registrationDate, @Size(max = 200) String size) {}
	public record Context(@Size(max = 200) String sector, @Size(max = 10000) String mission,
			@Size(max = 10000) String targetAudience, @Size(max = 10000) String differentiators,
			@Size(max = 200) String language) {}
	public record Address(@Size(max = 200) String street, @Size(max = 200) String number,
			@Size(max = 200) String complement, @Size(max = 200) String district,
			@Size(max = 200) String city, @Size(max = 200) String state,
			@Size(max = 200) String country, @Size(max = 200) String postalCode) {}
	public static Company empty(String organizationId) {
		return new Company(organizationId, organizationId,
				new Data("", "", "", null, ""), new Context("", "", "", "", ""),
				new Address("", "", "", "", "", "", "", ""), null);
	}
}
