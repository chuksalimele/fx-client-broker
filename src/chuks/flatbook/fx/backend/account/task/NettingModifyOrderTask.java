/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

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
    protected CompletableFuture<NettingTaskResult> run() {
        try {
            var cancelStoplosTask =
                    new NettingCancelIfOrderExistTask(account,
                            identifier, order,
                            order.getStoplossOrderID());
            
            var cancelTakeProfitTask =
                    new NettingCancelIfOrderExistTask(account,
                            identifier, order,
                            order.getTakeProfitOrderID());
            
            var modifyStoplosTask =
                    new NettingStopLossTask(account,
                            identifier, order, stoploss);
            
            var modifyTakeProfitTask =
                    new NettingTakeProfitTask(account,
                            identifier, order, takeProfit);
            
            future = cancelStoplosTask.run();
            
            if(!future.get().isSuccess()){
                return future;
            }
            
            future = modifyStoplosTask.run();
                        
            if(!future.get().isSuccess()){
                order.undoLastStoplossModify();
                //TODO resotre cancelled stoploss task goes here
                return future;
            }
            
            future = cancelTakeProfitTask.run();
            
            if(!future.get().isSuccess()){
                return future;
            }
            
            future = modifyTakeProfitTask.run();
            
            if(!future.get().isSuccess()){
                order.undoLastTakeProfitModify();
                //resotre cancelled take profit task 
                //var takeProfitTask
                //            = new NettingTakeProfitTask(account, identifier, order, oldTakeProfit);
                
                return future;
            }            
            
            
            
        } catch (InterruptedException | ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        }
                
       return future; 
    }
}
