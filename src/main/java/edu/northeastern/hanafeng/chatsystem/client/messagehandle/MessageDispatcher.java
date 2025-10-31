package edu.northeastern.hanafeng.chatsystem.client.messagehandle;

import edu.northeastern.hanafeng.chatsystem.client.config.ClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Message dispatcher
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDispatcher {

    private final BlockingQueue<String> sharedMessageQueue;
    private final MessageSendingService messageSendingService;
    private final ClientConfig clientConfig;


    public void start() {
        int numDispatchers = clientConfig.getDispatcherThreads();
        
        ExecutorService dispatcherExecutor = Executors.newFixedThreadPool(
            numDispatchers,
            r -> {
                Thread t = new Thread(r);
                t.setName("dispatcher-" + t.getId());
                return t;
            }
        );
        
        log.info("Starting {} dispatcher threads", numDispatchers);
        
        for (int i = 0; i < numDispatchers; i++) {
            final int dispatcherId = i;
            dispatcherExecutor.submit(() -> {
                log.info("Dispatcher-{} started", dispatcherId);
                try {
                    while (true) {
                        String jsonMessage = sharedMessageQueue.take();
                        
                        if ("POISON_PILL".equals(jsonMessage)) {
                            log.info("Dispatcher-{} received POISON_PILL, stopping", dispatcherId);
                            break;
                        }
                        
                        messageSendingService.sendMessageAsync(jsonMessage);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Dispatcher-{} interrupted", dispatcherId);
                } catch (Exception e) {
                    log.error("Dispatcher-{} error", dispatcherId, e);
                }
                log.info("Dispatcher-{} stopped", dispatcherId);
            });
        }
    }
}