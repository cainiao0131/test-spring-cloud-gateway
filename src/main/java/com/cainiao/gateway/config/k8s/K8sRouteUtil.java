package com.cainiao.gateway.config.k8s;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import lombok.experimental.UtilityClass;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Set;

@UtilityClass
class K8sRouteUtil {

    static final String K8S_ROUTE_PREFIX = "k8s-";

    private static final Set<String> NAMESPACE_BLACKLIST = Set
        .of("kube-system", "default", "calico-apiserver", "calico-system", "kuboard", "tigera-operator");

    static RouteDefinition createRouteDefinition(String namespace, String serviceName, Integer port) {
        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId(buildRouteDefinitionId(namespace, serviceName));
        routeDefinition
            .setPredicates(List.of(new PredicateDefinition("Path=/" + namespace + "/" + serviceName + "/**")));
        routeDefinition.setFilters(List.of(new FilterDefinition("StripPrefix=2")));
        routeDefinition.setUri(URI
            .create(String.format("http://%s.%s%s", serviceName, namespace, getPortString(port))));
        return routeDefinition;
    }

    private static String getPortString(Integer port) {
        if (port == null || port == 80 || port == 443) {
            return "";
        }
        return ":" + port;
    }

    static boolean shouldNamespaceUpdate(Service service) {
        ObjectMeta objectMeta = service.getMetadata();
        if (objectMeta == null) {
            return false;
        }
        String namespace = objectMeta.getNamespace();
        if (!StringUtils.hasText(namespace)) {
            return false;
        }
        return !NAMESPACE_BLACKLIST.contains(namespace);
    }

    static String buildRouteDefinitionId(String namespace, String serviceName) {
        return K8S_ROUTE_PREFIX + namespace + "-" + serviceName;
    }

    static Integer getServicePort(Service service) {
        ServiceSpec serviceSpec = service.getSpec();
        if (serviceSpec == null) {
            return null;
        }
        List<ServicePort> ports = serviceSpec.getPorts();
        if (ports == null || ports.isEmpty()) {
            return null;
        }
        return ports.getFirst().getPort();
    }
}
