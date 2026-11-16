package br.com.lhamalog.core.usage.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("PlanSubscription")
public record PlanSubscription(@Id String id, @NotBlank String tenantId, @NotBlank String productId,
		@NotNull PlanType planType, @NotNull LocalDate firstPaymentDate,
		@NotNull @DecimalMin(value = "0.0") BigDecimal allowanceGigabytes, boolean unlimitedUsage,
		@NotNull SubscriptionStatus status, boolean overageEnabled) {

	public PlanSubscription(String id, String tenantId, String productId, PlanType planType,
			LocalDate firstPaymentDate) {
		this(id, tenantId, productId, planType, firstPaymentDate, BigDecimal.ZERO, false, SubscriptionStatus.ATIVA,
				false);
	}

	public PlanSubscription(String id, String tenantId, String productId, PlanType planType,
			LocalDate firstPaymentDate, BigDecimal allowanceGigabytes, boolean unlimitedUsage) {
		this(id, tenantId, productId, planType, firstPaymentDate, allowanceGigabytes, unlimitedUsage,
				SubscriptionStatus.ATIVA, false);
	}

	/**
	 * Reads legacy subscription documents created before allowance, unlimited-usage, status, and overage
	 * fields were introduced. Missing values map to ATIVA / overageEnabled=false.
	 */
	@PersistenceCreator
	public PlanSubscription(String id, String tenantId, String productId, PlanType planType,
			LocalDate firstPaymentDate, BigDecimal allowanceGigabytes, Boolean unlimitedUsage,
			SubscriptionStatus status, Boolean overageEnabled) {
		this(id, tenantId, productId, planType, firstPaymentDate,
				allowanceGigabytes == null ? BigDecimal.ZERO : allowanceGigabytes,
				Boolean.TRUE.equals(unlimitedUsage), status == null ? SubscriptionStatus.ATIVA : status,
				Boolean.TRUE.equals(overageEnabled));
	}
}
