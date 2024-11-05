/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util;

import chuks.flatbook.fx.backend.account.Broker;
import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import quickfix.ConfigError;
import quickfix.Session;
import quickfix.SessionNotFound;
import quickfix.StringField;
import quickfix.field.Account;
import quickfix.field.AccountType;
import quickfix.field.ClOrdID;
import quickfix.field.ClearingBusinessDate;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.OrigClOrdID;
import quickfix.field.PosReqID;
import quickfix.field.PosReqType;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.StopPx;
import quickfix.field.Symbol;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;
import quickfix.fix44.RequestForPositions;

/**
 *
 * @author user
 */
public class FixUtil {

    public static CompletableFuture<TaskResult> sendMarketOrderRequest(Broker account, ManagedOrder order) throws SessionNotFound, SQLException {
        quickfix.fix44.NewOrderSingle newOrder = new quickfix.fix44.NewOrderSingle(
                new ClOrdID(order.getOrderID()),
                new Side(order.getSide()),
                new TransactTime(),
                new OrdType(OrdType.MARKET)
        );

        newOrder.set(new Symbol(order.getSymbol()));
        newOrder.set(new OrderQty(order.getLotSize() * ManagedOrder.FX_LOT_QTY));

        Session.sendToTarget(newOrder, account.getTradingSessionID());
        return new CompletableFuture();
    }

    public static CompletableFuture<TaskResult> sendTakeProfitOrderRequest(Broker account, ManagedOrder order) throws SessionNotFound, SQLException {
        quickfix.fix44.NewOrderSingle targetOrder = new quickfix.fix44.NewOrderSingle(
                new ClOrdID(order.getTakeProfitOrderID()),
                new Side(account.opposingSide(order.getSide())),
                new TransactTime(),
                new OrdType(OrdType.LIMIT)
        );

        targetOrder.set(new Symbol(order.getSymbol()));
        targetOrder.set(new OrderQty(order.getLotSize() * ManagedOrder.FX_LOT_QTY)); // Set lot size to 1.2 lots (120,000 units)
        targetOrder.set(new Price(order.getTakeProfitPrice()));
        targetOrder.set(new TimeInForce(TimeInForce.GOOD_TILL_CANCEL)); // Set TIF to GTC

        Session.sendToTarget(targetOrder, account.getTradingSessionID());

        return new CompletableFuture();
    }

    public static CompletableFuture<TaskResult> sendStoplossOrderRequest(Broker account, ManagedOrder order) throws SessionNotFound, SQLException {
        quickfix.fix44.NewOrderSingle stopOrder = new quickfix.fix44.NewOrderSingle(
                new ClOrdID(order.getStoplossOrderID()),
                new Side(account.opposingSide(order.getSide())),
                new TransactTime(),
                new OrdType(OrdType.STOP_STOP_LOSS)
        );

        stopOrder.set(new Symbol(order.getSymbol()));
        stopOrder.set(new OrderQty(order.getLotSize() * ManagedOrder.FX_LOT_QTY)); // Set lot size to 1.2 lots (120,000 units)
        stopOrder.set(new StopPx(order.getStoplossPrice()));
        stopOrder.set(new TimeInForce(TimeInForce.GOOD_TILL_CANCEL)); // Set TIF to GTC
        Session.sendToTarget(stopOrder, account.getTradingSessionID());

        return new CompletableFuture();
    }

    public static CompletableFuture<TaskResult> sendCloseOrderRequest(Broker account, String identifier, ManagedOrder order, double lot_size) throws SessionNotFound, SQLException {
        NewOrderSingle newOrder = new NewOrderSingle(
                new ClOrdID(order.markForCloseAndGetID(identifier)), // Unique order ID                
                new Side(account.opposingSide(order.getSide())), // Opposite of the original position
                new TransactTime(),
                new OrdType(OrdType.MARKET) // Market order to close the position
        );

        newOrder.set(new OrderQty(lot_size * ManagedOrder.FX_LOT_QTY)); // Quantity to close
        newOrder.set(new Symbol(order.getSymbol()));
        Session.sendToTarget(newOrder, account.getTradingSessionID());
        return new CompletableFuture();
    }

    public static CompletableFuture<TaskResult> sendCancelRequest(Broker account, ManagedOrder order, String orderID) throws SessionNotFound {
        OrderCancelRequest cancelRequest = new OrderCancelRequest(
                new OrigClOrdID(orderID),
                new ClOrdID("cancel-order-" + System.currentTimeMillis()),
                new Side(order.getSide()),
                new TransactTime()
        );

        cancelRequest.set(new OrderQty(order.getLotSize() * ManagedOrder.FX_LOT_QTY)); // Original order quantity
        cancelRequest.set(new Symbol(order.getSymbol()));
        Session.sendToTarget(cancelRequest, account.getTradingSessionID());
        return new CompletableFuture();
    }

    public static  CompletableFuture<TaskResult> sendPositionRequest(Broker account) throws ConfigError, SessionNotFound {
        RequestForPositions request = new RequestForPositions();
        request.set(new PosReqID("open-positions-" + System.currentTimeMillis())); // Unique request ID
        request.set(new PosReqType(PosReqType.POSITIONS)); // Request type for positions
        request.set(new Account(OrderNettingAccount.getSettings().getString("Account"))); //The account for which positions are requested
        request.setField(new StringField(715, "CURRENT"));//According to LP doc : ClearingBusinessDate, Local DateTime – currently not used ‘CURRENT’ or any other text will fit the requirements
        request.set(new AccountType(AccountType.ACCOUNT_IS_CARRIED_ON_CUSTOMER_SIDE_OF_THE_BOOKS));
        request.set(new ClearingBusinessDate());
        request.set(new TransactTime());

        Session.sendToTarget(request, account.getTradingSessionID());
        return new CompletableFuture();
    }
}
