/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import chuks.flatbook.fx.common.account.order.Position;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.LoggerFactory;

/**
 *
 * @author user
 */
public class NettingCancelIfOrderExistTask extends NettingCancelOrderTask {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingCancelOrderTask.class.getName());

    public NettingCancelIfOrderExistTask(OrderNettingAccount account, String identifier, ManagedOrder order, String orderID) {
        super(account, identifier, order, orderID);

    }

    @Override
    public void onCancelledOrder(String clOrdID) {

    }

    @Override
    public void onOrderCancelRequestRejected(String clOrdID, String reason) {

    }

    private NettingTaskResult checkOrderExist() throws InterruptedException, ExecutionException {
        var orderExistTask = new NettingOrderExistTask(account,
                identifier,
                orderID);
        future = orderExistTask.run();
        return future.get();
    }

    @Override
    public CompletableFuture<NettingTaskResult> run() {

        try {
            NettingTaskResult result = checkOrderExist();
            if (!result.isSuccess()) {
                future.complete(result);
                return future;
            }

        } catch (InterruptedException | ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
            future.complete(new NettingTaskResult(false, "Could not cancel order if exist"));
            return future;
        }
        return super.run();
    }

}
