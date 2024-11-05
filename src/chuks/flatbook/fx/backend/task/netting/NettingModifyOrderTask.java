/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.task.netting;

import util.TaskResult;
import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
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
public class NettingModifyOrderTask extends NettingTask {

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
        future.complete(new TaskResult(true, "New order :  " + clOrdID));
    }

    @Override
    public void onCancelledOrder(String clOrdID) {
        future.complete(new TaskResult(true, "Successfully cancelled order :  " + clOrdID));
    }

    @Override
    public void onOrderCancelRequestRejected(String clOrdID, String reason) {
        future.complete(new TaskResult(false, "Could not cancel order :  " + clOrdID));
    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {

    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        future.complete(new TaskResult(false, "Rejected order :  " + clOrdID));
    }

    @Override
    protected CompletableFuture<TaskResult> run() {
        try {

            //cancel existing stoploss
            String stoplossID = order.getStoplossOrderID();
            if (stoplossID != null) {
                future = FixUtil.sendCancelRequest(account, order, stoplossID);
                if (!future.get().isSuccess()) {
                    return future;
                }
            }

            //internally modify stoploss
            order.modifyStoploss(identifier, stoploss);

            //send stoloss order to modify the stoploss
            future = FixUtil.sendStoplossOrderRequest(account, order);
            if (!future.get().isSuccess()) {
                order.undoLastStoplossModify();// undo internal modification               
                return future;
            }

            //cancel existing take profit
            String takeProfifID = order.getTakeProfitOrderID();
            if (takeProfifID != null) {
                future = FixUtil.sendCancelRequest(account, order, takeProfifID);
                if (!future.get().isSuccess()) {
                    return future;
                }
            }

            //internally modify take profit
            order.modifyTakeProfit(identifier, stoploss);

            //send take profit order to modify the stoploss
            future = FixUtil.sendTakeProfitOrderRequest(account, order);
            if (!future.get().isSuccess()) {
                order.undoLastTakeProfitModify();//undo internal modification
                return future;
            }

        } catch (SessionNotFound | SQLException | InterruptedException | ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return future;
    }
}
