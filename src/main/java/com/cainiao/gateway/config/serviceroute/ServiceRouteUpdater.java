package com.cainiao.gateway.config.serviceroute;

import com.cainiao.gateway.config.serviceroute.k8s.InMemoryRouteDefinitionRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.cainiao.gateway.util.HttpUtil.buildURI;

@RequiredArgsConstructor
@Slf4j
public class ServiceRouteUpdater {

    private static final Duration MIN_UPDATE_INTERVAL = Duration.ofSeconds(5);
    private static final String ID_SEPARATOR = "@_@";
    // 限制一次更新的取数次数，避免不停有新事件，不停取，导致一直不更新路由
    private static final int BATCH_LIMIT = 10000;

    private final InMemoryRouteDefinitionRepository routeDefinitionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final ConcurrentHashMap<String, ServiceChangeEvent> lastEventOfServices = new ConcurrentHashMap<>();
    private final AtomicBoolean updateScheduled = new AtomicBoolean(false);

    public void onServiceChange(ServiceChangeEvent serviceChangeEvent) {
        lastEventOfServices.put(buildRouteDefinitionId(serviceChangeEvent.source(),
            serviceChangeEvent.systemName(), serviceChangeEvent.serviceName()), serviceChangeEvent);
        scheduleUpdateRoute();
    }

    private void scheduleUpdateRoute() {
        if (updateScheduled.compareAndSet(false, true)) {
            Mono.delay(MIN_UPDATE_INTERVAL)
                .publishOn(Schedulers.parallel())
                .flatMap(tick -> doUpdateRoute())
                .doFinally(signal -> {
                    updateScheduled.set(false);
                    if (!lastEventOfServices.isEmpty()) {
                        scheduleUpdateRoute();
                    }
                })
                .subscribe();
        }
    }

    private Mono<Void> doUpdateRoute() {
        Map<String, ServiceChangeEvent> eventMap = new HashMap<>();
        Iterator<Map.Entry<String, ServiceChangeEvent>> iterator = lastEventOfServices.entrySet().iterator();
        int count = 0;
        while (count++ < BATCH_LIMIT && iterator.hasNext()) {
            Map.Entry<String, ServiceChangeEvent> entry = iterator.next();
            iterator.remove();
            eventMap.put(entry.getKey(), entry.getValue());
        }
        if (!eventMap.isEmpty()) {
            List<Mono<Void>> monoList = new ArrayList<>();
            for (Map.Entry<String, ServiceChangeEvent> entry : eventMap.entrySet()) {
                monoList.add(updateRepository(entry.getKey(), entry.getValue()));
            }
            return Mono.when(monoList).onErrorResume(e -> Mono.empty())
                .then(Mono.fromRunnable(() -> applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this))));
        }
        return Mono.empty();
    }

    private Mono<Void> updateRepository(String routeDefinitionId, ServiceChangeEvent serviceChangeEvent) {
        String systemName = serviceChangeEvent.systemName();
        String serviceName = serviceChangeEvent.serviceName();
        RouteDefinition existedRouteDefinition = routeDefinitionRepository.getRouteDefinition(routeDefinitionId);
        return switch (serviceChangeEvent.changeType()) {
            case ADD, MODIFY -> {
                Integer port = serviceChangeEvent.port();
                if (existedRouteDefinition == null) {
                    yield routeDefinitionRepository.save(Mono
                        .just(createRouteDefinition(routeDefinitionId, systemName, serviceName, port)));
                }
                if (Objects.equals(port, getPort(existedRouteDefinition))) {
                    yield Mono.empty();
                }
                updateRouteDefinitionUri(existedRouteDefinition, systemName, serviceName, port);
                yield routeDefinitionRepository.save(Mono.just(existedRouteDefinition));
            }
            case DELETE -> existedRouteDefinition == null
                ? Mono.empty() : routeDefinitionRepository.delete(Mono.just(routeDefinitionId));
        };
    }

    private Integer getPort(@Nonnull RouteDefinition routeDefinition) {
        URI uri = routeDefinition.getUri();
        return uri == null ? null : uri.getPort();
    }

    static RouteDefinition createRouteDefinition(String routeDefinitionId,
                                                 String systemName, String serviceName, Integer port) {
        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId(routeDefinitionId);
        routeDefinition
            .setPredicates(List.of(new PredicateDefinition("Path=/" + systemName + "/" + serviceName + "/**")));
        routeDefinition.setFilters(List.of(new FilterDefinition("StripPrefix=2")));
        updateRouteDefinitionUri(routeDefinition, systemName, serviceName, port);
        return routeDefinition;
    }

    static void updateRouteDefinitionUri(RouteDefinition routeDefinition,
                                         String systemName, String serviceName, Integer port) {
        routeDefinition.setUri(buildURI("http", String.format("%s.%s", serviceName, systemName), port, null));
    }

    static String buildRouteDefinitionId(String source, String namespace, String serviceName) {
        return source + ID_SEPARATOR + namespace + ID_SEPARATOR + serviceName;
    }
}
