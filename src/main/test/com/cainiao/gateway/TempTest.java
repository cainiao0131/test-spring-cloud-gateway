package com.cainiao.gateway;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.cainiao.gateway.util.Util.print;

public class TempTest {

    @Test
    public void test() {
        Flux<Integer> publisher = Flux.range(0, 10).delayElements(Duration.ofSeconds(1));

        print("test() >>> before publisher.subscribe");

        publisher.subscribe(new Subscriber<>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription_) {
                subscription = subscription_;
                print("onSubscribe() >>> before subscription.request(1)");
                subscription.request(1);
                print("onSubscribe() >>> after subscription.request(1)");
            }

            @Override
            public void onNext(Integer value) {
                print("onNext() >>> value: " + value);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (subscription != null) {
                    print("onNext() >>> before subscription.request(1) >>> value: " + value);
                    subscription.request(1);
                    print("onNext() >>> after subscription.request(1) >>> value: " + value);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                print("onError() >>> throwable.getMessage(): " + throwable.getMessage());
            }

            @Override
            public void onComplete() {
                print("onComplete() >>>");
            }
        });
        print("test() >>> after publisher.subscribe");

        Flux<Integer> processed = publisher.filter(n -> n % 2 == 0);
        print("test() >>> before processed.subscribe");
        processed.subscribe(value -> {
            print("in processed >>> value: " + value);
        });
        print("test() >>> after processed.subscribe");

        Mono.delay(Duration.ofSeconds(30)).block();
    }
}
