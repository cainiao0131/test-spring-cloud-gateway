package com.cainiao.gateway.config.k8s;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class K8sRouteConfiguration {

    @Bean
    public K8sInMemoryRouteDefinitionRepository routeDefinitionRepository() {
        return new K8sInMemoryRouteDefinitionRepository();
    }

    @Bean
    public K8sRouteUpdater k8sRouteUpdater(K8sInMemoryRouteDefinitionRepository routeDefinitionRepository,
                                           KubernetesClient kubernetesClient,
                                           ApplicationEventPublisher applicationEventPublisher) {
        return new K8sRouteUpdater(routeDefinitionRepository, kubernetesClient, applicationEventPublisher);
    }
}
