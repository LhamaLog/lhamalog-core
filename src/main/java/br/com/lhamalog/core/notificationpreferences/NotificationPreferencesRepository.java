package br.com.lhamalog.core.notificationpreferences;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationPreferencesRepository extends MongoRepository<NotificationPreferences, String> {}
