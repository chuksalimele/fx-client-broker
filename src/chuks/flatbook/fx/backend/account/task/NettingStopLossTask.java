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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Session;
import quickfix.SessionNotFound;
import quickfix.field.ClOrdID;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.Side;
import quickfix.field.StopPx;
import quickfix.field.Symbol;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;

/**
 *
 * @author user
 */
public class NettingStopLossTask extends NettingTask {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingStopLossTask.class.getName());
    
    private final ManagedOrder order;
    private final double stoploss;

    public NettingStopLossTask(OrderNettingAccount account, String identifier, ManagedOrder order, double stoploss) {
        super(account, identifier);
        this.order = order;
        this.stoploss = stoploss;
    }

    @Override
    public void onNewOrder(String clOrdID) {
        future.complete(new NettingTaskResult(true, "Modified stoploss successful "));
    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {

    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        future.complete(new NettingTaskResult(false, "Could not modify stoploss - "+errMsg));        
    }

    @Override
    public CompletableFuture<NettingTaskResult> run() {
        try {

            order.modifyStoploss(identifier, stoploss);

            if (order.getStoplossPrice() == 0) {
                //before this point will have already cancelled previous
                //stoploss so now leave. No need to proceed. 
                //Job already done!               

                logger.debug("stoploss is zero ");
                future.complete(new NettingTaskResult(true, "Stoploss set to zero"));
                return future;
            }

            quickfix.fix44.NewOrderSingle stopOrder = new quickfix.fix44.NewOrderSingle(
                    new ClOrdID(order.getStoplossOrderID()),
                    new Side(account.opposingSide(order.getSide())),
                    new TransactTime(),
                    new OrdType(OrdType.STOP_STOP_LOSS)
            );

            stopOrder.set(new Symbol(order.getSymbol()));
            stopOrder.set(new OrderQty(order.getLotSize() * ManagedOrder.FX_LOT_QTY)); // Set lot size to 1.2 lots (120,000 units)
            stopOrder.set(new StopPx(order.getStoplossPrice()));
            stopOrder.set(new TimeInForce(TimeInForce.GOOD_TILL_CANCEL)); // Set TIF to GTC
            Session.sendToTarget(stopOrder, account.getTradingSessionID());

            logger.debug("sending stoploss order ");

        } catch (SQLException | SessionNotFound ex) {
            order.undoLastStoplossModify();
            String errStr = "Could not modify stoploss";
            logger.error(errStr +" - "+ex.getMessage(), ex);
            future.complete(new NettingTaskResult(false, errStr));
        }

        return future;
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        CompletableFuture<NettingTaskResult> future = new CompletableFuture();

        future.complete(new NettingTaskResult(true, ""));
        
        System.out.println(future.get().getResult());
    }
}
