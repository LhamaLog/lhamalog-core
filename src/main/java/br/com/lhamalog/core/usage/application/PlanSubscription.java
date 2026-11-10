package br.com.lhamalog.core.usage.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("PlanSubscription")
@CompoundIndex(name = "plan_subscription_tenant_product", def = "{'tenantId': 1, 'productId': 1}", unique = true)
public record PlanSubscription(@Id String id, @NotBlank String tenantId, @NotBlank String productId,
		@NotNull PlanType planType, @NotNull LocalDate firstPaymentDate) {}
