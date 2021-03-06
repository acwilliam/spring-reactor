package com.acwilliam.reactive.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FluxTest {

    @BeforeAll
    public static void setUp(){
        BlockHound.install();
    }

    @Test
    public void blockHoundWorks(){
        try {
            FutureTask<?> task = new FutureTask<>(()->{
                Thread.sleep(0);
                return "";
            });
            Schedulers.parallel().schedule(task);

            task.get(10, TimeUnit.SECONDS);
            Assertions.fail("Deveria Falhar");
        }catch (Exception e){
            Assertions.assertTrue(e.getCause() instanceof BlockingOperationError);
        }

    }

    @Test
    public void fluxSubscriber(){
        Flux<String> fluxString = Flux.just("William", "Sabrina", "Mariah", "Cristian", "Aprendizado")
                .log();
        StepVerifier.create(fluxString)
                .expectNext("William", "Sabrina", "Mariah", "Cristian", "Aprendizado")
                .verifyComplete() ;

    }

    @Test
    public void fluxSubscriberNumbers(){
        Flux<Integer> flux= Flux.range(1,5)
                .log();

        flux.subscribe(i -> log.info("Number {} ", i));

        log.info("-------------------------------------");
        StepVerifier.create(flux)
                .expectNext(1,2,3,4,5)
                .verifyComplete() ;
    }

    @Test
    public void fluxSubscriberFromList(){
        Flux<Integer> flux= Flux.fromIterable(List.of(1,2,3,4,5))
                .log();

        flux.subscribe(i -> log.info("Number {} ", i));

        log.info("-------------------------------------");
        StepVerifier.create(flux)
                .expectNext(1,2,3,4,5)
                .verifyComplete() ;
    }

    @Test
    public void fluxSubscriberNumbersErros(){
        Flux<Integer> flux= Flux.range(1,5)
                .log()
                .map(i ->{
                    if(i==4){
                      throw new IndexOutOfBoundsException("Error de index");
                    }
                    return i;
                });

        flux.subscribe(i -> log.info("Number {} ", i), Throwable::printStackTrace,
                () -> log.info("Finalizado com sucesso"),
                subscription -> subscription.request(3));

        log.info("-------------------------------------");
        StepVerifier.create(flux)
                .expectNext(1,2,3)
                .expectError(IndexOutOfBoundsException.class)
                .verify();
    }

    @Test
    public void fluxSubscriberNumbersBackPressure(){
        Flux<Integer> flux= Flux.range(1,10)
                .log();

        flux.subscribe(new Subscriber<>() {
            private int count= 0;
            private Subscription subscription;
            private final int requestCount= 2;

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                subscription.request(requestCount);
            }

            @Override
            public void onNext(Integer integer) {
                count++;
                if(count >= requestCount){
                    count =0;
                    subscription.request(requestCount);
                }
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {

            }
        });

        log.info("-------------------------------------");
        StepVerifier.create(flux)
                .expectNext(1,2,3,4,5,6,7,8,9,10)
                .verifyComplete();
    }


    @Test
    public void fluxSubscriberNumbersNotSoUglyBackPressure(){
        Flux<Integer> flux= Flux.range(1,10)
                .log();

        flux.subscribe(new BaseSubscriber<>() {
            private int count= 0;
            private final int requestCount= 2;

            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                request(requestCount);
            }

            @Override
            protected void hookOnNext(Integer value) {
                count++;
                if(count >= requestCount){
                    count =0;
                    request(requestCount);
            }
          }

        });

        log.info("-------------------------------------");
        StepVerifier.create(flux)
                .expectNext(1,2,3,4,5,6,7,8,9,10)
                .verifyComplete();
    }

    @Test
    public void fluxSubscriberPrettyBackPressure(){
        Flux<Integer> flux= Flux.range(1,10)
                .log()
                .limitRate(3);

        flux.subscribe(i -> log.info("Number {} ", i));

        log.info("-------------------------------------");
        StepVerifier.create(flux)
                .expectNext(1,2,3,4,5,6,7,8,9,10)
                .verifyComplete() ;
    }

    @Test
    public void fluxSubscriberIntervaloOne() throws Exception {
        Flux<Long> intervalo = Flux.interval(Duration.ofMillis(100))
             .take(10)
             .log();
        intervalo.subscribe(i -> log.info("Number{} ", 1));

        Thread.sleep(3000);
    }

    @Test
    public void fluxSubscriberIntervaloTwo() throws Exception {
              StepVerifier.withVirtualTime(this::createInterval)
                .expectSubscription()
                .expectNoEvent(Duration.ofDays(1))
                .thenAwait(Duration.ofDays(1))
                .expectNext(0L)
                .thenAwait(Duration.ofDays(1))
                .expectNext(1L)
                .thenCancel()
                .verify();
    }

    private Flux<Long> createInterval() {
        return Flux.interval(Duration.ofDays(1))
                .log();
    }

    @Test
    public void connectableFlux() throws Exception{
        ConnectableFlux<Integer> connectableFlux = Flux.range(1, 10)
                .log()
                .delayElements(Duration.ofMillis(100))
                .publish();
        connectableFlux.connect();

       // log.info("Thread sleeping for 300 ms");

       // Thread.sleep(300);

       // connectableFlux.subscribe(i-> log.info("Sub1 numero {} ",i));

       // log.info("Thread sleeping for 200ms");

        //Thread.sleep(200);

        //connectableFlux.subscribe(i-> log.info("Sub2 numero {} ",i));

        StepVerifier
                .create(connectableFlux)
                .then(connectableFlux::connect)
                .thenConsumeWhile(i->i<=5)
                .expectNext(6,7,8,9,10)
                .expectComplete()
                .verify();


    }

    @Test
    public void connectableFluxAutoConnect() throws Exception{
        Flux<Integer> fluxAutoConnect = Flux.range(1, 5)
                .log()
                .delayElements(Duration.ofMillis(100))
                .publish()
                .autoConnect(2);

        StepVerifier
                .create(fluxAutoConnect)
                .then(fluxAutoConnect::subscribe)
                .expectNext(1,2,3,4,5)
                .expectComplete()
                .verify();


    }
}
