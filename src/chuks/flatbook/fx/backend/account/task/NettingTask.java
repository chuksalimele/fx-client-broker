/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.Position;
import java.util.concurrent.CompletableFuture;
import chuks.flatbook.fx.backend.listener.BrokerFixOrderListener;

/**
 *
 * @author user
 */
abstract public class NettingTask implements BrokerFixOrderListener{
    protected CompletableFuture<NettingTaskResult> future = new CompletableFuture();
    protected final OrderNettingAccount account;
    protected final String identifier;
    
    public NettingTask(OrderNettingAccount account, String identifier){
        this.account = account;
        this.identifier = identifier;
    }
    
    protected OrderNettingAccount getAccount(){
        return this.account;
    }


    @Override
    public void onNewOrder(String clOrdID){}
    
    @Override
    public void onExecutedOrder(String clOrdID, double price){}

    @Override
    public void onCancelledOrder(String clOrdID){}

    @Override
    public void onOrderCancelRequestRejected(String clOrdID, String reason){}

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg){}

    @Override
    public void onPositionReport(Position position){}
            
    protected abstract CompletableFuture<NettingTaskResult> run();
}
