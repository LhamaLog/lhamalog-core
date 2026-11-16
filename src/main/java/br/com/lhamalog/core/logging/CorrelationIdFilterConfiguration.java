package br.com.lhamalog.core.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class CorrelationIdFilterConfiguration {

	@Bean
	FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
		FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(
				new CorrelationIdFilter());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		registration.addUrlPatterns("/*");
		return registration;
	}
}
