package edu.ucsal.fiadopay.config;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class AsyncConfig {


@Bean(destroyMethod = "shutdown")
public ExecutorService fiadopayExecutor() {
// Fixed thread pool with bounded queue to avoid OOM; tuned for simulator
return new ThreadPoolExecutor(
4, // core
8, // max
60L, TimeUnit.SECONDS,
new LinkedBlockingQueue<>(500),
new ThreadPoolExecutor.CallerRunsPolicy()
);
}
}