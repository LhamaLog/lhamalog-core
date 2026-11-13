package br.com.lhamalog.core.tenant.domain;

/** Lifecycle status of a tenant. Only {@link #ACTIVE} grants access. */
public enum TenantStatus {

	ACTIVE,

	SUSPENDED

}
