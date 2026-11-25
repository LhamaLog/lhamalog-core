package br.com.lhamalog.core.config;

import org.springframework.security.oauth2.jwt.JwtDecoder;

import br.com.lhamalog.core.occurrence.application.OccurrenceModuleProperties;

/** Gives the occurrence tests access to the internal JWT decoder built by the security configuration. */
public final class InternalServiceSecurityConfigurationAccess {

	private InternalServiceSecurityConfigurationAccess() {
	}

	public static JwtDecoder decoder(OccurrenceModuleProperties.InternalAuth auth) {
		return InternalServiceSecurityConfiguration.decoder(auth);
	}

}
