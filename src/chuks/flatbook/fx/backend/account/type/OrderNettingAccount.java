/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.type;

import chuks.flatbook.fx.backend.account.Broker;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import java.util.*;
import quickfix.*;
import chuks.flatbook.fx.backend.account.contract.OrderNettingAccountBuilder;
import chuks.flatbook.fx.backend.task.netting.NettingCloseTask;
import chuks.flatbook.fx.backend.task.netting.NettingMarketOrderTask;
import chuks.flatbook.fx.backend.task.netting.NettingModifyOrderTask;
import chuks.flatbook.fx.backend.task.Task;
import chuks.flatbook.fx.common.account.order.SymbolInfo;
import chuks.flatbook.fx.common.account.persist.OrderDB;
import chuks.flatbook.fx.common.account.order.OrderException;
import java.sql.SQLException;
import org.slf4j.LoggerFactory;
import static chuks.flatbook.fx.common.account.order.OrderIDUtil.getAccountNumber;
import chuks.flatbook.fx.common.account.order.Position;
import chuks.flatbook.fx.common.account.order.UnfilledOrder;

/**
 *
 * @author user
 */
public class OrderNettingAccount extends Broker {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrderNettingAccount.class.getName());

    
    public OrderNettingAccount(String settings_filename) throws ConfigError {
        super(settings_filename);
    }

    public void sendResultToTrader(boolean success, String result){
        
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

        var marketOrderTask = new NettingMarketOrderTask(this, req_identifier, order);        
        taskManager.addTask(marketOrderTask);
    }

    @Override
    public void sendClosePosition(String req_identifier, String clOrdId, double lot_size, double price, int slippage) {

        ManagedOrder order = this.ordersOpen.get(clOrdId);
        if (order == null) {
            var errStr = "Cannot perform close position operation."
                    + " Order not open.";
            logger.error(errStr);
            int account_number = getAccountNumber(clOrdId);
            orderActionListenersMap
                    .getOrDefault(account_number, DO_NOTHING_OAL)
                    .onOrderNotAvailable(req_identifier, account_number, errStr);

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

        var closePositionTask = new NettingCloseTask(this, req_identifier, order, lot_size, price, slippage);

        taskManager.addTask(closePositionTask);

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

        var modifyOrderTask = new NettingModifyOrderTask(this, req_identifier, order, stoploss_price, target_price);
     
        taskManager.addTask(modifyOrderTask);

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
    public void onNewOrder(String clOrdID) {
        Task task = taskManager.getCurrennTask();        
        if (task != null) {
            task.onNewOrder(clOrdID);
        }
        //check if it is market or pending order and add
        //and
        //check if it is stop loss or target of open order
        for (Map.Entry<String, ManagedOrder> entry : sentMarketOrders.entrySet()) {
            ManagedOrder order = entry.getValue();
            if (clOrdID.equals(order.getOrderID())) {
                //is market order so add
                ordersOpen.putIfAbsent(order.getOrderID(), sentMarketOrders.get(clOrdID));

                String req_identifier = order.getMarketOrderRequestIdentifier();

                //notify new open position    
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onNewMarketOrder(req_identifier, order);
            }

            if (clOrdID.equals(order.getTakeProfitOrderID())) {

                String req_identifier = order.getModifyOrderRequestIdentifier();
                //notify target modified
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onModifiedMarketOrder(req_identifier, order);

            }

            if (clOrdID.equals(order.getStoplossOrderID())) {

                String req_identifier = order.getModifyOrderRequestIdentifier();
                //notify stoploss modified                
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onModifiedMarketOrder(req_identifier, order);
            }

        }

    }

    @Override
    public void onRejectedOrder(String clOrdID, String errMsg) {
        Task task = taskManager.getCurrennTask();        
        if (task != null) {
            task.onRejectedOrder(clOrdID, errMsg);
        }
        //check the type of order
        for (Map.Entry<String, ManagedOrder> entry : sentMarketOrders.entrySet()) {
            ManagedOrder order = entry.getValue();

            if (clOrdID.equals(order.getOrderID())) {
                //is market order
                sentMarketOrders.remove(clOrdID);

                String req_identifier = order.getMarketOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Market order rejected: " + errMsg);

                logger.error("Market order rejected: " + errMsg);
            }

            if (clOrdID.equals(order.getTakeProfitOrderID())) {
                //is target order so undo it
                order.undoLastTakeProfitModify();

                String req_identifier = order.getModifyOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Target rejected: " + errMsg);

                logger.error("Target rejected: " + errMsg);
            }

            if (clOrdID.equals(order.getStoplossOrderID())) {
                //is stoploss order so just undo it
                order.undoLastStoplossModify();

                String req_identifier = order.getModifyOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Stoploss rejected: " + errMsg);

                logger.error("Stoploss rejected: " + errMsg);
            }

            if (clOrdID.equals(order.getCloseOrderID())) {
                //is close order so just remove
                order.removeCloseOrderID(clOrdID);

                String req_identifier = order.getCloseOrderRequestIdentifier();
                orderActionListenersMap
                        .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                        .onOrderRemoteError(req_identifier, order, "Close rejected: " + errMsg);

                logger.error("Close rejected: " + errMsg);
            }

        }

    }

    @Override
    public void onCancelledOrder(String clOrdID) {
        Task task = taskManager.getCurrennTask();        
        if (task != null) {
            task.onCancelledOrder(clOrdID);
        }
        for (Map.Entry<String, ManagedOrder> entry : sentMarketOrders.entrySet()) {
            ManagedOrder order = entry.getValue();

            if (clOrdID.equals(order.getStoplossOrderID())) {
                order.cancelStoplossOrder(clOrdID);
                break;
            }

            if (clOrdID.equals(order.getTakeProfitOrderID())) {
                order.cancelTakeProfitOrder(clOrdID);
                break;
            }
        }

    }

    @Override
    public void onOrderCancelRequestRejected(String clOrdID, String reason) {
        Task task = taskManager.getCurrennTask();
        if (task != null) {
            task.onOrderCancelRequestRejected(clOrdID, reason);
        }
    }

    @Override
    public void onExecutedOrder(String clOrdID, double price) {
        Task task = taskManager.getCurrennTask();
        if (task != null) {
            task.onExecutedOrder(clOrdID, price);
        }
        onExecutedOpenOrder(clOrdID, price);
    }

    @Override
    public void onPositionReport(List<Position> positionlist, String error) {
        Task task = taskManager.getCurrennTask();
        if (task != null) {
            task.onPositionReport(positionlist, error);
        }
    }

    @Override
    public void onOrderReport(UnfilledOrder unfilledOrder, int totalOrders) {
        Task task = taskManager.getCurrennTask();
        if (task != null) {
            task.onOrderReport(unfilledOrder, totalOrders);
        }
    }
    
    

    private void onExecutedOpenOrder(String clOrdID, double price) {
        
        //check if is market order, stoploss order, target or close order was hit
        //and cancel the other. if stoploss was hit then
        //cancel target and vice versa
        
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

                if (clOrdID.equals(order.getTakeProfitOrderID())) {
                    //Since the LP may not automatically cancel the stoploss stop order                                
                    //Cancel all stoploss orders related to this market orders

                    //first set open price and time
                    order.setClosePrice(price);
                    order.setCloseTime(new Date());

                    String ST_orderID = order.getStoplossOrderID();

                    if (ST_orderID != null) {
                        cancelOrder(ST_orderID,
                                order.getSymbol(),
                                opposingSide(order.getSide()),
                                order.getLotSize());

                        logger.debug("cancelling related stoploss order to Market order ID " + order.getOrderID());
                    }

                    //notify target hit and position closed    
                    this.ordersOpen.remove(order.getOrderID());
                    this.ordersHistory.put(order.getOrderID(), order);
                    OrderDB.insertHistoryOrder(order);

                    String req_identifier = order.getCloseOrderRequestIdentifier();
                    orderActionListenersMap
                            .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                            .onClosedMarketOrder(req_identifier, order);

                    break;
                }

                if (clOrdID.contains(order.getStoplossOrderID())) {
                    //Since the LP may not automatically cancel the target limit order
                    //Cancel all target orders related to this market orders

                    //first set open price and time
                    order.setClosePrice(price);
                    order.setCloseTime(new Date());

                    String TP_orderID = order.getTakeProfitOrderID();
                    if (TP_orderID != null) {
                        cancelOrder(TP_orderID,
                                order.getSymbol(),
                                opposingSide(order.getSide()),
                                order.getLotSize());

                        logger.debug("cancelling related target order to Market order ID " + order.getOrderID());
                    }

                    //notify stoploss hit and position closed    
                    this.ordersOpen.remove(order.getOrderID());
                    this.ordersHistory.put(order.getOrderID(), order);
                    OrderDB.insertHistoryOrder(order);

                    String req_identifier = order.getCloseOrderRequestIdentifier();
                    orderActionListenersMap
                            .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                            .onClosedMarketOrder(req_identifier, order);

                    break;
                }

                if (clOrdID.contains(order.getCloseOrderID())) {

                    //first set close price and time
                    order.setClosePrice(price);
                    order.setCloseTime(new Date());

                    //COME BACK FOR BELOW
                    //notify position closed
                    this.ordersOpen.remove(order.getOrderID());
                    this.ordersHistory.put(order.getOrderID(), order);
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
                                order.getTakeProfitPrice(),
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
