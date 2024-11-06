/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.task.netting;

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
public class NettingCloseTask extends NettingTask {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingCloseTask.class.getName());
    private final ManagedOrder order;
    private final double lot_size;

    public NettingCloseTask(OrderNettingAccount account, String identifier, ManagedOrder order, double lot_size) {
        super(account, identifier);
        this.order = order;
        this.lot_size = lot_size;
    }

    @Override
    public void onNewOrder(String clOrdID) {
        if (clOrdID.equals(order.getCloseOrderID())) {
            String errMsg = "Created close order";
            future.complete(new TaskResult(true, errMsg));
            logger.debug(errMsg + " - " + clOrdID);
        } 
    }

    @Override
    public void onCancelledOrder(String clOrdID) {
        if (clOrdID.equals(order.getStoplossOrderID())) {
            String errMsg = "Cancelled stoploss order";
            future.complete(new TaskResult(true, errMsg));
            logger.debug(errMsg + " - " + clOrdID);
        } else if (clOrdID.equals(order.getTakeProfitOrderID())) {
            String errMsg = "Cancelled take profit order";
            future.complete(new TaskResult(true, errMsg));
            logger.debug(errMsg + " - " + clOrdID);
        }
    }

    @Override
    public void onOrderCancelRequestRejected(String clOrdID, String reason) {
        if (clOrdID.equals(order.getStoplossOrderID())) {
            String errMsg = "Could not cancel stoploss order - " + reason;
            future.complete(new TaskResult(false, errMsg));
            logger.debug(errMsg + " - " + clOrdID);
        } else if (clOrdID.equals(order.getTakeProfitOrderID())) {
            String errMsg = "Could not cancel take profit order - " + reason;
            future.complete(new TaskResult(false, errMsg));
            logger.debug(errMsg + " - " + clOrdID);
        }
    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        if (clOrdID.equals(order.getCloseOrderID())) {
            future.complete(new TaskResult(false, "Rejected close order - " + errMsg));
            logger.debug(errMsg + " - " + clOrdID);
        }
    }

    @Override
    public CompletableFuture<TaskResult> run() {
        try {

            //cancel take profit order
            future = FixUtil.sendCancelRequest(account, order, order.getTakeProfitOrderID());

            if (!future.get().isSuccess()) {
                String errStr = "Could not close order";
                logger.error(errStr);
                account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, errStr);
                return future;
            }

            //cancel stoploss order
            future = FixUtil.sendCancelRequest(account, order, order.getStoplossOrderID());

            if (!future.get().isSuccess()) {
                String errStr = "Incomplete Transaction. Could not close order. Take Profit may have been cancelled";
                logger.error(LogMarker.INCOMPLETE_TRANSACTION, errStr);
                account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, errStr);
                return future;
            }

            //send close order
            future = FixUtil.sendCloseOrderRequest(account, identifier, order, lot_size);

            if (!future.get().isSuccess()) {
                String errStr = "Incomplete Transaction. Could not close order. Stoploss and/or Take Profit may have been cancelled";
                logger.error(LogMarker.INCOMPLETE_TRANSACTION, errStr);
                account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, errStr);
                return future;
            }

        } catch (SessionNotFound | SQLException | InterruptedException | ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
            account.getOrderActionListener(order.getAccountNumber())
                    .onOrderRemoteError(identifier, order, "Could not send closed order - Something went wrong.");
        }

        return future;
    }

}
