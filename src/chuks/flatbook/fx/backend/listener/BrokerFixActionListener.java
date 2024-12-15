/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbook.fx.backend.listener;

import chuks.flatbook.fx.backend.account.BrokerAccountInfo;
import chuks.flatbook.fx.common.account.order.Position;
import chuks.flatbook.fx.common.account.order.UnfilledOrder;
import java.util.List;

/**
 *
 * @author user
 */
public interface BrokerFixActionListener {

    void onNewOrder(String clOrdID);

    void onRejectedOrder(String clOrdID, String errMsg);

    void onCancelledOrder(String clOrdID);

    void onOrderCancelRequestRejected(String clOrdID, String reason);

    void onExecutedOrder(String clOrdID, double price);
    
    void onPositionReport(List<Position> positionlist, String error);
    
    void onOrderReport(UnfilledOrder unfilledOrder, int totalOrders);
    
    void onOrderMassStatusReport(List<UnfilledOrder> unfilledOrderList);
    
    void onAccountInfoReport(BrokerAccountInfo brokerAccountInfo);
}
