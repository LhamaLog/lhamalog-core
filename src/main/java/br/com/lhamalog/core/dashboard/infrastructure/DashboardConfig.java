package br.com.lhamalog.core.dashboard.infrastructure;

import br.com.lhamalog.core.dashboard.application.DashboardAggregationProperties;
import br.com.lhamalog.core.dashboard.application.OpenObserveProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DashboardAggregationProperties.class, OpenObserveProperties.class})
public class DashboardConfig {
}
