/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbook.fx.backend.account;

import chuks.flatbook.fx.common.account.order.SymbolInfo;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import chuks.flatbook.fx.backend.config.Config;
import static chuks.flatbook.fx.common.account.order.ManagedOrder.FX_LOT_QTY;
import chuks.flatbook.fx.common.account.persist.OrderDB;
import chuks.flatbook.fx.backend.custom.message.AccountInfoRequest;
import chuks.flatbook.fx.backend.custom.message.AccountInfoResponse;
import chuks.flatbook.fx.backend.listener.ConnectionListener;
import chuks.flatbook.fx.backend.listener.OrderActionListener;
import chuks.flatbook.fx.backend.listener.SymbolUpdateListener;
import java.util.*;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.*;
import chuks.flatbook.fx.backend.account.contract.BrokerAccount;
import chuks.flatbook.fx.common.account.profile.TraderAccountProfile;
import chuks.flatbook.fx.backend.account.persist.TraderDB;
import chuks.flatbook.fx.backend.listener.ConnectionAdapter;
import chuks.flatbook.fx.backend.listener.OrderActionAdapter;
import chuks.flatbook.fx.backend.listener.SymbolUpdateAdapter;
import chuks.flatbook.fx.backend.listener.TraderAccountAdapter;
import io.netty.channel.ChannelHandlerContext;
import java.sql.SQLException;
import java.util.Map.Entry;
import org.slf4j.LoggerFactory;
import chuks.flatbook.fx.backend.listener.AccountListener;
import chuks.flatbook.fx.common.account.order.OrderIDUtil;
import chuks.flatbook.fx.common.account.order.Position;
import chuks.flatbook.fx.common.account.order.UnfilledOrder;
import chuks.flatbook.fx.common.account.profile.AdminProfile;
import chuks.flatbook.fx.common.account.profile.BasicAccountProfile;
import chuks.flatbook.fx.common.account.profile.UserType;
import chuks.flatbook.fx.backend.listener.BrokerFixOrderListener;

/**
 *
 * @author user
 */
public abstract class Broker extends quickfix.MessageCracker implements quickfix.Application, BrokerAccount, BrokerFixOrderListener {

    protected final SymbolUpdateListener DO_NOTHING_SIL = new SymbolUpdateAdapter() {
    };
    protected final OrderActionListener DO_NOTHING_OAL = new OrderActionAdapter() {
    };
    protected final ConnectionListener DO_NOTHING_CL = new ConnectionAdapter() {
    };
    protected final AccountListener DO_NOTHING_TA = new TraderAccountAdapter() {
    };

    private final int FULL_REFRESH = 1;
    private final int INCREMENTAL_REFRESH = 2;
    protected Session tradingSession;
    protected Session quoteSession;
    protected SessionID tradingSessionID;
    protected SessionID quoteSessionID;
    protected static SessionSettings settings;
    protected String targetOrderID;
    protected String stopOrderID;
    private SocketInitiator initiator;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Broker.class.getName());

    final private Map<Integer, TraderAccountProfile> UsersMap = Collections.synchronizedMap(new LinkedHashMap());
    final private Map<Integer, AdminProfile> AdminsMap = Collections.synchronizedMap(new LinkedHashMap());

    protected Map<String, SymbolInfo> fullSymbolInfoMap = Collections.synchronizedMap(new LinkedHashMap());

    protected List<ManagedOrder> temOpenOrderList = Collections.synchronizedList(new LinkedList()); //temporary opend orders store when the application is closed

    protected Map<String, ManagedOrder> ordersHistory = Collections.synchronizedMap(new LinkedHashMap()); //the market order or pending order id is the key
    protected Map<String, ManagedOrder> ordersOpen = Collections.synchronizedMap(new LinkedHashMap()); //the market order id is the key
    protected Map<String, ManagedOrder> ordersPending = Collections.synchronizedMap(new LinkedHashMap()); //the market order id is the key
    protected Map<String, ManagedOrder> sentMarketOrders = Collections.synchronizedMap(new LinkedHashMap());
    protected Map<String, ManagedOrder> sentPendingOrders = Collections.synchronizedMap(new LinkedHashMap());

    protected Map<Integer, OrderActionListener> orderActionListenersMap = Collections.synchronizedMap(new HashMap());
    protected Map<Integer, SymbolUpdateListener> symbolUpdateListenersMap = Collections.synchronizedMap(new HashMap());
    protected Map<Integer, ConnectionListener> connectionListenersMap = Collections.synchronizedMap(new HashMap());
    protected Map<Integer, AccountListener> accountListenersMap = Collections.synchronizedMap(new HashMap());

    protected List<Position> positionAtLPList = Collections.synchronizedList(new LinkedList());
    protected List<UnfilledOrder> unfilledOrderAtLPList = Collections.synchronizedList(new LinkedList());

    {
        String[] supported_symbols = {
            "EURUSD", "USDJPY", "GBPUSD", "USDCHF", "AUDUSD", "USDCAD", "NZDUSD", "EURJPY",
            "GBPJPY", "CHFJPY", "AUDJPY", "EURGBP", "EURAUD", "EURCAD", "EURNZD", "GBPAUD",
            "GBPCAD", "GBPNZD", "AUDCAD", "AUDNZD", "NZDCAD", "NZDCHF", "CADCHF", "AUDCHF",
            "EURCHF", "EURNOK", "USDNOK", "USDSGD", "USDHKD", "USDMXN", "USDTRY", "USDZAR",
            "USDRUB", "USDINR", "USDKRW", "USDCNH", "USDPLN", "USDTHB", "USDCZK", "USDHUF"
        };

        // Populate the map with keys from the symbols array and null values
        for (String symbol : supported_symbols) {
            fullSymbolInfoMap.put(symbol, null);
        }
    }


    protected Broker(String settings_filename) throws ConfigError {
        initAndRun(settings_filename);
    }

    private void initAndRun(String settings_filename) throws ConfigError {

        settings = new SessionSettings(settings_filename);
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        quickfix.MessageFactory messageFactory = new DefaultMessageFactory();
        initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
        initiator.start();
    }

    public static SessionSettings getSettings() {
        return settings;
    }
    
    public SessionID getTradingSessionID() {
        return tradingSessionID;
    }

    public SessionID getQuoteSessionID() {
        return quoteSessionID;
    }

    public Session getTradingSession() {
        return tradingSession;
    }

    public Session getQuoteSession() {
        return quoteSession;
    } 
    
    public OrderActionListener getOrderActionListener(int account_number){
        return orderActionListenersMap.getOrDefault(account_number, DO_NOTHING_OAL);
    }
    
    public void storeSentMarketOrder(ManagedOrder order) {
        this.sentMarketOrders.put(order.getOrderID(), order);
    }

    @Override
    public boolean registerTrader(TraderAccountProfile account_profile) {
        int account_number = account_profile.getAccountNumber();
        try {
            TraderDB.insertTraderRegistration(account_profile.getEmail(),
                    account_profile.getAccountName(),
                    account_profile.getPassword(),
                    account_profile.getRegistrationTime());

            accountListenersMap
                    .getOrDefault(account_number, DO_NOTHING_TA)
                    .onAccountOpen(account_number);
            return true;
        } catch (SQLException ex) {
            logger.error("Could not signup", ex);
        }
        return false;
    }

    BasicAccountProfile byUserType(int account_number, UserType user_type) {
        if (user_type == null) {
            logger.warn("UNKNOWN USER TYPE - THIS SHOULD NOT HAPPEN AT ALL");
            return null;
        }

        switch (user_type) {
            case UserType.TRADER -> {
                return UsersMap.get(account_number);
            }
            case UserType.ADMIN -> {
                return AdminsMap.get(account_number);
            }
            default -> {
                logger.warn("UNKNOWN USER TYPE - THIS SHOULD NOT HAPPEN");
                return null;
            }

        }
    }

    @Override
    public boolean login(int account_number, byte[] hash_password, int user_type) {
        BasicAccountProfile user = byUserType(account_number, UserType.fromValue(user_type));
        if (user != null) {
            if (Arrays.equals(user.getPassword(), hash_password)) {
                user.setIsLoggedIn(true);
                accountListenersMap
                        .getOrDefault(account_number, DO_NOTHING_TA)
                        .onLogIn(account_number);
                this.refreshContent(account_number);
                return true;
            }
            user.setIsLoggedIn(true);
            accountListenersMap
                    .getOrDefault(account_number, DO_NOTHING_TA)
                    .onLogInFail(account_number, "Incorrect password");

        }

        return false;
    }

    @Override
    public boolean logout(int account_number, int user_type) {
        BasicAccountProfile user = byUserType(account_number, UserType.fromValue(user_type));

        if (user != null) {
            user.setIsLoggedIn(false);
            accountListenersMap
                    .getOrDefault(account_number, DO_NOTHING_TA)
                    .onLogOut(account_number);
            return true;
        }

        return false;
    }

    @Override
    public void refreshContent(int account_number) {

        symbolUpdateListenersMap
                .getOrDefault(account_number, DO_NOTHING_SIL)
                .onFullSymbolList(account_number, this.fullSymbolInfoMap.keySet());

        orderActionListenersMap
                .getOrDefault(account_number, DO_NOTHING_OAL)
                .onAddAllOpenOrders(null, account_number, new LinkedList(this.ordersOpen.values()));

        orderActionListenersMap
                .getOrDefault(account_number, DO_NOTHING_OAL)
                .onAddAllHistoryOrders(account_number, new LinkedList(this.ordersOpen.values()), null);

        orderActionListenersMap
                .getOrDefault(account_number, DO_NOTHING_OAL)
                .onAddAllPendingOrders(null, account_number, new LinkedList(this.ordersOpen.values()));

    }

    @Override
    public Set<String> getAllSymbols() {
        return this.fullSymbolInfoMap.keySet();
    }

    @Override
    public List<SymbolInfo> getSymbolInfoList(String[] symbols) {
        List<SymbolInfo> symbInfoList = new LinkedList();
        for (Entry<String, SymbolInfo> sybInfo : fullSymbolInfoMap.entrySet()) {
            for (String symbol : symbols) {
                if (symbol.equals(sybInfo.getKey())) {
                    symbInfoList.add(sybInfo.getValue());
                    break;
                }
            }
        }
        return symbInfoList;
    }

    public void saveHistoryOrder() {
        OrderDB.replaceHistoryOrderList(new LinkedList(this.ordersHistory.values()));
    }

    @Override
    public void addListeners(Client client) {
        orderActionListenersMap.put(client.getAccountNumber(), client);
        symbolUpdateListenersMap.put(client.getAccountNumber(), client);
        connectionListenersMap.put(client.getAccountNumber(), client);
        accountListenersMap.put(client.getAccountNumber(), client);
    }

    @Override
    public void clearListeners(Client client) {
        orderActionListenersMap.remove(client.getAccountNumber());
        symbolUpdateListenersMap.remove(client.getAccountNumber());
        connectionListenersMap.remove(client.getAccountNumber());
    }

    @Override
    public void clearListeners(ChannelHandlerContext ctx) {

        Client client = null;

        for (Entry<Integer, OrderActionListener> entry : orderActionListenersMap.entrySet()) {
            client = (Client) entry.getValue();
            if (client.getContext().equals(ctx)) {
                orderActionListenersMap.remove(client.getAccountNumber());
                break;
            }
        }

        if (client == null) {
            for (Entry<Integer, SymbolUpdateListener> entry : symbolUpdateListenersMap.entrySet()) {
                client = (Client) entry.getValue();
                if (client.getContext().equals(ctx)) {
                    symbolUpdateListenersMap.remove(client.getAccountNumber());
                    break;
                }
            }
        } else {
            symbolUpdateListenersMap.remove(client.getAccountNumber());
        }

        if (client == null) {
            for (Entry<Integer, ConnectionListener> entry : connectionListenersMap.entrySet()) {
                client = (Client) entry.getValue();
                if (client.getContext().equals(ctx)) {
                    connectionListenersMap.remove(client.getAccountNumber());
                    break;
                }
            }
        } else {
            connectionListenersMap.remove(client.getAccountNumber());
        }
    }

    @Override
    public void onCreate(SessionID sessionId) {
        logger.debug("Session created: " + sessionId);
        // Identify trading and quoting session IDs

        if (null == sessionId.getTargetCompID()) {
            System.err.println("UNKNOWN SESSION CREATED WITH TargetCompID : " + sessionId.getTargetCompID());
            System.err.println("PLEASE CHECK SETTINGS FILE OR EDIT CODE");
            System.exit(1);
        } else switch (sessionId.getTargetCompID()) {
            case Config.TRADE_SESSION_TARGET_COMP_ID -> {
                tradingSessionID = sessionId;
                tradingSession = Session.lookupSession(tradingSessionID);
            }
            case Config.PRICE_SESSION_TARGET_COMP_ID -> {
                quoteSessionID = sessionId;
                quoteSession = Session.lookupSession(quoteSessionID);
            }
            default -> {
                System.err.println("UNKNOWN SESSION CREATED WITH TargetCompID : " + sessionId.getTargetCompID());
                System.err.println("PLEASE CHECK SETTINGS FILE OR EDIT CODE");
                System.exit(1);
            }
        }

    }

    @Override
    public void onLogon(SessionID sessionId) {

        // Identify trading and quoting session IDs
        if (Config.TRADE_SESSION_TARGET_COMP_ID.equals(sessionId.getTargetCompID())) {
            logger.debug("Trading session logged in: " + sessionId);
        } else if (Config.PRICE_SESSION_TARGET_COMP_ID.equals(sessionId.getTargetCompID())) {
            logger.debug("Quote session logged in: " + sessionId);
        } else {
            System.err.println("UNKNOWN SESSION CREATED WITH TargetCompID : " + sessionId.getTargetCompID());
            System.err.println("PLEASE CHECK SETTINGS FILE OR EDIT CODE");
            System.exit(1);
        }

        //Send Account Info request
        if (sessionId.equals(tradingSessionID)) {
            sendRequestActiveOrders();
            sendRequestCurrentOpenPositions();//or may be 
        }

        //Send Account Info request
        if (sessionId.equals(tradingSessionID)) {
            sendAccountInfoRequest();
        }

        // Send SecurityListRequest to query supported symbols upon logon to the quoting session
        if (sessionId.equals(quoteSessionID)) {
            querySupportedSymbols();
        }

        // Subscribe to market data upon logon to the quoting session
        if (sessionId.equals(quoteSessionID)) {
            subscribeToMarketData(this.fullSymbolInfoMap.keySet());
        }
    }

    // Send SecurityListRequest to query supported symbols
    public void querySupportedSymbols() {
        try {
            SecurityListRequest request = new SecurityListRequest(
                    new SecurityReqID("SecurityListRequest-" + System.currentTimeMillis()),
                    new SecurityListRequestType(SecurityListRequestType.SYMBOL)
            );

            Session.sendToTarget(request, quoteSessionID);
        } catch (SessionNotFound ex) {
            logger.error("An error occurred", ex);
        }
    }

    public void subscribeToMarketData(Set<String> symbolList) {
        for (String symbol : symbolList) {
            subscribeToMarketData(symbol);
        }
    }

    public void subscribeToMarketData(String symbol) {
        String md_req_id_prefix = "symbol-meta data-" + System.currentTimeMillis();

        try {
            MarketDataRequest marketDataRequest = new MarketDataRequest();
            marketDataRequest.set(new MDReqID(md_req_id_prefix));
            marketDataRequest.set(new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_UPDATES));
            marketDataRequest.set(new MarketDepth(0));// 0 -> acccording the LP FIX documention
            marketDataRequest.set(new MDUpdateType(0));// 0 -> acccording the LP FIX documention
            marketDataRequest.set(new NoMDEntryTypes(2));// 2 -> acccording the LP FIX documention
            marketDataRequest.set(new NoRelatedSym(1));// 1 -> acccording the LP FIX documention

            MarketDataRequest.NoMDEntryTypes noMDEntryTypes1 = new MarketDataRequest.NoMDEntryTypes();
            noMDEntryTypes1.set(new MDEntryType(MDEntryType.BID));
            marketDataRequest.addGroup(noMDEntryTypes1);

            MarketDataRequest.NoMDEntryTypes noMDEntryTypes2 = new MarketDataRequest.NoMDEntryTypes();
            noMDEntryTypes2.set(new MDEntryType(MDEntryType.OFFER));
            marketDataRequest.addGroup(noMDEntryTypes2);

            MarketDataRequest.NoRelatedSym noRelatedSym = new MarketDataRequest.NoRelatedSym();
            noRelatedSym.set(new Symbol(symbol));
            marketDataRequest.addGroup(noRelatedSym);

            // Sending the request
            Session.sendToTarget(marketDataRequest, quoteSessionID);
        } catch (SessionNotFound ex) {
            logger.error("An error occurred", ex);
        }
    }

    public void subscribeToMarketData_old(String symbol) {
        String md_req_id_prefix = "symbol-metadata-" + System.currentTimeMillis();

        try {
            // Creating MarketDataRequest with required fields
            MarketDataRequest marketDataRequest = new MarketDataRequest(
                    new MDReqID(md_req_id_prefix + symbol),
                    new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_UPDATES),
                    new MarketDepth(1)
            );

            // Adding related symbol group
            MarketDataRequest.NoRelatedSym noRelatedSym = new MarketDataRequest.NoRelatedSym();
            noRelatedSym.set(new Symbol(symbol));
            marketDataRequest.addGroup(noRelatedSym);

            // Adding NoMDEntries groups for bid, ask, and swap
            MarketDataRequest.NoMDEntryTypes bidEntry = new MarketDataRequest.NoMDEntryTypes();
            bidEntry.set(new MDEntryType(MDEntryType.BID));
            marketDataRequest.addGroup(bidEntry);

            MarketDataRequest.NoMDEntryTypes askEntry = new MarketDataRequest.NoMDEntryTypes();
            askEntry.set(new MDEntryType(MDEntryType.OFFER));
            marketDataRequest.addGroup(askEntry);

            marketDataRequest.set(new MDUpdateType(MDUpdateType.FULL_REFRESH));
            marketDataRequest.set(new AggregatedBook(true));
            marketDataRequest.set(new NoMDEntryTypes(2));// 2 -> That is BID and OFFER (Ask) 

            // Sending the request
            Session.sendToTarget(marketDataRequest, quoteSessionID);
        } catch (SessionNotFound ex) {
            logger.error("An error occurred", ex);
        }
    }

    public void unsubscribeToMarketData(Set<String> symbolList) {
        for (String symbol : symbolList) {
            unsubscribeToMarketData(symbol);
        }

    }

    public void unsubscribeToMarketData(String symbol) {

        String md_req_id_prefix = "unsub-symbol-metadata-" + System.currentTimeMillis();
        try {
            MarketDataRequest marketDataRequest = new MarketDataRequest(
                    new MDReqID(md_req_id_prefix + symbol),
                    new SubscriptionRequestType(SubscriptionRequestType.DISABLE_PREVIOUS_SNAPSHOT_UPDATE_REQUEST),
                    new MarketDepth(1)
            );

            MarketDataRequest.NoRelatedSym noRelatedSym = new MarketDataRequest.NoRelatedSym();
            noRelatedSym.set(new Symbol(symbol));
            marketDataRequest.addGroup(noRelatedSym);
            Session.sendToTarget(marketDataRequest, quoteSessionID);
        } catch (SessionNotFound ex) {
            logger.error("An error occurred", ex);
        }

    }

    @Override
    public void onLogout(SessionID sessionId) {

        if (Config.TRADE_SESSION_TARGET_COMP_ID.equals(sessionId.getTargetCompID())) {
            logger.debug("Trading session logged out: " + sessionId);
        } else if (Config.PRICE_SESSION_TARGET_COMP_ID.equals(sessionId.getTargetCompID())) {
            logger.debug("Quote session logged out: " + sessionId);
        }
    }

    @Override
    public void toAdmin(quickfix.Message message, SessionID sessionId) {
        // Add username and password

        if (message instanceof quickfix.fix44.Logon logon) {
            try {
                logon.set(new ResetSeqNumFlag(true)); // Reset sequence numbers on logon
                logon.set(new Username(settings.getString(sessionId, "Username"))); // Set username
                logon.set(new Password(settings.getString(sessionId, "Password"))); // Set password
            } catch (ConfigError ex) {
                logger.error("An error occurred", ex);
            }
        }

        //logger.debug("To Admin: " + message);
    }

    @Override
    public void fromAdmin(quickfix.Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        //logger.debug("From Admin: " + message);
    }

    @Override
    public void toApp(quickfix.Message message, SessionID sessionId) throws DoNotSend {
        //logger.debug("To App: " + message);
    }

    @Override
    public void fromApp(quickfix.Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        //logger.debug("From App: " + message);
        if (!customResponse(message)) {
            crack(message, sessionId);
        }

    }

    private boolean customResponse(quickfix.Message message) {
        try {
            String msgType = message.getHeader().getField(new MsgType()).getValue();
            if ("UAB".equals(msgType)) {
                handleAccountInfoResponse(message);
                return true;
            }
            //more custom message may go here

        } catch (FieldNotFound ex) {
            logger.error("An error occurred", ex);
        }
        return false;
    }

    private void handleAccountInfoResponse(quickfix.Message message) {
        // Extract custom fields from the response message
        try {
            if (message.isSetField(AccountInfoResponse.ACCOUNT_INFO_RESULT_FIELD)) {
                int result = message.getInt(AccountInfoResponse.ACCOUNT_INFO_RESULT_FIELD);
                switch (result) {
                    case 0 ->
                        logger.debug("Account Info: Recieved successfully");
                    case 1 ->
                        logger.debug("Account Info: Invalid or unknown account");
                    case 99 ->
                        logger.debug("Account Info: Other [not successful]");
                    default ->
                        logger.debug("UNKNOW RESULT OF ACCOUNT INFO RESPONSE");
                }
            }
            if (message.isSetField(AccountInfoResponse.CURRENCY_FIELD)) {
                String currency = message.getString(AccountInfoResponse.CURRENCY_FIELD);
                logger.debug("Currency: " + currency);
            }

            if (message.isSetField(AccountInfoResponse.BALANCE_FIELD)) {
                double balance = message.getDouble(AccountInfoResponse.BALANCE_FIELD);
                logger.debug("Balance: " + balance);
            }

            if (message.isSetField(AccountInfoResponse.EQUITY_FIELD)) {
                double equity = message.getDouble(AccountInfoResponse.EQUITY_FIELD);
                logger.debug("Equity: " + equity);
            }

            if (message.isSetField(AccountInfoResponse.CREDIT_FIELD)) {
                double credit = message.getDouble(AccountInfoResponse.CREDIT_FIELD);
                logger.debug("Credit: " + credit);
            }

            if (message.isSetField(AccountInfoResponse.MARGIN_FIELD)) {
                double margin = message.getDouble(AccountInfoResponse.MARGIN_FIELD);
                logger.debug("Margin: " + margin);
            }

            if (message.isSetField(AccountInfoResponse.FREE_MARGIN_FIELD)) {
                double free_margin = message.getDouble(AccountInfoResponse.FREE_MARGIN_FIELD);
                logger.debug("Free Margin: " + free_margin);
            }

            if (message.isSetField(AccountInfoResponse.PROFIT_FIELD)) {
                double profit = message.getDouble(AccountInfoResponse.PROFIT_FIELD);
                logger.debug("Profit: " + profit);
            }
        } catch (FieldNotFound ex) {
            logger.error("An error occurred", ex);
        }

        // Handle other custom fields as needed
        // Handle other custom fields as needed
        // Handle other custom fields as needed
        // Handle other custom fields as needed
    }

    // This method handles execution reports which confirm order status
    public void onMessage(ExecutionReport message, SessionID sessionId) throws FieldNotFound {
        char execType = message.getChar(ExecType.FIELD);
        char orderStatus = message.getChar(ExecType.ORDER_STATUS);

        switch (execType) {
            case ExecType.NEW -> {

                logger.debug("New Order: " + message);
                String clOrdID = message.getClOrdID().getValue();
                onNewOrder(clOrdID);
            }
            case ExecType.REJECTED -> {
                logger.debug("Order rejected: " + message);
                String errMsg = message.getField(new StringField(58)).getValue();
                String clOrdID = message.getClOrdID().getValue();
                onRejectedOrder(clOrdID, errMsg);
            }
            case ExecType.CANCELED -> {
                logger.debug("Order cancelled: " + message);
                String clOrdID = message.getClOrdID().getValue();
                onCancelledOrder(clOrdID);
            }
            case ExecType.FILL -> {
                //String ordid = message.getClOrdID().toString();
                double filled_price = message.getDouble(AvgPx.FIELD);
                logger.debug("Order filled at price: " + filled_price);

            }
            case ExecType.TRADE -> {
                double lastPx = message.getDouble(LastPx.FIELD);

                double commission = message.isSetField(Commission.FIELD)
                        ? message.getDouble(Commission.FIELD) : 0;

                logger.debug("Trade executed at price: " + lastPx);
                logger.debug(message.isSetField(
                        Commission.FIELD) ? ("Commision: " + commission)
                                : "Commission not set or may not be implemented");

                String clOrdID = message.getClOrdID().getValue();
                onExecutedOrder(clOrdID, lastPx);
            }
            case ExecType.REPLACED -> {
                logger.debug("Order replaced: " + message);
                String clOrdID = message.getClOrdID().getValue();
                //onReplacedOrder(clOrdID);
            }
            case ExecType.ORDER_STATUS -> {
                logger.debug("Order Status: " + message);

                orderMassStatusReport(message);

            }
        }

    }

    public void onMessage(MarketDataSnapshotFullRefresh message, SessionID sessionID) {
        try {
            for (int i = 1; i <= message.getGroupCount(NoMDEntries.FIELD); i++) {
                Group group = message.getGroup(i, NoMDEntries.FIELD);
                Symbol symbol = new Symbol();
                message.getField(symbol); // Extract symbol from the group
                processMarketDataEntry(symbol, group, FULL_REFRESH); // Pass symbol and group to processing method
            }
        } catch (FieldNotFound ex) {
            logger.error("An error occurred", ex);
        }
    }

    public void onMessage(MarketDataIncrementalRefresh message, SessionID sessionID) {
        try {
            for (int i = 1; i <= message.getGroupCount(NoMDEntries.FIELD); i++) {
                Group group = message.getGroup(i, NoMDEntries.FIELD);
                Symbol symbol = new Symbol();
                group.getField(symbol); // Extract symbol from the group
                processMarketDataEntry(symbol, group, INCREMENTAL_REFRESH); // Pass symbol and group to processing method
            }
        } catch (FieldNotFound ex) {
            logger.error("An error occurred", ex);
        }
    }

    
    
    public void onMessage(OrderCancelReject message, SessionID sessionId) throws FieldNotFound {
        
        String clOrderID = message.getString(ClOrdID.FIELD);
        int intReason = message.getInt(CxlRejReason.FIELD);
        int intResponseTo = message.getInt(CxlRejResponseTo.FIELD);
        
        String strReason = "";
        switch (intReason) {
            case 0 -> strReason = "Too Late";
            case 1 -> strReason = "Unknown Order";
            case 2 -> strReason = "Broker Opt";
            default -> {
            }
        }
        
        
        if(intResponseTo == 1){ // 1 means it is in response to Order Cancel Request
            onOrderCancelRequestRejected(clOrderID, strReason);
        }
    }


    String extractOriginalClOrderID(String clOrdID) {
        int dash_index = clOrdID.indexOf('-');
        //remove LP appended string and return the rest
        return clOrdID.substring(dash_index + 1);
    }

    public void onMessage(PositionReport positionReport, SessionID sessionId) throws FieldNotFound {

        // Body Fields in PositionReport
        String account = positionReport.getString(Account.FIELD);                      // tag 1
        String symbol = positionReport.getString(Symbol.FIELD);                        // tag 55
        int noPartyIDs = positionReport.getInt(NoPartyIDs.FIELD);                      // tag 453
        int accountType = positionReport.getInt(AccountType.FIELD);                      // tag 581
        String posReqID = positionReport.getString(PosReqID.FIELD);          // tag 710
        String clearingBusinessDate = positionReport.getString(ClearingBusinessDate.FIELD);       // tag 715
        String settlSessID = positionReport.getString(SettlSessID.FIELD);              // tag 716
        String posMaintRptID = positionReport.getString(PosMaintRptID.FIELD);          // tag 721
        int posReqResult = positionReport.getInt(PosReqResult.FIELD);                      // tag 728
        double settlPrice = positionReport.getDouble(SettlPrice.FIELD);                // tag 730
        int settlPriceType = positionReport.getInt(SettlPriceType.FIELD);            // tag 731
        double priorSettlPrice = positionReport.getDouble(PriorSettlPrice.FIELD);  // tag 734

        // Nested Group Fields (PositionAmountData group, e.g., tags 702, 703, 704)
        int noPositions = positionReport.getInt(NoPositions.FIELD);                    // tag 702
        String posType = positionReport.getString(PosType.FIELD);                      // tag 703
        double longQty = positionReport.getDouble(LongQty.FIELD);                      // tag 704
        double shortQty = positionReport.getDouble(ShortQty.FIELD);                    // tag 705
        int noPosAmt = positionReport.getInt(NoPosAmt.FIELD);                         // tag 753

        // Position Amount Data Group Fields (e.g., tags 707, 708)
        String posAmtType = positionReport.getString(PosAmtType.FIELD);                // tag 707
        double posAmt = positionReport.getDouble(PosAmt.FIELD);                        // tag 708

        // Print out all values for verification
        System.out.println("Position Report Fields:");

        System.out.println("Account: " + account);
        System.out.println("Symbol: " + symbol);
        System.out.println("NoPartyIDs: " + noPartyIDs);
        System.out.println("AccountType: " + accountType);
        System.out.println("PosReqID: " + posReqID);
        System.out.println("ClearingBusinessDate: " + clearingBusinessDate);
        System.out.println("SettlSessID: " + settlSessID);
        System.out.println("PosMaintRptID: " + posMaintRptID);
        System.out.println("PosReqResult: " + posReqResult);
        System.out.println("SettlPrice: " + settlPrice);
        System.out.println("SettlPriceType: " + settlPriceType);
        System.out.println("PriorSettlPrice: " + priorSettlPrice);
        System.out.println("NoPositions: " + noPositions);
        System.out.println("PosType: " + posType);
        System.out.println("LongQty: " + longQty);
        System.out.println("ShortQty: " + shortQty);
        System.out.println("noPosAmt: " + noPosAmt);
        System.out.println("PosAmtType: " + posAmtType);
        System.out.println("PosAmt: " + posAmt);

        String clOrdID = extractOriginalClOrderID(settlSessID);
        var positon = new Position();
        positon.setID(clOrdID);
        positon.setPrice(settlPrice);
        positon.setQty(longQty != 0 ? longQty : shortQty);
        positon.setSide(longQty != 0 ? ManagedOrder.Side.BUY : ManagedOrder.Side.SELL);
        positon.setSymbol(symbol);
        positon.setTime(OrderIDUtil.getTime(clOrdID));

        positionAtLPList.add(positon);
        
        onPositionReport(positon);

    }

    private void orderMassStatusReport(ExecutionReport executionReport) throws FieldNotFound {

        // Accessing fields from ExecutionReport
        String account = executionReport.getString(Account.FIELD);                      // tag 1
        double avgPx = executionReport.getDouble(AvgPx.FIELD);                          // tag 6
        String clOrdID = executionReport.getString(ClOrdID.FIELD);                      // tag 11
        int cumQty = executionReport.getInt(CumQty.FIELD);                              // tag 14
        String execID = executionReport.getString(ExecID.FIELD);                        // tag 17
        double lastPx = executionReport.getDouble(LastPx.FIELD);                        // tag 31
        String orderID = executionReport.getString(OrderID.FIELD);                      // tag 37
        double orderQty = executionReport.getDouble(OrderQty.FIELD);                    // tag 38
        char ordStatus = executionReport.getChar(OrdStatus.FIELD);                      // tag 39
        char side = executionReport.getChar(Side.FIELD);                                // tag 54
        String symbol = executionReport.getString(Symbol.FIELD);                        // tag 55
        char timeInForce = executionReport.getChar(TimeInForce.FIELD);                  // tag 59
        char execType = executionReport.getChar(ExecType.FIELD);                        // tag 150
        double leavesQty = executionReport.getDouble(LeavesQty.FIELD);                  // tag 151
        String expireDate = executionReport.getString(ExpireDate.FIELD);                // tag 432
        String text = executionReport.getString(Text.FIELD);                            // tag 58
        String massStatusReqID = executionReport.getString(MassStatusReqID.FIELD);       // tag 584
        int totNumReports = executionReport.getInt(TotNumReports.FIELD);                 // tag 911
        boolean lastRptRequested = executionReport.getBoolean(LastRptRequested.FIELD); // tag 912

        // Print all values
        System.out.println("Execution Report Fields:");

        System.out.println("Account: " + account);
        System.out.println("AvgPx: " + avgPx);
        System.out.println("ClOrdID: " + clOrdID);
        System.out.println("CumQty: " + cumQty);
        System.out.println("ExecID: " + execID);
        System.out.println("LastPx: " + lastPx);
        System.out.println("OrderID: " + orderID);
        System.out.println("OrderQty: " + orderQty);
        System.out.println("OrdStatus: " + ordStatus);
        System.out.println("Side: " + side);
        System.out.println("Symbol: " + symbol);
        System.out.println("TimeInForce: " + timeInForce);
        System.out.println("ExecType: " + execType);
        System.out.println("LeavesQty: " + leavesQty);
        System.out.println("ExpireDate: " + expireDate);
        System.out.println("Text: " + text);
        System.out.println("massStatusReqID: " + massStatusReqID);
        System.out.println("totNumReports: " + totNumReports);
        System.out.println("lastRptRequested: " + lastRptRequested);

        var unfilleOrderAtLP = new UnfilledOrder();

        unfilleOrderAtLP.setID(extractOriginalClOrderID(clOrdID));
        unfilleOrderAtLP.setPrice(lastPx);
        unfilleOrderAtLP.setSide(side);
        unfilleOrderAtLP.setSymbol(symbol);

        if (cumQty == 0) {//we only want order not filled at all 
            unfilledOrderAtLPList.add(unfilleOrderAtLP);
        }else{
            logger.warn("order partially or fully filled"); //come back for more detail warning message
        }
        
        if (unfilledOrderAtLPList.size() == totNumReports) {
            //now we can recreate the open orders
            recreateOpenOrders();
        }
    }

    void recreateOpenOrders() {

        positionAtLPList.forEach((Position position) -> {

            for (UnfilledOrder unfilledOrder : unfilledOrderAtLPList) {
                
                
            }

        });

    }

    private void processMarketDataEntry(Symbol symbol, Group group, int refresh_type) throws FieldNotFound {
        MDEntryType entryType = new MDEntryType();
        MDEntryPx entryPx = new MDEntryPx();

        double bidPrice = 0;
        double askPrice = 0;
        double point = 0.0001; // Example point value for EUR/USD
        double tickValue = 0;
        double tickSize = 0;

        if (group.isSetField(entryType)) {
            group.getField(entryType);
            switch (entryType.getValue()) {
                case MDEntryType.BID ->
                    bidPrice = group.getField(entryPx).getValue();
                case MDEntryType.OFFER ->
                    askPrice = group.getField(entryPx).getValue();
                default -> {
                }
            }
        }

        tickSize = point; //come back
        tickValue = 1; //come back

        //logger.debug("Point: " + point);
        //logger.debug("Tick Size: " + tickSize);
        //logger.debug("Tick Value: " + tickValue);
        String symbolName = symbol.getValue();

        SymbolInfo symbolInfo = this.fullSymbolInfoMap.get(symbolName);
        if (symbolInfo == null) {
            int digits = determineSymbolDigits(symbolName);
            symbolInfo = new SymbolInfo(symbolName, digits,
                    tickValue,
                    tickSize);
            this.fullSymbolInfoMap.put(symbolName, symbolInfo);
            SymbolInfo symbolInfo_added = symbolInfo;
        }
        symbolInfo.setBid(bidPrice);
        symbolInfo.setAsk(askPrice);
        checkLocalPendingOrderHit(symbolInfo);
        SymbolInfo symbolInfo_price_change = symbolInfo;

        symbolUpdateListenersMap.values().forEach(listener -> {
            listener.onPriceChange(symbolInfo_price_change);
        });

    }

    @Override
    public void sendAccountInfoRequest() {
        if (tradingSessionID == null) {
            return;
        }
        String accountID = "potential@mtr.pl";

        try {
            AccountInfoRequest accRequest = new AccountInfoRequest();

            accRequest.setAccountInfoReqID("account_info_req_id" + System.currentTimeMillis());
            accRequest.setAccount(accountID);

            Session.sendToTarget(accRequest, tradingSessionID);
        } catch (SessionNotFound ex) {
            logger.error("An error occurred", ex);
        }

    }

    @Override
    public void sendRequestCurrentOpenPositions() {
        try {
            RequestForPositions request = new RequestForPositions();
            request.set(new PosReqID("open-positions-" + System.currentTimeMillis())); // Unique request ID
            request.set(new PosReqType(PosReqType.POSITIONS)); // Request type for positions
            request.set(new Account(settings.getString("Account"))); //The account for which positions are requested
            request.setField(new StringField(715, "CURRENT"));//According to LP doc : ClearingBusinessDate, Local DateTime – currently not used ‘CURRENT’ or any other text will fit the requirements
            request.set(new AccountType(AccountType.ACCOUNT_IS_CARRIED_ON_CUSTOMER_SIDE_OF_THE_BOOKS));
            request.set(new ClearingBusinessDate());
            request.set(new TransactTime());

            Session.sendToTarget(request, tradingSessionID);

        } catch (SessionNotFound | ConfigError ex) {
            logger.error(ex.getMessage(), ex);
        }

    }

    @Override
    public void sendRequestActiveOrders() {
        try {
            OrderMassStatusRequest request = new OrderMassStatusRequest();
            request.set(new MassStatusReqID("active-orders-" + System.currentTimeMillis()));
            request.set(new MassStatusReqType(6));
            request.set(new Account(settings.getString("Account"))); //The account for which positions are requested

            Session.sendToTarget(request, tradingSessionID);

        } catch (SessionNotFound | ConfigError ex) {
            logger.error(ex.getMessage(), ex);
        }

    }

    @Override
     public abstract void onNewOrder(String clOrdID);

    @Override
     public abstract void onRejectedOrder(String clOrdID, String errMsg);

    @Override
     public abstract void onCancelledOrder(String clOrdID);

    @Override
    public abstract void onOrderCancelRequestRejected(String clOrdID, String reason);

    @Override
    public abstract void onExecutedOrder(String clOrdID, double price);

    @Override
    public abstract void onPositionReport(Position position);

    @Override
    public abstract void onOrderReport(UnfilledOrder unfilledOrder, int totalOrders);
    
    
    
    @Override
    public void sendMarketOrder(String req_identifier, ManagedOrder order) {
        try {
            quickfix.fix44.NewOrderSingle newOrder = new quickfix.fix44.NewOrderSingle(
                    new ClOrdID(order.getOrderID()),
                    new Side(order.getSide()),
                    new TransactTime(),
                    new OrdType(OrdType.MARKET)
            );

            newOrder.set(new Symbol(order.getSymbol()));
            newOrder.set(new OrderQty(order.getLotSize() * FX_LOT_QTY)); // Set lot size to 1.2 lots (120,000 units)

            Session.sendToTarget(newOrder, tradingSessionID);

        } catch (SessionNotFound ex) {
            logger.error("Could not send market order", ex);
            orderActionListenersMap
                    .getOrDefault(order.getAccountNumber(), DO_NOTHING_OAL)
                    .onOrderRemoteError(null, order, "Could not send market order - Something Went Wrong!");
        }
    }

    public char opposingSide(char side) {
        if (side == Side.BUY) {
            return Side.SELL;
        }
        if (side == Side.SELL) {
            return Side.BUY;
        }

        return 0;//unknown
    }

    @Override
    public void placePendingOrder(String req_identifier, ManagedOrder order) {
    }

    public void cancelOrder(String clOrdId, String symbol, char side, double lot_size) throws SessionNotFound {

            OrderCancelRequest cancelRequest = new OrderCancelRequest(
                    new OrigClOrdID(clOrdId),
                    new ClOrdID("cancel-order-" + System.currentTimeMillis()),
                    new Side(side),
                    new TransactTime()
            );

            cancelRequest.set(new OrderQty(lot_size * FX_LOT_QTY)); // Original order quantity
            cancelRequest.set(new Symbol(symbol));
            Session.sendToTarget(cancelRequest, tradingSessionID);

    }

    /**
     * Modify target and stop loss orders abstract protected void
     *
     * @param req_identifier
     * @param clOrdId
     * @param target_price
     * @param stoploss_price
     */
    @Override
    abstract public void modifyOpenOrder(String req_identifier, String clOrdId, double target_price, double stoploss_price);

    @Override
    abstract public void deletePendingOrder(String req_identifier, String clOrdId);

    @Override
    abstract public void sendClosePosition(String req_identifier, String clOrdId, double lot_size);

    public boolean isQuoteSessionLogon() {
        Session session = Session.lookupSession(quoteSessionID);
        return session == null || session.isLoggedOn();
    }

    public boolean isTradeSessionLogon() {
        Session session = Session.lookupSession(tradingSessionID);
        return session == null || session.isLoggedOn();
    }

    private int determineSymbolDigits(String symbol) {
        int digits = 5;//default

        //COME BACK FOR MORE ADVANCED CHECKS
        if (symbol.endsWith("JPY")
                || symbol.equals("XAUUSD")
                || symbol.equals("XAGUSD")) {
            digits = 3;
        }

        return digits;
    }

    @Override
    public void shutdown() {
        this.initiator.stop();
    }

    protected void checkLocalPendingOrderHit(SymbolInfo symbolInfo) {
    }
}
