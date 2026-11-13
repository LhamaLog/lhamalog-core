package br.com.lhamalog.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import br.com.lhamalog.core.identity.adapter.in.security.ClerkAuthenticationConverter;
import br.com.lhamalog.core.identity.adapter.in.security.SecurityContextCurrentRequestContext;
import br.com.lhamalog.core.identity.adapter.out.clerk.ClerkBackendSessionRevocation;
import br.com.lhamalog.core.identity.application.AccessResolver;
import br.com.lhamalog.core.identity.application.port.CurrentRequestContext;
import br.com.lhamalog.core.identity.application.port.out.SessionRevocation;
import br.com.lhamalog.core.tenant.application.port.out.TenantRepository;

/** Wires the identity module. Application services are plain classes, created here instead of being annotated. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClerkProperties.class)
public class IdentityConfig {

	@Bean
	AccessResolver accessResolver(TenantRepository tenantRepository) {
		return new AccessResolver(tenantRepository);
	}

	@Bean
	ClerkAuthenticationConverter clerkAuthenticationConverter(AccessResolver accessResolver) {
		return new ClerkAuthenticationConverter(accessResolver);
	}

	@Bean
	CurrentRequestContext currentRequestContext() {
		return new SecurityContextCurrentRequestContext();
	}

	/**
	 * Plain {@code RestClient.builder()} from spring-web: Boot's restclient module is not on the classpath and no new
	 * dependency is added for it.
	 */
	@Bean
	SessionRevocation sessionRevocation(ClerkProperties clerk) {
		return new ClerkBackendSessionRevocation(RestClient.builder(), clerk.apiUrl(), clerk.secretKey(),
				clerk.connectTimeout(), clerk.readTimeout());
	}

}
