package com.jjenus.tracker.shared.pubsub;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class SimpleEventBus implements EventPublisher, EventSubscriber {
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<EventHandler<?>>> handlers;
    private final ExecutorService executor;

    public SimpleEventBus() {
        this.handlers = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(10);
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }

        Class<?> eventType = event.getClass();
        CopyOnWriteArrayList<EventHandler<?>> eventHandlers = handlers.get(eventType);

        if (eventHandlers != null && !eventHandlers.isEmpty()) {
            for (EventHandler handler : eventHandlers) {
                try {
                    executor.submit(() -> {
                        try {
                            handler.handle(event);
                        } catch (Exception e) {
                            System.err.println("Error handling event: " + e.getMessage());
                        }
                    });
                } catch (RejectedExecutionException e) {
                    // Executor is shutting down, ignore
                    System.err.println("Event rejected from executor: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        if (eventType == null || handler == null) {
            return;
        }
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void unsubscribe(Class<? extends DomainEvent> eventType, EventHandler<?> handler) {
        if (eventType == null || handler == null) {
            return;
        }
        CopyOnWriteArrayList<EventHandler<?>> eventHandlers = handlers.get(eventType);
        if (eventHandlers != null) {
            eventHandlers.remove(handler);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
