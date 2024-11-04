/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.backend.config.LogMarker;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
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
        future.complete(new NettingTaskResult(true, "New market order :  " + clOrdID));
    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {

    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        future.complete(new NettingTaskResult(false, "Rejected market order :  " + clOrdID));
    }

    protected CompletableFuture<NettingTaskResult> sendMarketOrder() {

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
            logger.error(errStr + " - " + ex.getMessage(), ex);
            future.complete(new NettingTaskResult(false, errStr));
        }
        return future;
    }

    @Override
    protected CompletableFuture<NettingTaskResult> run() {

        try {
            future = this.sendMarketOrder();
            NettingTaskResult result = future.get();
            if (result.isSuccess()) {

                if (order.getStoplossPrice() > 0) {
                    var stoplossTask
                            = new NettingStopLossTask(account, identifier, order, order.getStoplossPrice());
                    future = stoplossTask.run();
                    if (!future.get().isSuccess()) {
                        String errStr = "Incomplete Transaction. could not set stoploss on market order.";
                        logger.error(LogMarker.INCOMPLETE_TRANSACTION, errStr);
                        account.getOrderActionListener(order.getAccountNumber())
                                .onOrderRemoteError(identifier, order, errStr);
                        return future;
                    }
                }

                if (order.getTakeProfitPrice() > 0) {
                    var takeProfitTask
                            = new NettingTakeProfitTask(account, identifier, order, order.getTakeProfitPrice());
                    future = takeProfitTask.run();
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
        } catch (InterruptedException | ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
            account.getOrderActionListener(order.getAccountNumber())
                        .onOrderRemoteError(identifier, order, "Could not send market order - Something went wrong.");
        }

        return future;
    }

}
