package com.phu.ecommerceapi.payment.infrastructure;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ProviderWebhookEventRepository extends JpaRepository<ProviderWebhookEventRecord, UUID> {

    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = """
            INSERT INTO provider_webhook_event (
                id,
                provider_name,
                provider_event_id,
                event_type,
                payload_hash,
                payload,
                processing_status,
                received_at,
                provider_event_created_at,
                provider_event_type,
                provider_object_id,
                provider_object_type,
                version
            )
            VALUES (
                :id,
                :providerName,
                :providerEventId,
                :eventType,
                :payloadHash,
                :payload,
                'RECEIVED',
                :receivedAt,
                :providerEventCreatedAt,
                :providerEventType,
                :providerObjectId,
                :providerObjectType,
                0
            )
            ON CONFLICT (provider_name, provider_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertReceived(
            @Param("id") UUID id,
            @Param("providerName") String providerName,
            @Param("providerEventId") String providerEventId,
            @Param("eventType") String eventType,
            @Param("payloadHash") String payloadHash,
            @Param("payload") String payload,
            @Param("receivedAt") OffsetDateTime receivedAt,
            @Param("providerEventCreatedAt") OffsetDateTime providerEventCreatedAt,
            @Param("providerEventType") String providerEventType,
            @Param("providerObjectId") String providerObjectId,
            @Param("providerObjectType") String providerObjectType
    );

    Optional<ProviderWebhookEventRecord> findByProviderNameAndProviderEventId(
            String providerName,
            String providerEventId
    );
}
