/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.task.netting;

import chuks.flatbook.fx.backend.task.Task;
import util.TaskResult;
import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
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
public class NettingModifyOrderTask extends Task {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingMarketOrderTask.class.getName());
    private final ManagedOrder order;
    private final double stoploss;
    private final double takeProfit;
    private final double oldStoploss;
    private final double oldTakeProfit;

    public NettingModifyOrderTask(OrderNettingAccount account, String identifier, ManagedOrder order, double stoploss, double takeProfit) {
        super(account, identifier);
        this.order = order;
        oldStoploss = order.getStoplossPrice();
        oldTakeProfit = order.getTakeProfitPrice();
        this.stoploss = stoploss;
        this.takeProfit = takeProfit;

    }

    @Override
    public void onNewOrder(String clOrdID) {
        String msg;
        if (clOrdID.equals(order.getStoplossOrderID())) {
            msg = "New stoploss order for market order";
            future.complete(new TaskResult(true, msg));
            logger.debug(msg);
        } else if (clOrdID.equals(order.getTakeProfitOrderID())) {
            msg = "New take profit order for market order";
            future.complete(new TaskResult(true, msg));
            logger.debug(msg);
        }

    }

    @Override
    public void onCancelledOrder(String clOrdID) {
        String msg;
        if (clOrdID.equals(order.getStoplossOrderID())) {
            msg = "Canelled old stoploss order succellfuly";
            future.complete(new TaskResult(true, msg));
            logger.debug(msg);
        } else if (clOrdID.equals(order.getTakeProfitOrderID())) {
            msg = "Cancelled old take profit order succellfuly";
            future.complete(new TaskResult(true, msg));
            logger.debug(msg);
        }
    }

    @Override
    public void onOrderCancelRequestRejected(String clOrdID, String reason) {
        String errMsg;
        if (clOrdID.equals(order.getStoplossOrderID())) {
            errMsg = "Could not cancel old stoploss so as to set new stoplos - " + reason;
            future.complete(new TaskResult(false, errMsg));
            logger.debug(errMsg);
        } else if (clOrdID.equals(order.getTakeProfitOrderID())) {
            errMsg = "Could not cancel old take profit so as to set new take profit - " + reason;
            future.complete(new TaskResult(false, errMsg));
            logger.debug(errMsg);
        }
    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {

    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        String err_msg;
        if (clOrdID.equals(order.getStoplossOrderID())) {
            err_msg = "Could not create new stoplos order - " + errMsg;
            future.complete(new TaskResult(false, err_msg));
            logger.debug(err_msg);
        } else if (clOrdID.equals(order.getTakeProfitOrderID())) {
            err_msg = "Could not create new take profit order - " + errMsg;
            future.complete(new TaskResult(false, err_msg));
            logger.debug(err_msg);
        }
    }

    @Override
    protected CompletableFuture<TaskResult> run() {
        TaskResult taskResult;
        boolean is_incomplete_trans = false;
        try {
            //cancel existing stoploss
            String stoplossID = order.getStoplossOrderID();
            if (stoplossID != null) {
                future = FixUtil.sendCancelRequest(account, order, stoplossID);
                taskResult = future.get();
                if (!taskResult.isSuccess()) {
                    throw new OrderActionException(taskResult.getResult());
                }
            }

            //internally modify stoploss
            order.modifyStoploss(identifier, stoploss);

            //send stoloss order to modify the stoploss
            future = FixUtil.sendStoplossOrderRequest(account, order);
            taskResult = future.get();
            if (!taskResult.isSuccess()) {
                order.undoLastStoplossModify();// undo internal modification               
                is_incomplete_trans = true;
                throw new OrderActionException(taskResult.getResult());
            }

            //cancel existing take profit
            String takeProfifID = order.getTakeProfitOrderID();
            if (takeProfifID != null) {
                future = FixUtil.sendCancelRequest(account, order, takeProfifID);
                taskResult = future.get();
                if (!taskResult.isSuccess()) {
                    is_incomplete_trans = true;
                    throw new OrderActionException(taskResult.getResult());
                }
            }

            //internally modify take profit
            order.modifyTakeProfit(identifier, stoploss);

            //send take profit order to modify the take profit
            future = FixUtil.sendTakeProfitOrderRequest(account, order);
            taskResult = future.get();
            if (!taskResult.isSuccess()) {
                order.undoLastTakeProfitModify();//undo internal modification
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
