package com.cainiao.gateway.config.serviceroute.k8s;

import com.cainiao.gateway.config.serviceroute.ServiceRouteUpdater;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class K8sRouteConfiguration {

    @Bean
    public ServiceRouteUpdater k8sServiceRouteUpdater(InMemoryRouteDefinitionRepository routeDefinitionRepository,
                                                      ApplicationEventPublisher applicationEventPublisher) {
        return new ServiceRouteUpdater(routeDefinitionRepository, applicationEventPublisher);
    }

    @Bean
    public InMemoryRouteDefinitionRepository routeDefinitionRepository() {
        return new InMemoryRouteDefinitionRepository();
    }

    @Bean
    public K8sServiceWatcher k8sRouteUpdater(KubernetesClient kubernetesClient,
                                             ServiceRouteUpdater serviceRouteUpdater) {
        return new K8sServiceWatcher(kubernetesClient, serviceRouteUpdater);
    }
}
