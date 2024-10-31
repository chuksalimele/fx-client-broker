/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.type;

import chuks.flatbook.fx.backend.account.Broker;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import java.util.*;
import quickfix.*;
import quickfix.field.*;
import chuks.flatbook.fx.backend.account.contract.OrderNettingAccountBuilder;
import static chuks.flatbook.fx.common.account.order.ManagedOrder.FX_LOT_QTY;
import chuks.flatbook.fx.common.account.order.SymbolInfo;
import chuks.flatbook.fx.common.account.persist.OrderDB;
import chuks.flatbook.fx.backend.listener.OrderActionListener;
import chuks.flatbook.fx.common.account.order.MarketOrderIDFamily;
import chuks.flatbook.fx.common.account.order.OrderException;
import chuks.flatbook.fx.common.account.order.OrderIDUtil;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;
import quickfix.fix44.NewOrderSingle;
import static chuks.flatbook.fx.common.account.order.OrderIDUtil.getAccountNumber;

/**
 *
 * @author user
 */
public class OrderNettingAccount extends Broker {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrderNettingAccount.class.getName());

    protected List<ManagedOrder> deferredTakeProfitOrderRequestList = Collections.synchronizedList(new LinkedList());
    protected List<ManagedOrder> deferredStoplossOrderRequestList = Collections.synchronizedList(new LinkedList());
    protected Map<String, ManagedOrder> ordersClosingInProgress = Collections.synchronizedMap(new LinkedHashMap());

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
            //making sure no hanging stoploss order, so cancell all previous ones
            if (!ST_orderIDs.isEmpty()) {
                for (String ST_orderID : ST_orderIDs) {
                    cancelOrder(ST_orderID,
                            order.getSymbol(),
                            order.getSide(),
                            order.getLotSize());

                    logger.debug("cancelling stoploss with ID " + ST_orderID);
                }

                deferredStoplossOrderRequestList.add(order);
                return; //leave because we will not set stoploss until any previous one is confirmed cancelled
            }

            order.modifyStoploss(req_identifier, order.getStoplossPrice());

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

        } catch (SQLException | SessionNotFound ex) {
            order.undoLastStoplossModify();
            logger.error("Cannot send modify stoploss order - " + ex.getMessage(), ex);
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not set stoploss - Something went wrong!");
        }
    }

    private void setTakeProfit(String req_identifier, ManagedOrder order) {
        try {

            //first cancel any previous target orders attached to the market order
            List<String> TP_orderIDs = order.getTakeProfitOrderIDList();
            //making sure no hanging target order, so cancell all previous ones
            if (!TP_orderIDs.isEmpty()) {
                for (String TP_orderID : TP_orderIDs) {
                    cancelOrder(TP_orderID,
                            order.getSymbol(),
                            order.getSide(),
                            order.getLotSize());

                    logger.debug("cancelling target with ID " + TP_orderID);
                }

                deferredTakeProfitOrderRequestList.add(order);
                return; //leave because we will not set targe until any previous one is confirmed cancelled
            }

            order.modifyStoploss(req_identifier, order.getTargetPrice());

            if (order.getTargetPrice() == 0) {
                //before this point will have already cancelled previous
                //target so now leave. No need to proceed. 
                //Job already done!    

                logger.debug("target is zero");

                return;
            }

            quickfix.fix44.NewOrderSingle targetOrder = new quickfix.fix44.NewOrderSingle(
                    new ClOrdID(order.getLastTakeProfitOrderID()),
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

        } catch (SQLException | SessionNotFound ex) {
            order.undoLastTakeProfitModify();
            logger.error("Could not set target", ex);
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not set target - Something went wrong!");
        }
    }

    void changeToOrderClosingState(String ordID) {
        //remove it
        ManagedOrder order = ordersOpen.remove(ordID);

        //and then transfer to closing-inprogress state        
        if (order != null) {
            ordersClosingInProgress.put(order.getOrderID(), order);
        }

    }

    void rollbackOrderClosingState(String ordID) {
        //remove it
        ManagedOrder order = ordersClosingInProgress.remove(ordID);

        //and then return back to open orders
        if (order != null) {
            ordersOpen.put(order.getOrderID(), order);
        }

    }

    @Override
    public void sendClosePosition(String req_identifier, String clOrdId, double lot_size) {

        ManagedOrder order = this.ordersOpen.get(clOrdId);
        if (order == null) {
            if (ordersClosingInProgress.containsKey(clOrdId)) {
                var errStr = "Cannot perform close position operation."
                        + " Order is inclosing state.";
                logger.error(errStr);
                int account_number = getAccountNumber(clOrdId);
                orderActionListenersMap
                        .getOrDefault(account_number, DO_NOTHING_OAL)
                        .onOrderNotAvailable(req_identifier, account_number, errStr);
            } else {
                var errStr = "Cannot perform close position operation."
                        + " Order not open.";
                logger.error(errStr);
                int account_number = getAccountNumber(clOrdId);
                orderActionListenersMap
                        .getOrDefault(account_number, DO_NOTHING_OAL)
                        .onOrderNotAvailable(req_identifier, account_number, errStr);
            }
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
            //first cancel target and stoploss orders . this is LIMIT and STOP orders representing target and stoploss pending orders at the LP

            //But first move this opend order to close-inprogress list - we shall use it to verify that the whole close process was completed
            changeToOrderClosingState(clOrdId);

            //cancelling target order
            List<String> TP_orderIDs = order.getTakeProfitOrderIDList();
            for (String TP_orderID : TP_orderIDs) {
                cancelOrder(TP_orderID,
                        order.getSymbol(),
                        order.getSide(),
                        order.getLotSize());

                logger.debug("cancelling target with ID " + TP_orderID);
            }

            //cancelling stoploss order
            List<String> ST_orderIDs = order.getStoplossOrderIDList();
            for (String ST_orderID : ST_orderIDs) {
                cancelOrder(ST_orderID,
                        order.getSymbol(),
                        order.getSide(),
                        order.getLotSize());

                logger.debug("cancelling stoploss with ID " + ST_orderID);
            }

            //Now we can send the close order
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

            rollbackOrderClosingState(clOrdId);

            logger.error("Could not close position - " + ex.getMessage(), ex);
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(req_identifier, order, "Could not close position - Something went wrong!");

        }
    }

    @Override
    public void modifyOpenOrder(String req_identifier, String clOrdId, double target_price, double stoploss_price) {
        ManagedOrder order = this.ordersOpen.get(clOrdId);
        if (order == null) {
            logger.error("Cannot perform modify position operation. Order not open.");
            int account_number = getAccountNumber(clOrdId);
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
        setStopLoss(req_identifier, order);
        setTakeProfit(req_identifier, order);
        //if both target and stoploss is zero then no need to wait
        //for FIX sever response, just send the feed back to the client
        if (order.getStoplossPrice() == 0
                && order.getTargetPrice() == 0) {
            //notify target modified
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onModifiedMarketOrder(req_identifier, order);

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
                int account_number = getAccountNumber(clOrdId);
                orderActionListenersMap
                        .getOrDefault(account_number, DO_NOTHING_OAL)
                        .onOrderNotAvailable(req_identifier, account_number, "Cannot perform modify pending order operation. Order not pending.");
                return;
            }

            //we only modify locally
            pend_order.modifyStoploss(req_identifier, stoploss_price);
            pend_order.modifyTakeProfit(req_identifier, target_price);

            orderActionListenersMap
                    .getOrDefault(pend_order.getAccountNumber(), DO_NOTHING_OAL)
                    .onModifiedPendingOrder(req_identifier, pend_order);
        } catch (SQLException ex) {

            logger.error("Could not modify pending order - " + ex.getMessage(), ex);
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
            int account_number = getAccountNumber(clOrdId);
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

            logger.error("Could not delet pending order - " + ex.getMessage(), ex);
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

            if (order.getTakeProfitOrderIDList().contains(clOrdID)) {

                String req_identifier = order.getModifyOrderRequestIdentifier();
                //notify target modified
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onModifiedMarketOrder(req_identifier, order);

            }

            if (order.getStoplossOrderIDList().contains(clOrdID)) {

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

                logger.error("Market order rejected: " + errMsg);
            }

            if (order.getTakeProfitOrderIDList().contains(clOrdID)) {
                //is target order so just remove
                order.removeTakeProfitOrderID(clOrdID);
                sentMarketOrders.remove(clOrdID);

                String req_identifier = order.getModifyOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Target rejected: " + errMsg);

                logger.error("Target rejected: " + errMsg);
            }

            if (order.getStoplossOrderIDList().contains(clOrdID)) {
                //is stoploss order so just remove
                order.removeStoplossOrderID(clOrdID);
                sentMarketOrders.remove(clOrdID);

                String req_identifier = order.getModifyOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Stoploss rejected: " + errMsg);

                logger.error("Stoploss rejected: " + errMsg);
            }

            if (order.getCloseOrderIDList().contains(clOrdID)) {
                //is close order so just remove
                order.removeCloseOrderID(clOrdID);
                sentMarketOrders.remove(clOrdID);

                String req_identifier = order.getCloseOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Close rejected: " + errMsg);

                logger.error("Close rejected: " + errMsg);
            }

        }

    }

    /**
     * Called when Limit and Stop pending orders are cancelled for the purpose
     * of modifying target and stoploss which will trigger the sending of the
     * deferred target or stoploss request
     *
     * @param clOrdID
     */
    @Override
    protected void onCancelledOrder(String clOrdID) {

        for (Map.Entry<String, ManagedOrder> entry : sentMarketOrders.entrySet()) {
            ManagedOrder order = entry.getValue();
            //manage cancelled stoploss orders cancelled by user action like
            //modifying stoploss which triggers cancellation of previous stoploss order
            if (clOrdID.contains(order.getLastStoplossOrderID())) {
                order.cancelStoplossOrderID(clOrdID);
                break;
            }

            //manage cancelled target orders cancelled by user action like
            //modifying target which triggers cancellation of previous target order
            if (clOrdID.contains(order.getLastTakeProfitOrderID())) {
                order.cancelTakeProfitOrderID(clOrdID);
                break;
            }
        }

        //next handle defferred stoploss/target modification requests
        for (int i = 0; i < deferredStoplossOrderRequestList.size(); i++) {
            ManagedOrder order = deferredStoplossOrderRequestList.get(i);
            if (order.getStoplossOrderIDList().isEmpty()) {
                //check if it is the cancel stoploss id 
                if (order.getCancelledStoplossOrderIDList().contains(clOrdID)) {
                    String identifier = order.getMarketOrderRequestIdentifier();
                    setStopLoss(identifier, order);
                    deferredStoplossOrderRequestList.remove(order);
                    break;
                }
            }
        }

        for (int i = 0; i < deferredTakeProfitOrderRequestList.size(); i++) {
            ManagedOrder order = deferredTakeProfitOrderRequestList.get(i);
            if (order.getTakeProfitOrderIDList().isEmpty()) {
                //check if it is the cancel take profit id 
                if (order.getCancelledTakeProfitOrderIDList().contains(clOrdID)) {
                    String identifier = order.getMarketOrderRequestIdentifier();
                    setTakeProfit(identifier, order);
                    deferredTakeProfitOrderRequestList.remove(order);
                    break;
                }
            }
        }

    }

    @Override
    protected void onOrderCancelRequestRejected(String clOrdID, String reason) {

        for (int i = 0; i < deferredStoplossOrderRequestList.size(); i++) {
            ManagedOrder order = deferredStoplossOrderRequestList.get(i);
            if (order.getStoplossOrderIDList().contains(clOrdID)) {
                String identifier = order.getMarketOrderRequestIdentifier();
                //Just delete the request
                deferredStoplossOrderRequestList.remove(order);

                //And report failed request to the client
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(identifier, order, "Could not modify stoploss - " + reason);

                logger.error("Could not modify stoploss - " + reason);
                break;
            }
        }

        for (int i = 0; i < deferredTakeProfitOrderRequestList.size(); i++) {
            ManagedOrder order = deferredTakeProfitOrderRequestList.get(i);
            if (order.getTakeProfitOrderIDList().contains(clOrdID)) {
                String identifier = order.getMarketOrderRequestIdentifier();
                //Just delete the request                
                deferredTakeProfitOrderRequestList.remove(order);

                //And report failed request to the client
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(identifier, order, "Could not modify take profi - " + reason);

                logger.error("Could not modify take profi - " + reason);
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
        ManagedOrder order = null;
        try {
            for (Map.Entry<String, ManagedOrder> entry : sentMarketOrders.entrySet()) {
                order = entry.getValue();

                //check if is market order
                if (order.getOrderID().equals(clOrdID)) {
                    order.setOpenPrice(price);
                    order.setOpenTime(new Date());
                    break;
                }

                if (order.getTakeProfitOrderIDList().contains(clOrdID)) {
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

                    //notify target hit and position closed    
                    this.ordersOpen.remove(order.getOrderID());
                    OrderDB.insertHistoryOrder(order);

                    String req_identifier = order.getCloseOrderRequestIdentifier();
                    orderActionListenersMap
                            .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                            .onClosedMarketOrder(req_identifier, order);

                    break;
                }

                if (order.getStoplossOrderIDList().contains(clOrdID)) {
                    //Since the LP may not automatically cancel the target limit order
                    //Cancel all target orders related to this market orders

                    //first set open price and time
                    order.setClosePrice(price);
                    order.setCloseTime(new Date());

                    List<String> TP_orderIDs = order.getTakeProfitOrderIDList();
                    for (String TP_orderID : TP_orderIDs) {
                        cancelOrder(TP_orderID,
                                order.getSymbol(),
                                order.getSide(),
                                order.getLotSize());

                        logger.debug("cancelling related target order to Market order ID " + order.getOrderID());

                    }

                    //notify stoploss hit and position closed    
                    this.ordersOpen.remove(order.getOrderID());
                    OrderDB.insertHistoryOrder(order);

                    String req_identifier = order.getCloseOrderRequestIdentifier();
                    orderActionListenersMap
                            .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                            .onClosedMarketOrder(req_identifier, order);

                    break;
                }

                if (order.getCloseOrderIDList().contains(clOrdID)) {

                    //first set close price and time
                    order.setClosePrice(price);
                    order.setCloseTime(new Date());

                    //notify position closed
                    this.ordersClosingInProgress.remove(order.getOrderID());
                    OrderDB.insertHistoryOrder(order);

                    String req_identifier = order.getCloseOrderRequestIdentifier();
                    orderActionListenersMap
                            .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                            .onClosedMarketOrder(req_identifier, order);

                    break;
                }

            }
        } catch (SessionNotFound ex) {
            logger.error(ex.getMessage(), ex);
            if (order != null) {
                String req_identifier = order.getCloseOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Session not found at remote end after execution report");
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
                        logger.error("Could not trigger pending order - " + ex.getMessage(), ex);
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
