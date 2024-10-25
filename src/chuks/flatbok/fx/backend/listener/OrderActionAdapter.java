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
    public Identifier onNewMarketOrder(String req_identifier, ManagedOrder order) {
        return idf;
    }

    @Override
    public Identifier onClosedMarketOrder(String req_identifier, ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onModifiedMarketOrder(String req_identifier, ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onTriggeredPendingOrder(String req_identifier, ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onNewPendingOrder(String req_identifier, ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onDeletedPendingOrder(String req_identifier, ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onModifiedPendingOrder(String req_identifier, ManagedOrder order) {        
        return idf;
    }

    @Override
    public Identifier onOrderRemoteError(String req_identifier, ManagedOrder order, String errMsg) {
        return idf;
    }

    @Override
    public Identifier onOrderNotAvailable(String req_identifier, int account_number, String errMsg) {
        return idf;
    }    

    @Override
    public Identifier onAddAllHistoryOrders(int account_number, List<ManagedOrder> order, String req_identifier) {
        return idf;
    }

    @Override
    public Identifier onAddAllOpenOrders(String req_identifier, int account_number, List<ManagedOrder> order) {
        return idf;
    }

    @Override
    public Identifier onAddAllPendingOrders(String req_identifier, int account_number, List<ManagedOrder> order) {
        return idf;
    }

}
