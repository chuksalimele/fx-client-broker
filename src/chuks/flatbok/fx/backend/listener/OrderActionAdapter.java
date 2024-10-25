/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbok.fx.backend.listener;

import chuks.flatbok.fx.backend.account.contract.Identifier;
import chuks.flatbok.fx.common.account.order.ManagedOrder;
import java.awt.Component;
import java.util.List;

/**
 *
 * @author user
 */
 abstract public class OrderActionAdapter implements OrderActionListener{

    private Component comp = null;
    private Identifier idf = new Identifier(){
            @Override
            public int getAccountNumber() {
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }
        };
    public OrderActionAdapter() {
    }

    public OrderActionAdapter(Component comp) {
        this.comp = comp;
    }

    public Component getComponent(){
        return comp;
    }
     
    @Override
    public Identifier onNewMarketOrder(ManagedOrder order) {
        return idf;
    }

    @Override
    public Identifier onClosedMarketOrder(ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onModifiedMarketOrder(ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onTriggeredPendingOrder(ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onNewPendingOrder(ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onDeletedPendingOrder(ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onModifiedPendingOrder(ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onOrderRemoteError(ManagedOrder order, String errMsg) {
        return idf;
    }

    @Override
    public Identifier onOrderNotAvailable(int account_number, String errMsg) {
        return idf;
    }    

    @Override
    public Identifier onAddAllHistoryOrders(int account_number, List<ManagedOrder> order) {
        return idf;
    }

    @Override
    public Identifier onAddAllOpenOrders(int account_number, List<ManagedOrder> order) {
        return idf;
    }

    @Override
    public Identifier onAddAllPendingOrders(int account_number, List<ManagedOrder> order) {
        return idf;
    }

}
