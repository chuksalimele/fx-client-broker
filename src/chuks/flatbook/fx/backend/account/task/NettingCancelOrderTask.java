/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import chuks.flatbook.fx.common.account.order.Position;
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;
import quickfix.Session;
import quickfix.SessionNotFound;
import quickfix.field.ClOrdID;
import quickfix.field.OrderQty;
import quickfix.field.OrigClOrdID;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TransactTime;
import quickfix.fix44.OrderCancelRequest;

/**
 *
 * @author user
 */
public class NettingCancelOrderTask extends NettingTask{
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingCancelOrderTask.class.getName());
    protected final ManagedOrder order;
    protected final String orderID;


    public NettingCancelOrderTask(OrderNettingAccount account, String identifier, ManagedOrder order, String orderID) {
        super(account, identifier);
        this.order = order;
        this.orderID = orderID;
    }

    
    @Override
    public void onCancelledOrder(String clOrdID) {
        future.complete(new NettingTaskResult(true, "Cancelled order:  "+clOrdID));
    }

    @Override
    public void onOrderCancelRequestRejected(String clOrdID, String reason) {
        future.complete(new NettingTaskResult(false, "Rejected cancel order :  "+clOrdID));
    }

    @Override
    public CompletableFuture<NettingTaskResult> run() {
        
        try {
            
            OrderCancelRequest cancelRequest = new OrderCancelRequest(
                    new OrigClOrdID(orderID),
                    new ClOrdID("cancel-order-" + System.currentTimeMillis()),
                    new Side(order.getSide()),
                    new TransactTime()
            );

            cancelRequest.set(new OrderQty(order.getLotSize() * ManagedOrder.FX_LOT_QTY)); // Original order quantity
            cancelRequest.set(new Symbol(order.getSymbol()));
            Session.sendToTarget(cancelRequest, account.getTradingSessionID());

        } catch (SessionNotFound ex) {
            String errStr = "Could not cancel order";
            logger.error(errStr +" - "+ex.getMessage(), ex);
            future.complete(new NettingTaskResult(false, errStr));                 
        }
        
        return future;
    }
    
}

