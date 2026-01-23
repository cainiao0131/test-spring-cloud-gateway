package com.cainiao.gateway.config.serviceroute.k8s;

import com.cainiao.gateway.config.serviceroute.ServiceChangeEvent;
import com.cainiao.gateway.config.serviceroute.ServiceRouteUpdater;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

import static com.cainiao.gateway.config.serviceroute.ServiceChangeEvent.ChangeType;

@Slf4j
public class K8sServiceWatcher {

    private static final String K8S_SOURCE = "k8s";
    private static final Set<String> NAMESPACE_BLACKLIST = Set
        .of("kube-system", "default", "calico-apiserver", "calico-system", "kuboard", "tigera-operator");

    private final KubernetesClient kubernetesClient;
    private final ServiceRouteUpdater serviceRouteUpdater;

    private volatile Watch watch;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        closeWatch();

        watch = kubernetesClient.services().inAnyNamespace().watch(new Watcher<>() {
            @Override
            public void eventReceived(Action action, Service service) {
                ObjectMeta objectMeta = service.getMetadata();
                if (objectMeta == null) {
                    return;
                }
                String namespace = objectMeta.getNamespace();
                if (!StringUtils.hasText(namespace)) {
                    return;
                }
                if (NAMESPACE_BLACKLIST.contains(namespace)) {
                    return;
                }
                String serviceName = objectMeta.getName();
                Integer port = getServicePort(service);
                switch (action) {
                    case ERROR:
                        // TODO ERROR 时重新连接 watch
                        log.debug("updateRoute() >>> action == ERROR, service.toString(): {}", service);
                        break;
                    case ADDED:
                        serviceRouteUpdater.onServiceChange(
                            new ServiceChangeEvent(K8S_SOURCE, namespace, serviceName, port, ChangeType.ADD));
                        break;
                    case MODIFIED:
                        serviceRouteUpdater.onServiceChange(
                            new ServiceChangeEvent(K8S_SOURCE, namespace, serviceName, port, ChangeType.MODIFY));
                        break;
                    case DELETED:
                        serviceRouteUpdater.onServiceChange(
                            new ServiceChangeEvent(K8S_SOURCE, namespace, serviceName, port, ChangeType.DELETE));
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onClose(WatcherException cause) {
                // Optional: Reconnect logic if needed
            }
        });
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

    private static Integer getServicePort(Service service) {
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

    public K8sServiceWatcher(KubernetesClient kubernetesClient, ServiceRouteUpdater serviceRouteUpdater) {
        this.kubernetesClient = kubernetesClient;
        this.serviceRouteUpdater = serviceRouteUpdater;
    }
}
