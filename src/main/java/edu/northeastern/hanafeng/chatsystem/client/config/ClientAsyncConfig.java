package edu.northeastern.hanafeng.chatsystem.client.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("client")
@EnableAsync

public class ClientAsyncConfig {

    private final ClientConfig clientConfig;
    /**
     * Configure websocket connection thread pool
     * when start the client
     * it concurrent connect to the websocket server
     */

    @Bean(name = "websocketConnectionPool")

    public Executor connectionInitExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int numRooms = clientConfig.getNumRooms();

        executor.setCorePoolSize(numRooms);
        executor.setMaxPoolSize(numRooms*2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("conn-init-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Cofigure message producer thread
     * only one thread to generate messages
     */

     @Bean(name = "messageProducerExecutor")

     public Executor messageProducerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int producerThreads = clientConfig.getNumProducerThreads();
        executor.setCorePoolSize(producerThreads);
        executor.setMaxPoolSize(producerThreads);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("producer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
     }

     /**
      * Configure message consumer threads pool
      * one thread to consume messages from the shared message queue
      * and send to the websocket server
      */

      @Bean(name = "messageSenderExecutor")
      public Executor messageSenderExecutor() {
          ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
          
          int coreThreads = clientConfig.getSenderThreads();
          int maxThreads = coreThreads * 4;
          int taskQueueCapacity = clientConfig.getSenderTaskQueueCapacity();
          
          executor.setCorePoolSize(coreThreads);
          executor.setMaxPoolSize(maxThreads);
          executor.setQueueCapacity(taskQueueCapacity);
          executor.setKeepAliveSeconds(60);
          executor.setAllowCoreThreadTimeOut(false);
          executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
          executor.setThreadNamePrefix("sender-");
          executor.setWaitForTasksToCompleteOnShutdown(true);
          executor.setAwaitTerminationSeconds(300);
          executor.initialize();

          return executor;
      }

      /**
       * Configure message queue
       */

       @Bean(name = "sharedMessageQueue")
       public BlockingQueue<String> sharedMessageQueue() {
           int queueCapacity = clientConfig.getMessageQueueCapacity();
           log.info("Shared message queue configured: capacity={}", queueCapacity);
           return new LinkedBlockingQueue<>(queueCapacity);
       }
}
