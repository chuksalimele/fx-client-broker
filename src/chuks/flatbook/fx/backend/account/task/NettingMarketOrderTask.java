/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;
import quickfix.Session;
import quickfix.SessionNotFound;
import quickfix.field.ClOrdID;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TransactTime;

/**
 *
 * @author user
 */
public class NettingMarketOrderTask extends NettingSequentialTask {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingMarketOrderTask.class.getName());
    private final ManagedOrder order;

    public NettingMarketOrderTask(OrderNettingAccount account, String identifier, ManagedOrder order) {
        super(account, identifier);
        this.order = order;
        if (order.getStoplossPrice() > 0 && order.getTakeProfitPrice() > 0) {
            var stoplossTask
                    = new NettingStopLossTask(account, identifier, order);
            var takeProfitTask
                    = new NettingTakeProfitTask(account, identifier, order);

            setSequence(stoplossTask, takeProfitTask);

        } else if (order.getStoplossPrice() > 0) {
            var stoplossTask
                    = new NettingStopLossTask(account, identifier, order);
            setSequence(stoplossTask);
        } else if (order.getTakeProfitPrice() > 0) {
            var takeProfitTask
                    = new NettingTakeProfitTask(account, identifier, order);
            setSequence(takeProfitTask);
        }        
    }

    @Override
    public void onNewOrder(String clOrdID) {
        future.complete(new NettingTaskResult(true, "New market order :  "+clOrdID));
    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {
        
    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        future.complete(new NettingTaskResult(false, "Rejected market order :  "+clOrdID));
    }

    @Override
    protected CompletableFuture<NettingTaskResult> finallyRun() {
        
        try {
            quickfix.fix44.NewOrderSingle newOrder = new quickfix.fix44.NewOrderSingle(
                    new ClOrdID(order.getOrderID()),
                    new Side(order.getSide()),
                    new TransactTime(),
                    new OrdType(OrdType.MARKET)
            );
            
            newOrder.set(new Symbol(order.getSymbol()));
            newOrder.set(new OrderQty(order.getLotSize() * ManagedOrder.FX_LOT_QTY)); // Set lot size to 1.2 lots (120,000 units)

            Session.sendToTarget(newOrder, account.getTradingSessionID());
        } catch (SessionNotFound ex) {
            String errStr = "Could  not send market order";
            logger.error(errStr +" - "+ex.getMessage(), ex);
            future.complete(new NettingTaskResult(false, errStr));                
        }
        return future;
    }

}
