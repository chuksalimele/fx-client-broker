/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Session;
import quickfix.SessionNotFound;
import quickfix.field.ClOrdID;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;

/**
 *
 * @author user
 */
public class NettingTakeProfitTask extends NettingTask {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingTakeProfitTask.class.getName());
    private final ManagedOrder order;
    private final double takeProfit;

    public NettingTakeProfitTask(OrderNettingAccount account, String identifier, ManagedOrder order, double takeProfit) {
        super(account, identifier);
        this.order = order;
        this.takeProfit = takeProfit;
    }

    @Override
    public void onNewOrder(String clOrdID) {
        future.complete(new NettingTaskResult(true, "Modified take profit successful "));
    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {

    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        future.complete(new NettingTaskResult(false, "Could not modify take prfoit - "+errMsg));        
    }


    @Override
    public CompletableFuture<NettingTaskResult> run() {

        try {
            order.modifyTakeProfit(identifier, takeProfit);

            if (order.getTakeProfitPrice() == 0) {
                //before this point will have already cancelled previous
                //target so now leave. No need to proceed.
                //Job already done!

                logger.debug("target is zero ");
                future.complete(new NettingTaskResult(true, "Target set to zero"));
                return future;
            }

            quickfix.fix44.NewOrderSingle targetOrder = new quickfix.fix44.NewOrderSingle(
                    new ClOrdID(order.getTakeProfitOrderID()),
                    new Side(account.opposingSide(order.getSide())),
                    new TransactTime(),
                    new OrdType(OrdType.LIMIT)
            );

            targetOrder.set(new Symbol(order.getSymbol()));
            targetOrder.set(new OrderQty(order.getLotSize() * ManagedOrder.FX_LOT_QTY)); // Set lot size to 1.2 lots (120,000 units)
            targetOrder.set(new Price(order.getTakeProfitPrice()));
            targetOrder.set(new TimeInForce(TimeInForce.GOOD_TILL_CANCEL)); // Set TIF to GTC

            Session.sendToTarget(targetOrder, account.getTradingSessionID());
        } catch (SQLException | SessionNotFound ex) {
            order.undoLastTakeProfitModify();
            String errStr = "Could not modify target";
            logger.error(errStr +" - "+ex.getMessage(), ex);
            future.complete(new NettingTaskResult(false, errStr));            

        }

        return future;
    }

}
