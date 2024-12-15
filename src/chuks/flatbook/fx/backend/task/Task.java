/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.task;

import chuks.flatbook.fx.backend.account.Broker;
import chuks.flatbook.fx.backend.account.BrokerAccountInfo;
import util.TaskResult;
import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.Position;
import java.util.concurrent.CompletableFuture;
import chuks.flatbook.fx.common.account.order.UnfilledOrder;
import chuks.flatbook.fx.backend.listener.BrokerFixActionListener;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author user
 */
abstract public class Task implements BrokerFixActionListener{
    protected CompletableFuture<TaskResult> future = new CompletableFuture();
    protected final Broker account;
    protected final String identifier;
    
    public Task(Broker account, String identifier){
        this.account = account;
        this.identifier = identifier;          
    }
    
    protected Broker getAccount(){
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
    public void onPositionReport(List<Position> positionlist, String error){}

    @Override
    public void onOrderReport(UnfilledOrder unfilledOrder, int totalOrders) {}

    @Override
    public void onAccountInfoReport(BrokerAccountInfo brokerAccountInfo) {}

    @Override
    public void onOrderMassStatusReport(List<UnfilledOrder> unfilledOrderList) {}
    
    
    
    
            
    protected abstract CompletableFuture<TaskResult> run();
}
