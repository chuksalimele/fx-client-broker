/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.type;

import chuks.flatbook.fx.backend.account.Broker;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import chuks.flatbook.fx.backend.account.contract.RetailAccountBuilder;
import static chuks.flatbook.fx.common.account.order.ManagedOrder.FX_LOT_QTY;
import chuks.flatbook.fx.common.account.order.OrderIDUtil;
import chuks.flatbook.fx.common.account.order.Position;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.ClOrdID;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.OrigClOrdID;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.StopPx;
import quickfix.field.Symbol;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;
import quickfix.fix44.OrderCancelReplaceRequest;

/**
 *
 * @author user
 */
public class HedgeAccount extends Broker{

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(HedgeAccount.class.getName());
    protected Map<String, ManagedOrder> sentModifiedOrders = Collections.synchronizedMap(new LinkedHashMap());

    public HedgeAccount(String settings_filename) throws ConfigError {
        super(settings_filename);
    }

    @Override
    public void sendMarketOrder(String req_identifier, ManagedOrder order) {

    }

    @Override
    public void deletePendingOrder(String req_identifier, String clOrdId) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void sendClosePosition(String req_identifier, String clOrdId, double lot_size) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void modifyOpenOrder(String req_identifier, String clOrdId, double target_price, double stoploss_price) {
        ManagedOrder order = this.ordersOpen.get(clOrdId);
        try {
            // Create a new OrderCancelReplaceRequest message
            OrderCancelReplaceRequest replaceRequest = new OrderCancelReplaceRequest(
                    new OrigClOrdID(clOrdId), // The original order's ID
                    new ClOrdID(OrderIDUtil.createModifyHedgeOrderID(clOrdId, req_identifier)), // New order ID for replacement
                    new Side(order.getSide()), // Side (Buy or Sell)
                    new TransactTime(),// Transaction time (current time)
                    new OrdType(OrdType.MARKET)                    
            );

            // Set new order details
            //replaceRequest.set(new OrderQty(newQuantity));  // New order quantity
            // Set new stop-loss, if provided
            if (stoploss_price >= 0) {
                replaceRequest.set(new StopPx(stoploss_price));
            }

            // Set new take-profit, if provided
            if (target_price >= 0) {
                replaceRequest.set(new Price(target_price));
            }

            // Send the replacement request
            Session.sendToTarget(replaceRequest, tradingSessionID);
            
            this.sentModifiedOrders.put(order.getOrderID(), order);
            
        } catch (SessionNotFound | SQLException ex) {
            logger.error("Could not send modify order", ex);
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not modify market order - Something Went Wrong!");
        }
    }

    @Override
    public void placePendingOrder(String req_identifier, ManagedOrder order) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void modifyPendingOrder(String req_identifier, String clOrdId, double open_price, double target_price, double stoploss_price) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void onNewOrder(String clOrdID) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void onCancelledOrder(String clOrdID) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void onPositionReport(Position position) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
    

    @Override
    public void onOrderCancelRequestRejected(String clOrdID, String reason) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    public static class Builder implements RetailAccountBuilder {

        String settings_filename;

        public Builder() {
        }

        @Override
        public Builder accountConfig(String settings_filename) throws ConfigError {
            this.settings_filename = settings_filename;
            return this;
        }

        public Broker build() throws ConfigError {
            return new HedgeAccount(this.settings_filename);
        }

    }

}
