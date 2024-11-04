/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.backend.config.LogMarker;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import java.sql.SQLException;
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
import quickfix.field.Symbol;
import quickfix.field.TransactTime;
import quickfix.fix44.NewOrderSingle;

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
        future.complete(new NettingTaskResult(true, "Closed order :  " + clOrdID));
    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        future.complete(new NettingTaskResult(false, "Rejected close order :  " + clOrdID));
    }

    public CompletableFuture<NettingTaskResult> sendCloseOrder() {

        try {
            //Now we can send the close order
            // Send an order in the opposite direction
            // of the open position to close the open postion
            NewOrderSingle newOrder = new NewOrderSingle(
                    new ClOrdID(order.markForCloseAndGetID(identifier)), // Unique order ID                
                    new Side(account.opposingSide(order.getSide())), // Opposite of the original position
                    new TransactTime(),
                    new OrdType(OrdType.MARKET) // Market order to close the position
            );

            newOrder.set(new OrderQty(lot_size * ManagedOrder.FX_LOT_QTY)); // Quantity to close
            newOrder.set(new Symbol(order.getSymbol()));
            Session.sendToTarget(newOrder, account.getTradingSessionID());

        } catch (SQLException | SessionNotFound ex) {
            String errStr = "Could not close trade";
            logger.error(errStr + " - " + ex.getMessage(), ex);
            future.complete(new NettingTaskResult(false, errStr));
        }

        return future;
    }

    @Override
    public CompletableFuture<NettingTaskResult> run() {
        try {
            var cancelStoplosTask
                    = new NettingCancelIfOrderExistTask(account,
                            identifier, order,
                            order.getStoplossOrderID());

            var cancelTakeProfitTask
                    = new NettingCancelIfOrderExistTask(account,
                            identifier, order,
                            order.getTakeProfitOrderID());

            future = cancelTakeProfitTask.run();

            if (!future.get().isSuccess()) {
                String errStr = "Could not close order";
                logger.error(errStr);
                account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, errStr);                
                return future;
            }

            future = cancelStoplosTask.run();

            if (!future.get().isSuccess()) {
                String errStr = "Incomplete Transaction. Could not close order. Take Profit may have been cancelled";
                logger.error(LogMarker.INCOMPLETE_TRANSACTION, errStr);
                account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, errStr);                
                return future;
            }

            future = sendCloseOrder();

            if (!future.get().isSuccess()) {
                String errStr = "Incomplete Transaction. Could not close order. Stoploss and/or Take Profit may have been cancelled";
                logger.error(LogMarker.INCOMPLETE_TRANSACTION, errStr);
                account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, errStr);
                return future;
            }

        } catch (InterruptedException | ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
            account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, "Could not send closed order - Something went wrong.");
        }

        return future;
    }

}
