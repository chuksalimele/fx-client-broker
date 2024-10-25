/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbok.fx.backend.account.type;

import chuks.flatbok.fx.backend.account.Broker;
import chuks.flatbok.fx.common.account.order.ManagedOrder;
import java.util.*;
import quickfix.*;
import quickfix.field.*;
import chuks.flatbok.fx.backend.account.contract.OrderNettingAccountBuilder;
import static chuks.flatbok.fx.common.account.order.ManagedOrder.FX_LOT_QTY;
import chuks.flatbok.fx.common.account.order.SymbolInfo;
import chuks.flatbok.fx.common.account.persist.OrderDB;
import chuks.flatbok.fx.backend.listener.OrderActionListener;
import chuks.flatbok.fx.common.account.order.OrderException;
import chuks.flatbok.fx.common.account.order.OrderIDFamily;
import static chuks.flatbok.fx.common.account.order.OrderIDFamily.getAccountNumberFromOrderID;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;
import quickfix.fix44.NewOrderSingle;

/**
 *
 * @author user
 */
public class OrderNettingAccount extends Broker {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrderNettingAccount.class.getName());

    public OrderNettingAccount(String settings_filename) throws ConfigError {
        super(settings_filename);
    }

    @Override
    public void sendMarketOrder(String req_identifier, ManagedOrder order) {
        Session session = Session.lookupSession(tradingSessionID);
        if (session == null) {
            logger.error("Session not found. Cannot send market order.");
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not send market order - Something went wrong!");

            return;
        }

        if (!session.isLoggedOn()) {
            logger.error("Session is not logged on. Cannot send market order.");

            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not send market order - Something went wrong!");
            return;
        }

        //check if an opposing side is a market order already
        for (Map.Entry<String, ManagedOrder> entry : this.ordersOpen.entrySet()) {
            ManagedOrder open_order = entry.getValue();
            if (order.getSymbol().equals(open_order.getSymbol())
                    && order.getSide() != open_order.getSide()) {
                String err_msg = "Operation is not allowed for Order Nettin account. "
                        + "You have opposite open order of the same instrument. "
                        + "You cannot open two opposing sides (BUY/SELL) of orders of same instrument. You can only open all SELLs or all BUYs. "
                        + "To open a different side please close all opposite sides of the same instrument.";

                logger.error(err_msg);
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, err_msg);
                return;
            }
        }

        for (Map.Entry<String, ManagedOrder> entry : this.ordersPending.entrySet()) {
            ManagedOrder pend_order = entry.getValue();
            if (order.getSymbol().equals(pend_order.getSymbol())
                    && order.getSide() != pend_order.getSide()) {

                String err_msg = "Operation is not allowed for Order Nettin account. "
                        + "You have opposite open order of the same instrument. "
                        + "You cannot open two opposing sides (BUY/SELL) of orders of same instrument. You can only open all SELLs or all BUYs. "
                        + "To open a different side please cancel all opposite sides pending order of the same instrument.";

                logger.error(err_msg);
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, err_msg);
                return;
            }
        }

        super.sendMarketOrder(req_identifier, order);

        if (order.getStoplossPrice() > 0) {
            setStopLoss(req_identifier, order);
        }
        if (order.getTargetPrice() > 0) {
            setTakeProfit(req_identifier, order);
        }
        this.sentMarketOrders.put(order.getOrderID(), order);
    }

    private void setStopLoss(String req_identifier, ManagedOrder order) {

        try {
            //first cancel any previous stoploss orders attached to the market order
            List<String> ST_orderIDs = order.getStoplossOrderIDList();
            for (String ST_orderID : ST_orderIDs) {
                order.addStoplossOrderIDCancelProcessing(ST_orderID);
                cancelOrder(ST_orderID,
                        order.getSymbol(),
                        order.getSide(),
                        order.getLotSize());

                logger.debug("cancelling stoploss with ID " + ST_orderID);
            }

            if (order.getStoplossPrice() == 0) {
                //before this point will have already cancelled previous
                //stoploss so now leave. No need to proceed. 
                //Job already done!               

                logger.debug("stoploss is zero ");

                return;
            }

            quickfix.fix44.NewOrderSingle stopOrder = new quickfix.fix44.NewOrderSingle(
                    new ClOrdID(order.getLastStoplossOrderID()),
                    new Side(this.opposingSide(order.getSide())),
                    new TransactTime(),
                    new OrdType(OrdType.STOP_STOP_LOSS)
            );

            stopOrder.set(new Symbol(order.getSymbol()));
            stopOrder.set(new OrderQty(order.getLotSize() * FX_LOT_QTY)); // Set lot size to 1.2 lots (120,000 units)
            stopOrder.set(new StopPx(order.getStoplossPrice()));
            stopOrder.set(new TimeInForce(TimeInForce.GOOD_TILL_CANCEL)); // Set TIF to GTC
            Session.sendToTarget(stopOrder, this.tradingSessionID);

            logger.debug("sending stoploss order ");

        } catch (SessionNotFound ex) {
            logger.error("Session is not logged on. Cannot send order.");
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not set stoploss - Something went wrong!");
        }
    }

    private void setTakeProfit(String req_identifier, ManagedOrder order) {
        try {

            //first cancel any previous target orders attached to the market order
            List<String> TP_orderIDs = order.getTargetOrderIDList();
            for (String TP_orderID : TP_orderIDs) {
                order.addTargetOrderIDCancelProcessing(TP_orderID);
                cancelOrder(TP_orderID,
                        order.getSymbol(),
                        order.getSide(),
                        order.getLotSize());

                logger.debug("cancelling target with ID " + TP_orderID);
            }

            if (order.getTargetPrice() == 0) {
                //before this point will have already cancelled previous
                //target so now leave. No need to proceed. 
                //Job already done!    

                logger.debug("target is zero");

                return;
            }

            quickfix.fix44.NewOrderSingle targetOrder = new quickfix.fix44.NewOrderSingle(
                    new ClOrdID(order.getLastTargetOrderID()),
                    new Side(opposingSide(order.getSide())),
                    new TransactTime(),
                    new OrdType(OrdType.LIMIT)
            );

            targetOrder.set(new Symbol(order.getSymbol()));
            targetOrder.set(new OrderQty(order.getLotSize() * FX_LOT_QTY)); // Set lot size to 1.2 lots (120,000 units)
            targetOrder.set(new Price(order.getTargetPrice()));
            targetOrder.set(new TimeInForce(TimeInForce.GOOD_TILL_CANCEL)); // Set TIF to GTC

            Session.sendToTarget(targetOrder, tradingSessionID);

            logger.debug("sending target order ");

        } catch (SessionNotFound ex) {

            logger.error("Could not set target", ex);
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not set target - Something went wrong!");
        }
    }

    @Override
    public void sendClosePosition(String req_identifier, String clOrdId, double lot_size) {

        ManagedOrder order = this.ordersOpen.get(clOrdId);
        if (order == null) {
            logger.error("Cannot perform close position operation. Order not open.");
            int account_number = getAccountNumberFromOrderID(clOrdId);
            orderActionListenersMap
                    .getOrDefault(account_number, DO_NOTHING_OAL)
                    .onOrderNotAvailable(req_identifier, account_number, "Cannot perform close position operation. Order not open.");
            return;
        }

        Session session = Session.lookupSession(tradingSessionID);
        if (session == null) {
            logger.error("Session not found. Cannot send order.");

            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not close position - Something went wrong!");
            return;
        }

        if (!session.isLoggedOn()) {
            logger.error("Session is not logged on. Cannot send order.");
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not close position - Something went wrong!");
            return;
        }

        try {
            // Send an order in the opposite direction
            // of the open position to close the open postion
            NewOrderSingle newOrder = new NewOrderSingle(
                    new ClOrdID(order.markForCloseAndGetID(req_identifier)), // Unique order ID                
                    new Side(opposingSide(order.getSide())), // Opposite of the original position
                    new TransactTime(),
                    new OrdType(OrdType.MARKET) // Market order to close the position
            );

            newOrder.set(new OrderQty(lot_size * FX_LOT_QTY)); // Quantity to close
            newOrder.set(new Symbol(order.getSymbol()));
            Session.sendToTarget(newOrder, tradingSessionID);

        } catch (SessionNotFound | SQLException ex) {
            logger.error("Could not close position - " +ex.getMessage(), ex);
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not close position - Something went wrong!");

        }
    }

    @Override
    public void modifyOpenOrder(String req_identifier, String clOrdId, double target_price, double stoploss_price) {
        ManagedOrder order = this.ordersOpen.get(clOrdId);
        try {

            if (order == null) {
                logger.error("Cannot perform modify position operation. Order not open.");
                int account_number = getAccountNumberFromOrderID(clOrdId);
                orderActionListenersMap
                        .getOrDefault(account_number, DO_NOTHING_OAL)
                        .onOrderNotAvailable(req_identifier, account_number, "Cannot perform modify position operation. Order not open.");
                return;
            }

            Session session = Session.lookupSession(tradingSessionID);
            if (session == null) {
                logger.error("Could not modify order");
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Could not modify order - Something went wrong!");

                return;
            }

            if (!session.isLoggedOn()) {
                logger.error("Session is not logged on. Could not modify order.");
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Could not modify order - Something went wrong!");

                return;
            }

            order.modifyOrder(req_identifier, target_price, stoploss_price);
            setStopLoss(null, order);
            setTakeProfit(req_identifier, order);
        } catch (SQLException ex) {
            logger.error("Could not modify order - " +ex.getMessage(), ex);
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not modify order - Somethin went wrong!");

        }

    }

    /**
     * Pending Orders are stored locally are executed internally my client
     * application by automatically sending the market order to the LP. It does
     * not look possible to implement pending order management by storing it at
     * the LP side for Order Netting account type so we will store the pending
     * order locally and track price changes. As price reach the pending order
     * entry price we will send the order as market order and its stoploss and
     * target to the LP server
     *
     *
     * @param req_identifier
     * @param pend_order
     */
    @Override
    public void placePendingOrder(String req_identifier, ManagedOrder pend_order) {

        //We simply store the pending orders locally and
        //trigger it by sending it as market order when
        //the price it hit
        this.sentPendingOrders.put(pend_order.getOrderID(), pend_order);

        orderActionListenersMap
                .getOrDefault(pend_order.getAccountNumber(), DO_NOTHING_OAL)
                .onNewPendingOrder(req_identifier, pend_order);
    }

    @Override
    public void modifyPendingOrder(String req_identifier, String clOrdId, double open_price, double target_price, double stoploss_price) {

        //TODO - IMPLEMENT MODIFICATION OF OPEN PRICE TOO FOR PENDING 
        //ORDERS AS IT IS IN MT4 AND MT5
        //CURRENTLY ONLY TARGET AND STOPLOSS CAN BE MODIFIED
        ManagedOrder pend_order = this.ordersPending.get(clOrdId);

        try {
            if (pend_order == null) {
                logger.error("Cannot perform modify pending order operation. Order not pending.");
                int account_number = getAccountNumberFromOrderID(clOrdId);
                orderActionListenersMap
                        .getOrDefault(account_number, DO_NOTHING_OAL)
                        .onOrderNotAvailable(req_identifier, account_number, "Cannot perform modify pending order operation. Order not pending.");
                return;
            }

            //we only modify locally
            pend_order.modifyOrder(req_identifier, target_price, stoploss_price);

            orderActionListenersMap
                    .getOrDefault(pend_order.getAccountNumber(), DO_NOTHING_OAL)
                    .onModifiedPendingOrder(req_identifier, pend_order);
        } catch (SQLException ex) {

            logger.error("Could not modify pending order - " +ex.getMessage(), ex);
            orderActionListenersMap
                    .getOrDefault(pend_order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, pend_order, "Could not modify pending order - Something went wrong!");
        }

    }

    @Override
    public void deletePendingOrder(String req_identifier, String clOrdId) {

        ManagedOrder pend_order = ordersPending.get(clOrdId);
        if (pend_order == null) {
            logger.error("Cannot perform delete pending order operation. Order not pending.");
            int account_number = getAccountNumberFromOrderID(clOrdId);
            orderActionListenersMap
                    .getOrDefault(account_number, DO_NOTHING_OAL)
                    .onOrderNotAvailable(req_identifier, account_number, "Cannot perform delete pending order operation. Order not pending.");
            return;
        }

        try {

            //We simply delete the pending order locally
            this.ordersPending.remove(clOrdId);
            //now make the order identifiable at the client end 
            String clOrderID = pend_order.markForDeleteAndGetID(req_identifier);
            pend_order.setOrderID(clOrderID); //important

            req_identifier = pend_order.getDeleteOrderRequestIdentifier();

            orderActionListenersMap
                    .getOrDefault(pend_order.getAccountNumber(), DO_NOTHING_OAL)
                    .onDeletedPendingOrder(req_identifier, pend_order);
        } catch (SQLException ex) {
            
            logger.error("Could not delet pending order - "+ ex.getMessage(), ex);            
            orderActionListenersMap
                    .getOrDefault(pend_order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, pend_order, "Could not delet pending order - Something went wrong!");            
        }

    }

    @Override
    protected void onNewOrder(String clOrdID) {

        //check if it is market or pending order and add
        //and
        //check if it is stop loss or target of open order
        for (Map.Entry<String, ManagedOrder> entry : sentMarketOrders.entrySet()) {
            ManagedOrder order = entry.getValue();
            if (order.getOrderID().equals(clOrdID)) {
                //is market order so add
                ordersOpen.putIfAbsent(order.getOrderID(), sentMarketOrders.get(clOrdID));

                String req_identifier = order.getMarketOrderRequestIdentifier();

                //notify new open position    
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onNewMarketOrder(req_identifier, order);
            }

            if (order.getTargetOrderIDList().contains(clOrdID)) {
                //is target order so just ensure it is added
                ordersOpen.putIfAbsent(order.getOrderID(), sentMarketOrders.get(clOrdID));

                String req_identifier = order.getModifyOrderRequestIdentifier();
                //notify target modified
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onModifiedMarketOrder(req_identifier, order);

            }

            if (order.getStoplossOrderIDList().contains(clOrdID)) {
                //is stoploss order so just ensure it is added
                ordersOpen.putIfAbsent(order.getOrderID(), sentMarketOrders.get(clOrdID));

                String req_identifier = order.getModifyOrderRequestIdentifier();
                //notify stoploss modified                
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onModifiedMarketOrder(req_identifier, order);
            }

        }

    }

    @Override
    protected void onRejectedOrder(String clOrdID, String errMsg) {

        //check the type of order
        for (Map.Entry<String, ManagedOrder> entry : sentMarketOrders.entrySet()) {
            ManagedOrder order = entry.getValue();

            if (order.getOrderID().equals(clOrdID)) {
                //is market order
                sentMarketOrders.remove(clOrdID);

                String req_identifier = order.getMarketOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Market order rejected: " + errMsg);
            }

            if (order.getTargetOrderIDList().contains(clOrdID)) {
                //is target order so just remove
                order.removeTargetOrderID(clOrdID);
                sentMarketOrders.remove(clOrdID);

                String req_identifier = order.getModifyOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Target rejected: " + errMsg);
            }

            if (order.getStoplossOrderIDList().contains(clOrdID)) {
                //is stoploss order so just remove
                order.removeStoplossOrderID(clOrdID);
                sentMarketOrders.remove(clOrdID);

                String req_identifier = order.getModifyOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Stoploss rejected: " + errMsg);
            }

            if (order.getCloseOrderIDList().contains(clOrdID)) {
                //is close order so just remove
                order.removeCloseOrderID(clOrdID);
                sentMarketOrders.remove(clOrdID);

                String req_identifier = order.getCloseOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Close rejected: " + errMsg);
            }

        }

    }

    @Override
    protected void onCancelledOrder(String clOrdID) {

        for (Map.Entry<String, ManagedOrder> entry : sentMarketOrders.entrySet()) {
            ManagedOrder order = entry.getValue();
            //manage cancelled stoploss orders cancelled by user action like
            //modifying stoploss which triggers cancellation of previous stoploss order
            if (order.getStoplossOrderIDCancelProcessingList().contains(clOrdID)) {
                order.removeStoplossOrderIDCancelProcessingList(clOrdID);
                order.removeStoplossOrderIDList(clOrdID);

                String req_identifier = order.getModifyOrderRequestIdentifier();
                //notify stoploss modified
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onModifiedMarketOrder(req_identifier, order);
                logger.debug("cancel stoploss completed : ID " + clOrdID);

                break;
            }

            //manage cancelled target orders cancelled by user action like
            //modifying target which triggers cancellation of previous target order
            if (order.getTargetOrderIDCancelProcessingList().contains(clOrdID)) {
                order.removeTargetOrderIDCancelProcessingList(clOrdID);
                order.removeTargetOrderIDList(clOrdID);

                String req_identifier = order.getModifyOrderRequestIdentifier();
                //notify target modified
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onModifiedMarketOrder(req_identifier, order);
                logger.debug("cancel target completed : ID " + clOrdID);

                break;
            }
        }
    }

    @Override
    protected void onExecutedOrder(String clOrdID, double price) {
        onExecutedOpenOrder(clOrdID, price);
    }

    private void onExecutedOpenOrder(String clOrdID, double price) {
        //check if is market order, stoploss order, target or close order was hit
        //and cancel the other. if stoploss was hit then
        // cancel target and vice versa

        for (Map.Entry<String, ManagedOrder> entry : sentMarketOrders.entrySet()) {
            ManagedOrder order = entry.getValue();

            //check if is market order
            if (order.getOrderID().equals(clOrdID)) {
                order.setOpenPrice(price);
                order.setOpenTime(new Date());
                break;
            }

            if (order.getTargetOrderIDList().contains(clOrdID)) {
                //Since the LP may not automatically cancel the stoploss stop order                                
                //Cancel all stoploss orders related to this market orders

                //first set open price and time
                order.setClosePrice(price);
                order.setCloseTime(new Date());

                List<String> ST_orderIDs = order.getStoplossOrderIDList();
                for (String ST_orderID : ST_orderIDs) {
                    cancelOrder(ST_orderID,
                            order.getSymbol(),
                            order.getSide(),
                            order.getLotSize());

                    logger.debug("cancelling related stoploss order to Market order ID " + order.getOrderID());
                }

                // just in case, cancel the close orders if any happens to be in progress
                List<String> CL_orderIDs = order.getCloseOrderIDList();
                for (String CL_orderID : CL_orderIDs) {
                    cancelOrder(CL_orderID,
                            order.getSymbol(),
                            order.getSide(),
                            order.getLotSize());

                    logger.debug("cancelling just in case related close order to Market order ID " + order.getOrderID());

                }

                //notify target hit and position closed    
                OrderActionListener listener = orderActionListenersMap
                        .get(order.getAccountNumber());
                if (listener != null) {
                    String req_identifier = order.getCloseOrderRequestIdentifier();
                    listener.onClosedMarketOrder(req_identifier, order);
                    this.ordersOpen.remove(order.getOrderID());
                    OrderDB.removeOpenOrder(order.getOrderID());
                    OrderDB.insertHistoryOrder(order);
                }

                break;
            }

            if (order.getStoplossOrderIDList().contains(clOrdID)) {
                //Since the LP may not automatically cancel the target limit order
                //Cancel all target orders related to this market orders

                //first set open price and time
                order.setClosePrice(price);
                order.setCloseTime(new Date());

                List<String> TP_orderIDs = order.getTargetOrderIDList();
                for (String TP_orderID : TP_orderIDs) {
                    cancelOrder(TP_orderID,
                            order.getSymbol(),
                            order.getSide(),
                            order.getLotSize());

                    logger.debug("cancelling related target order to Market order ID " + order.getOrderID());

                }

                // just in case, cancel the close orders if any happens to be in progress
                List<String> CL_orderIDs = order.getCloseOrderIDList();
                for (String CL_orderID : CL_orderIDs) {
                    cancelOrder(CL_orderID,
                            order.getSymbol(),
                            order.getSide(),
                            order.getLotSize());

                    logger.debug("cancelling just in case related close order to Market order ID " + order.getOrderID());

                }

                //notify stoploss hit and position closed    
                OrderActionListener listener = orderActionListenersMap
                        .get(order.getAccountNumber());
                if (listener != null) {
                    String req_identifier = order.getCloseOrderRequestIdentifier();
                    listener.onClosedMarketOrder(req_identifier, order);
                    this.ordersOpen.remove(order.getOrderID());
                    OrderDB.removeOpenOrder(order.getOrderID());
                    OrderDB.insertHistoryOrder(order);
                }

                break;
            }

            if (order.getCloseOrderIDList().contains(clOrdID)) {
                //Since the LP may not automatically cancel the target limit order and stoploss stop order
                //Cancel all target  and stoploss  orders related to this market orders

                //first set open price and time
                order.setClosePrice(price);
                order.setCloseTime(new Date());

                //cancel the target orders
                List<String> TP_orderIDs = order.getTargetOrderIDList();
                for (String TP_orderID : TP_orderIDs) {
                    cancelOrder(TP_orderID,
                            order.getSymbol(),
                            order.getSide(),
                            order.getLotSize());

                    logger.debug("cancelling related target order to Market order ID " + order.getOrderID());

                }

                //cancel the stoploss orders
                List<String> ST_orderIDs = order.getStoplossOrderIDList();
                for (String ST_orderID : ST_orderIDs) {
                    cancelOrder(ST_orderID,
                            order.getSymbol(),
                            order.getSide(),
                            order.getLotSize());

                    logger.debug("cancelling related stoploss order to Market order ID " + order.getOrderID());

                }

                //notify position closed by user click close action
                OrderActionListener listener = orderActionListenersMap
                        .get(order.getAccountNumber());
                if (listener != null) {
                    String req_identifier = order.getCloseOrderRequestIdentifier();
                    listener.onClosedMarketOrder(req_identifier, order);
                    this.ordersOpen.remove(order.getOrderID());
                    OrderDB.removeOpenOrder(order.getOrderID());
                    OrderDB.insertHistoryOrder(order);
                }

                break;
            }

        }
    }

    @Override
    protected void checkLocalPendingOrderHit(SymbolInfo symbolInfo) {
        if (symbolInfo.getPrice() <= 0) {
            return;
        }
        for (Map.Entry<String, ManagedOrder> entry : this.ordersPending.entrySet()) {
            ManagedOrder order = entry.getValue();

            if (order.getSymbol().equals(symbolInfo.getName())) {
                if ((order.getSide() == ManagedOrder.Side.BUY_LIMIT
                        && symbolInfo.getPrice() <= order.getOpenPrice())
                        || (order.getSide() == ManagedOrder.Side.BUY_STOP
                        && symbolInfo.getPrice() >= order.getOpenPrice())
                        || (order.getSide() == ManagedOrder.Side.SELL_LIMIT
                        && symbolInfo.getPrice() >= order.getOpenPrice())
                        || (order.getSide() == ManagedOrder.Side.SELL_STOP
                        && symbolInfo.getPrice() <= order.getOpenPrice())) {

                    this.ordersPending.remove(order.getOrderID());
                    //order.convertToMarketOrder();//@Deprecated
                    String req_identifier = order.getMarketOrderRequestIdentifier();
                    ManagedOrder mktOrder;
                    try {
                        mktOrder = new ManagedOrder(req_identifier,
                                order.getAccountNumber(),
                                symbolInfo,
                                order.getSide(),
                                order.getTargetPrice(),
                                order.getStoplossPrice());

                        //String req_identifier = mktOrder.getMarketOrderRequestIdentifier();
                        this.sendMarketOrder(req_identifier, mktOrder);
                        //notify pending order triggered             
                        orderActionListenersMap
                                .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                                .onTriggeredPendingOrder(req_identifier, order);
                    } catch (OrderException ex) {
                        logger.error("Could not trigger pending order - "+ex.getMessage(), ex);
                        orderActionListenersMap
                                .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                                .onOrderRemoteError(req_identifier, order, "Could not trigger pending order");
                        continue;
                    }
                }

                break;
            }

        }

    }

    public static class Builder implements OrderNettingAccountBuilder {

        String settings_filename;

        public Builder() {
        }

        @Override
        public Builder accountConfig(String settings_filename) throws ConfigError {
            this.settings_filename = settings_filename;
            return this;
        }

        public Broker build() throws ConfigError {
            return new OrderNettingAccount(this.settings_filename);
        }
    }

}
