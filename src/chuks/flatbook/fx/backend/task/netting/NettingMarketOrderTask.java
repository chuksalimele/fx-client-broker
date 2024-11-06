/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.task.netting;

import util.TaskResult;
import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.backend.config.LogMarker;
import static chuks.flatbook.fx.backend.config.LogMarker.INCOMPLETE_TRANSACTION;
import chuks.flatbook.fx.backend.exception.OrderActionException;
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
        String msg;
        if (clOrdID.equals(order.getOrderID())) {
            msg = "New market order";
            future.complete(new TaskResult(true, msg));
            logger.debug(msg);
        } else if (clOrdID.equals(order.getStoplossOrderID())) {
            msg = "New stoploss order for open order";
            future.complete(new TaskResult(true, msg));
            logger.debug(msg);
        } else if (clOrdID.equals(order.getTakeProfitOrderID())) {
            msg = "New take profit order for open order";
            future.complete(new TaskResult(true, msg));
            logger.debug(msg);
        }

    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {

    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        String result;
        if (clOrdID.equals(order.getOrderID())) {
            result = "Rejected market order - " + errMsg;
            future.complete(new TaskResult(false, result));
            logger.debug(result);
        } else if (clOrdID.equals(order.getStoplossOrderID())) {
            result = "Rejected stoploss order for open order - " + errMsg;
            future.complete(new TaskResult(false, result));
            logger.debug(result);
        } else if (clOrdID.equals(order.getTakeProfitOrderID())) {
            result = "Rejected take profit order for open order - " + errMsg;
            future.complete(new TaskResult(false, result));
            logger.debug(result);
        }
    }

    @Override
    protected CompletableFuture<TaskResult> run() {
        TaskResult taskResult;
        boolean is_incomplete_trans = false;
        try {
            future = FixUtil.sendMarketOrderRequest(account, order);
            account.storeSentMarketOrder(order);

            taskResult = future.get();
            if (taskResult.isSuccess()) {

                if (order.getStoplossPrice() > 0) {
                    //set stoploss
                    future = FixUtil.sendStoplossOrderRequest(account, order);
                    taskResult = future.get();
                    if (!taskResult.isSuccess()) {
                        is_incomplete_trans = true;
                        throw new OrderActionException(taskResult.getResult());
                    }
                }

                if (order.getTakeProfitPrice() > 0) {
                    future = FixUtil.sendTakeProfitOrderRequest(account, order);
                    taskResult = future.get();
                    if (!taskResult.isSuccess()) {
                        is_incomplete_trans = true;
                        throw new OrderActionException(taskResult.getResult());
                    }

                }

            } else {
                throw new OrderActionException(taskResult.getResult());
            }
        } catch (OrderActionException | SessionNotFound | SQLException | InterruptedException | ExecutionException ex) {

            String prefix = "";
            if (is_incomplete_trans) {
                prefix = "Incomplete transaction";
                logger.error(INCOMPLETE_TRANSACTION, ex.getMessage());
            } else {
                logger.error(ex.getMessage(), ex);
            }

            if (ex instanceof OrderActionException) {
                account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, prefix + " - " + ex.getMessage());
            } else {
                account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, prefix + " - " + "Something went wrong when creating market order");
            }

        }

        return future;
    }

}
