/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author user
 */
abstract class NettingSequentialTask extends NettingTask {
    
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingSequentialTask.class.getName());
    private NettingTask[] tasks;

    public NettingSequentialTask(OrderNettingAccount account, String identifier){
        super(account, identifier);
    }
    
    public NettingSequentialTask(NettingTask... tasks) {
        super(null, null);
        this.tasks = tasks;
    }

    public void setSequence(NettingTask... tasks) {
        this.tasks = tasks;
    }
    
    abstract protected CompletableFuture<NettingTaskResult> finallyRun();

    @Override
    public CompletableFuture<NettingTaskResult> run() {
        
        CompletableFuture<NettingTaskResult> future = null;
        NettingTaskResult result;
        try {
            for (NettingTask task : tasks) {
                
                result = task.run().get();

                if (!result.isSuccess()) {
                    break;
                }

            }
        } catch (InterruptedException| ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
            if(future == null){
                future = new CompletableFuture();
                future.complete(new NettingTaskResult(false, "An error occurred while processing task"));
            }
        }

        return future;
    }
}
