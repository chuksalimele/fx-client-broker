/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import util.TaskResult;
import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.backend.config.LogMarker;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.LoggerFactory;
import quickfix.SessionNotFound;
import util.FixUtil;

/**
 *
 * @author user
 */
public class NettingMarketOrderTask extends NettingTask {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingMarketOrderTask.class.getName());
    private final ManagedOrder order;

    public NettingMarketOrderTask(OrderNettingAccount account, String identifier, ManagedOrder order) {
        super(account, identifier);
        this.order = order;
    }

    @Override
    public void onNewOrder(String clOrdID) {
        future.complete(new TaskResult(true, "New market order :  " + clOrdID));
    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {

    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        future.complete(new TaskResult(false, "Rejected market order :  " + clOrdID));
    }

    @Override
    protected CompletableFuture<TaskResult> run() {

        try {
            future = FixUtil.sendMarketOrderRequest(account, order);
            account.storeSentMarketOrder(order);
            
            TaskResult result = future.get();
            if (result.isSuccess()) {

                if (order.getStoplossPrice() > 0) {
                    //set stoploss
                    future = FixUtil.sendStoplossOrderRequest(account, order);
                    if (!future.get().isSuccess()) {
                        String errStr = "Incomplete Transaction. could not set stoploss on market order.";
                        logger.error(LogMarker.INCOMPLETE_TRANSACTION, errStr);
                        account.getOrderActionListener(order.getAccountNumber())
                                .onOrderRemoteError(identifier, order, errStr);
                        return future;
                    }
                }

                if (order.getTakeProfitPrice() > 0) {
                    future = FixUtil.sendTakeProfitOrderRequest(account, order);
                    if (!future.get().isSuccess()) {
                        String errStr = "Incomplete Transaction. could not set target on market order.";
                        logger.error(LogMarker.INCOMPLETE_TRANSACTION, errStr);
                        account.getOrderActionListener(order.getAccountNumber())
                                .onOrderRemoteError(identifier, order, errStr);
                        return future;
                    }
                }

            } else {
                account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, result.getResult());
                return future;
            }
        } catch (SessionNotFound | SQLException | InterruptedException | ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
            account.getOrderActionListener(order.getAccountNumber())
                    .onOrderRemoteError(identifier, order, "Could not send market order - Something went wrong.");
        }

        return future;
    }

}
