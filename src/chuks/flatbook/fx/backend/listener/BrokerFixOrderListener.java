/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbook.fx.backend.listener;

import chuks.flatbook.fx.common.account.order.Position;

/**
 *
 * @author user
 */
public interface BrokerFixOrderListener {

    void onNewOrder(String clOrdID);

    void onRejectedOrder(String clOrdID, String errMsg);

    void onCancelledOrder(String clOrdID);

    void onOrderCancelRequestRejected(String clOrdID, String reason);

    void onExecutedOrder(String clOrdID, double price);
    
    void onPositionReport(Position position);
}
