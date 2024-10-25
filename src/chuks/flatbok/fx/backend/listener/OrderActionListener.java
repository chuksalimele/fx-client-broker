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
    Identifier onNewMarketOrder(ManagedOrder order);
    Identifier onClosedMarketOrder(ManagedOrder order);
    Identifier onModifiedMarketOrder(ManagedOrder order);
    Identifier onTriggeredPendingOrder(ManagedOrder order);
    Identifier onNewPendingOrder(ManagedOrder order);
    Identifier onDeletedPendingOrder(ManagedOrder order);
    Identifier onModifiedPendingOrder(ManagedOrder order);
    Identifier onOrderRemoteError(ManagedOrder order, String errMsg);
    Identifier onOrderNotAvailable(int account_number, String errMsg);
    Identifier onAddAllOpenOrders(int account_number, List<ManagedOrder> order);
    Identifier onAddAllPendingOrders(int account_number, List<ManagedOrder> order);
    Identifier onAddAllHistoryOrders(int account_number, List<ManagedOrder> order);
}
