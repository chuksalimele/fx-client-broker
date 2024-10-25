/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbok.fx.backend.listener;

import chuks.flatbok.fx.backend.account.contract.Identifier;
import chuks.flatbok.fx.common.account.order.ManagedOrder;
import java.util.List;

/**
 *
 * @author user
 */
public interface OrderActionListener {    
    Identifier onNewMarketOrder(String req_identifier, ManagedOrder order);
    Identifier onClosedMarketOrder(String req_identifier, ManagedOrder order);
    Identifier onModifiedMarketOrder(String req_identifier, ManagedOrder order);

    /**
     * 
     * @param req_identifier may not be neccessary of onTriggeredPendingOrder
     * @param order
     * @return 
     */
    Identifier onTriggeredPendingOrder(String req_identifier, ManagedOrder order);
    Identifier onNewPendingOrder(String req_identifier, ManagedOrder order);
    Identifier onDeletedPendingOrder(String req_identifier, ManagedOrder order);
    Identifier onModifiedPendingOrder(String req_identifier, ManagedOrder order);
    Identifier onOrderRemoteError(String req_identifier, ManagedOrder order, String errMsg);
    Identifier onOrderNotAvailable(String req_identifier, int account_number, String errMsg);
    Identifier onAddAllOpenOrders(String req_identifier, int account_number, List<ManagedOrder> order);
    Identifier onAddAllPendingOrders(String req_identifier, int account_number, List<ManagedOrder> order);
    Identifier onAddAllHistoryOrders(int account_number, List<ManagedOrder> order, String req_identifier);
}
