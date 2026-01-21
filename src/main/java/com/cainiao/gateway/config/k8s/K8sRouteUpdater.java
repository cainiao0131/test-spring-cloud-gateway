package com.cainiao.gateway.config.k8s;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class K8sRouteUpdater {

    private final K8sInMemoryRouteDefinitionRepository routeDefinitionRepository;
    private final KubernetesClient kubernetesClient;

    private final ApplicationEventPublisher applicationEventPublisher;

    private volatile Watch watch;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        closeWatch();

        routeDefinitionRepository.getRouteDefinitions()
            .filter(routeDefinition -> {
                String id = routeDefinition.getId();
                return id != null && id.startsWith(K8sRouteUtil.K8S_ROUTE_PREFIX);
            })
            .flatMap(routeDefinition -> {
                String id = routeDefinition.getId();
                assert id != null;
                return routeDefinitionRepository.delete(Mono.just(id));
            })
            .thenMany(
                Flux.fromIterable(kubernetesClient.services().inAnyNamespace().list().getItems())
                    .filter(K8sRouteUtil::shouldNamespaceUpdate)
                    .map(service -> {
                        ObjectMeta objectMeta = service.getMetadata();
                        return K8sRouteUtil.createRouteDefinition(objectMeta.getNamespace(),
                            objectMeta.getName(), K8sRouteUtil.getServicePort(service));
                    })
                    .flatMap(routeDefinition -> routeDefinitionRepository.save(Mono.just(routeDefinition)))
            )
            // TODO 待优化，避免服务频繁注册导致路由频繁更新，发布一个自定义事件，在自定事件监听器中统一防抖
            .then(Mono.fromRunnable(() -> applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this))))
            .subscribe();

        watch = kubernetesClient.services().inAnyNamespace().watch(new Watcher<>() {
            @Override
            public void eventReceived(Action action, Service service) {
                if (!K8sRouteUtil.shouldNamespaceUpdate(service)) {
                    return;
                }
                ObjectMeta objectMeta = service.getMetadata();
                Mono<Void> mono = updateRepository(action,
                    objectMeta.getNamespace(), objectMeta.getName(), K8sRouteUtil.getServicePort(service));
                if (mono == null) {
                    return;
                }
                mono.then(Mono.fromRunnable(() ->
                    applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this)))).subscribe();
            }

            @Override
            public void onClose(WatcherException cause) {
                // Optional: Reconnect logic if needed
            }
        });
    }

    private Mono<Void> updateRepository(Watcher.Action action, String namespace, String serviceName, Integer port) {
        return switch (action) {
            case ERROR ->
                // TODO ERROR 时重新连接 watch
                null;
            case MODIFIED, ADDED ->
                routeDefinitionRepository.save(Mono.just(K8sRouteUtil.createRouteDefinition(namespace, serviceName, port)));
            case DELETED ->
                routeDefinitionRepository.delete(Mono.just(K8sRouteUtil.buildRouteDefinitionId(namespace, serviceName)));
            default -> null;
        };
    }

    @PreDestroy
    public void closeWatch() {
        if (watch != null) {
            try {
                watch.close();
                log.info("Kubernetes services watch closed");
            } catch (Exception e) {
                log.warn("Error closing watch", e);
            } finally {
                watch = null;
            }
        }
    }

    public K8sRouteUpdater(K8sInMemoryRouteDefinitionRepository routeDefinitionRepository,
                           KubernetesClient kubernetesClient,
                           ApplicationEventPublisher applicationEventPublisher) {
        this.routeDefinitionRepository = routeDefinitionRepository;
        this.kubernetesClient = kubernetesClient;
        this.applicationEventPublisher = applicationEventPublisher;
    }
}
