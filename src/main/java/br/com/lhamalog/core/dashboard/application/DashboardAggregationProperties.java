package br.com.lhamalog.core.dashboard.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Scheduling for the SSID_011 dashboard aggregation job.
 *
 * @param cron Spring 6-field cron; default every hour at minute 0
 */
@ConfigurationProperties("lhamalog.dashboard.aggregation")
public record DashboardAggregationProperties(@DefaultValue("0 0 * * * *") String cron) {
}
