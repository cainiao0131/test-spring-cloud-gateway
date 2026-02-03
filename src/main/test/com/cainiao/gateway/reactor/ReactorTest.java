package com.cainiao.gateway.reactor;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * <br />
 * <p>
 * Author: Cai Niao(wdhlzd@163.com)<br />
 */
public class ReactorTest {

    @Test
    public void test() {
        Flux<Integer> publisher = Flux.range(0, 10).delayElements(Duration.ofSeconds(1));

        publisher.subscribe(new Subscriber<>() {
            Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription) {
                System.out.println("onSubscribe() >>>");
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(Integer value) {
                System.out.println("onNext() >>> value: " + value);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Subscription s = this.subscription;
                if (s != null) {
                    s.request(1);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println("Error: " + throwable.getMessage());
            }

            @Override
            public void onComplete() {
                System.out.println("Completed!");
            }
        });

        Flux<Integer> processed = publisher.filter(n -> n % 2 == 0); // Processor 隐式创建
        processed.subscribe(System.out::println);

        Mono.delay(Duration.ofSeconds(30)).block();
    }
}
