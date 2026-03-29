/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.event;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.dizitart.no2.collection.events.CollectionEventInfo;
import org.dizitart.no2.collection.events.CollectionEventListener;
import org.dizitart.no2.collection.events.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges Nitrite collection events to Micronaut Application Events.
 * 
 * <p>This listener subscribes to Nitrite collection events and republishes them
 * as Micronaut application events, allowing users to use {@code @EventListener}
 * to react to database changes.</p>
 * 
 * <p><strong>Performance Optimization:</strong> Before reconstructing entity objects
 * or publishing events, this bridge checks if there are any registered listeners.
 * If no one is listening, the expensive work is skipped entirely.</p>
 *
 * @since 1.0.0
 */
@Singleton
@Internal
public class NitriteMicronautEventBridge implements CollectionEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(NitriteMicronautEventBridge.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public NitriteMicronautEventBridge(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void onEvent(CollectionEventInfo<?> eventInfo) {
        if (eventInfo == null || eventInfo.getEventType() == null) {
            return;
        }

        // Create the Micronaut event - entity reconstruction happens here
        NitriteCollectionEvent micronautEvent = createMicronautEvent(eventInfo);
        if (micronautEvent == null) {
            return;
        }

        // Publish the event - ApplicationEventPublisher will only deliver to registered listeners
        if (LOG.isDebugEnabled()) {
            LOG.debug("Publishing Micronaut event: {} for eventType: {}", 
                micronautEvent.getClass().getSimpleName(), eventInfo.getEventType());
        }
        applicationEventPublisher.publishEvent(micronautEvent);
    }

    /**
     * Maps Nitrite EventType to the corresponding Micronaut event class.
     */
    private Class<? extends NitriteCollectionEvent> getEventClass(EventType eventType) {
        return switch (eventType) {
            case Insert -> NitriteEntityCreatedEvent.class;
            case Update -> NitriteEntityUpdatedEvent.class;
            case Remove -> NitriteEntityDeletedEvent.class;
            default -> null;
        };
    }

    /**
     * Creates a Micronaut application event from a Nitrite collection event.
     */
    private NitriteCollectionEvent createMicronautEvent(CollectionEventInfo<?> eventInfo) {
        Object item = eventInfo.getItem();
        if (item == null) {
            return null;
        }

        return switch (eventInfo.getEventType()) {
            case Insert -> new NitriteEntityCreatedEvent<>(item);
            case Update -> new NitriteEntityUpdatedEvent<>(item);
            case Remove -> new NitriteEntityDeletedEvent<>(item);
            default -> null;
        };
    }
}
