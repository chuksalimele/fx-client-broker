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
        TaskResult taskResult;
        boolean is_incomplete_trans = false;
        try {

            //cancel take profit order
            future = FixUtil.sendCancelRequest(account, order, order.getTakeProfitOrderID());

            taskResult = future.get();
            if (!taskResult.isSuccess()) {
                throw new OrderActionException(taskResult.getResult());
            }

            //cancel stoploss order
            future = FixUtil.sendCancelRequest(account, order, order.getStoplossOrderID());

            taskResult = future.get();
            if (!taskResult.isSuccess()) {
                is_incomplete_trans = true;
                throw new OrderActionException(taskResult.getResult());
            }

            //send close order
            future = FixUtil.sendCloseOrderRequest(account, identifier, order, lot_size);

            taskResult = future.get();
            if (!taskResult.isSuccess()) {
                is_incomplete_trans = true;
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
