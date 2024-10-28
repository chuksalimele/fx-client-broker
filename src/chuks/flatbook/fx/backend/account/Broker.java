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
import chuks.flatbook.fx.common.account.profile.AdminProfile;
import chuks.flatbook.fx.common.account.profile.BasicAccountProfile;
import chuks.flatbook.fx.common.account.profile.UserType;

/**
 *
 * @author user
 */
public abstract class Broker extends quickfix.MessageCracker implements quickfix.Application, BrokerAccount {

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
        if(user_type == null){
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

        if (Config.TRADE_SESSION_TARGET_COMP_ID.equals(sessionId.getTargetCompID())) {
            tradingSessionID = sessionId;
            tradingSession = Session.lookupSession(tradingSessionID);
        } else if (Config.PRICE_SESSION_TARGET_COMP_ID.equals(sessionId.getTargetCompID())) {
            quoteSessionID = sessionId;
            quoteSession = Session.lookupSession(quoteSessionID);
        } else {
            System.err.println("UNKNOWN SESSION CREATED WITH TargetCompID : " + sessionId.getTargetCompID());
            System.err.println("PLEASE CHECK SETTINGS FILE OR EDIT CODE");
            System.exit(1);
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

                logger.debug("Order accepted: " + message);
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
        }

        switch (orderStatus) {
            // case OrdStatus.
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
        //TODO
    }

    public void onMessage(PositionReport message, SessionID sessionId) throws FieldNotFound {

        String posMaintRptID = message.getString(PosMaintRptID.FIELD);
        String posReqID = message.getString(PosReqID.FIELD);
        String account = message.getAccount().getValue();
        String symbol = message.getString(Symbol.FIELD);
        logger.debug("Position Report for Account: " + account);

        // Accessing the NoPositions group
        if (message.isSetField(NoPositions.FIELD)) {
            int noPositionsCount = message.getInt(NoPositions.FIELD);
            for (int i = 1; i <= noPositionsCount; i++) {
                Group positionGroup = message.getGroup(i, NoPositions.FIELD);
                //String symbol = positionGroup.isSetField(Symbol.FIELD) ? positionGroup.getString(Symbol.FIELD) : "N/A";
                double longQty = positionGroup.isSetField(LongQty.FIELD) ? positionGroup.getDouble(LongQty.FIELD) : 0.0;
                double shortQty = positionGroup.isSetField(ShortQty.FIELD) ? positionGroup.getDouble(ShortQty.FIELD) : 0.0;

                logger.debug("Symbol: " + symbol);
                logger.debug("Long Quantity: " + longQty);
                logger.debug("Short Quantity: " + shortQty);
            }
        }

        //List<PositionReport.NoPositions> positions = message.getGroups(PositionReport.NoPosAmt.);
        //for (PositionReport.NoPositions pos : positions) {
        //String symbol = pos.getString(Symbol.FIELD);
        //double positionQty = pos.get(new PosQty()).getValue();
        //logger.debug("Symbol: " + symbol + ", Position Quantity: " + positionQty);
        //}
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
        RequestForPositions request = new RequestForPositions();
        request.set(new PosReqID("open-positions-" + System.currentTimeMillis())); // Unique request ID
        request.set(new PosReqType(PosReqType.POSITIONS)); // Request type for positions
        //request.set(new Account(account_name)); //COME BACK!!! The account for which positions are requested
        request.set(new AccountType(AccountType.ACCOUNT_IS_CARRIED_ON_CUSTOMER_SIDE_OF_THE_BOOKS));
        request.set(new ClearingBusinessDate());
        request.set(new TransactTime());
        try {
            Session.sendToTarget(request, tradingSessionID);
        } catch (quickfix.SessionNotFound ex) {
            logger.error("An error occurred", ex);
        }
    }

    @Override
    public void sendRequestActiveOrders() {
        OrderMassStatusRequest request = new OrderMassStatusRequest(
                new MassStatusReqID("active-orders-" + System.currentTimeMillis()
                ), new MassStatusReqType(6)
        );

        //request.set(new Account(account_name)); //COME BACK!!! The account for which positions are requested
        try {
            Session.sendToTarget(request, tradingSessionID);
        } catch (quickfix.SessionNotFound ex) {
            logger.error("An error occurred", ex);
        }

    }

    abstract protected void onNewOrder(String clOrdID);

    abstract protected void onRejectedOrder(String clOrdID, String errMsg);

    abstract protected void onCancelledOrder(String clOrdID);

    abstract protected void onExecutedOrder(String clOrdID, double price);

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

    public void cancelOrder(String clOrdId, String symbol, char side, double lot_size) {

        try {
            OrderCancelRequest cancelRequest = new OrderCancelRequest(
                    new OrigClOrdID(clOrdId),
                    new ClOrdID("cancel-order-" + System.currentTimeMillis()),
                    new Side(opposingSide(side)),
                    new TransactTime()
            );

            cancelRequest.set(new OrderQty(lot_size * FX_LOT_QTY)); // Original order quantity
            cancelRequest.set(new Symbol(symbol));
            Session.sendToTarget(cancelRequest, tradingSessionID);

        } catch (SessionNotFound ex) {
            logger.error("An error occurred", ex);
        }
    }

    /**
     * Modify target and stop loss orders abstract protected void
     *
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
