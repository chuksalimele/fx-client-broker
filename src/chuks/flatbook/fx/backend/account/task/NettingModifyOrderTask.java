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
public class NettingModifyOrderTask extends NettingTask {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingMarketOrderTask.class.getName());
    private final ManagedOrder order;
    private double stoploss;
    private double takeProfit;
    private double oldStoploss;
    private double oldTakeProfit;    

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
    public CompletableFuture<NettingTaskResult> run() {

        
        
        return future;
    }

}
