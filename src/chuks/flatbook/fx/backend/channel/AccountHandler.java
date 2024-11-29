/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.channel;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.ScanException;
import ch.qos.logback.core.util.OptionHelper;
import chuks.flatbook.fx.backend.account.contract.BrokerAccount;
import chuks.flatbook.fx.backend.account.Client;
import static chuks.flatbook.fx.backend.account.Client.NO_ACCOUNT_NUMBER;
import chuks.flatbook.fx.common.account.profile.TraderInfo;
import chuks.flatbook.fx.backend.account.persist.AdminDB;
import chuks.flatbook.fx.common.account.order.ManagedOrder;
import chuks.flatbook.fx.common.account.order.OrderException;
import chuks.flatbook.fx.backend.account.persist.TraderDB;
import chuks.flatbook.fx.backend.config.Config;
import chuks.flatbook.fx.transport.DynamicIpFilter;
import chuks.flatbook.fx.transport.SharableTransportHandler;
import chuks.flatbook.fx.transport.message.ChannelMessage;
import static chuks.flatbook.fx.transport.message.MessageType.DELETE_PENDING_ORDER;
import static chuks.flatbook.fx.transport.message.MessageType.GET_ACCOUNT_LIST;
import static chuks.flatbook.fx.transport.message.MessageType.LOGIN;
import static chuks.flatbook.fx.transport.message.MessageType.MODIFY_OPEN_ORDER;
import static chuks.flatbook.fx.transport.message.MessageType.MODIFY_PENDING_ORDER;
import static chuks.flatbook.fx.transport.message.MessageType.PLACE_PENDING_ORDER;
import static chuks.flatbook.fx.transport.message.MessageType.SEND_CLOSE_POSITION;
import static chuks.flatbook.fx.transport.message.MessageType.SEND_MARKET_ORDER;
import static chuks.flatbook.fx.transport.message.MessageType.SIGN_UP;
import io.netty.channel.ChannelHandlerContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.List;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import static chuks.flatbook.fx.backend.config.Error.ERR_NO_ACCOUNT_ALTER_PRIVILEDGE;
import static chuks.flatbook.fx.backend.config.Error.ERR_NO_ACCOUNT_VIEW_PRIVILEDGE;
import static chuks.flatbook.fx.backend.config.Error.ERR_NO_SERVER_CONFIG_PRIVILEDGE;
import static chuks.flatbook.fx.backend.config.LogMarker.MARKER_REJECTED_IP;
import chuks.flatbook.fx.common.account.profile.UserType;
import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;
import static chuks.flatbook.fx.backend.config.LogMarker.MARKER_SUSPICIOUS_IP;
import chuks.flatbook.fx.common.util.Utils;
import chuks.flatbook.fx.common.util.log.LogCapture;
import static chuks.flatbook.fx.common.util.log.LogConst.concatLogMsg;
import chuks.flatbook.fx.common.util.log.LogLevel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 *
 * @author user
 */
class AccountHandler extends SharableTransportHandler {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AccountHandler.class.getName());

    // private static final org.slf4j.Logger rejectedIPLogger = LoggerFactory.getLogger("REJECTED_IP_LOGGER");
    // private static final org.slf4j.Logger suspiciousIPLogger = LoggerFactory.getLogger("SUSPICIOUS_IP_LOGGER");
    BrokerAccount brokerAccount;
    private final Map<Integer, Client> clientsMap = new ConcurrentHashMap<>();
    private int maxRequestsPerSecond = 100;
    private int maxConnectionsPerIp = 10;

    private final ConcurrentHashMap<String, Integer> ipRequestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> ipConnections = new ConcurrentHashMap<>();
    private final DynamicIpFilter dynamicIpFilter;
    private final int LOWER_LIMIT_OF_MAX_REQUEST_PER_SECOND_PER_IP = 10;
    private final int LOWER_LIMIT_OF_MAX_CONNECTION_PER_IP = 2;

    AccountHandler(BrokerAccount brokerAccount, DynamicIpFilter dynamicIpFilter) {
        this.brokerAccount = brokerAccount;
        this.dynamicIpFilter = dynamicIpFilter;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        startResetTask(ctx);  // Resets the request count every second

        InetAddress address = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
        String ipAddress = address.getHostAddress();

        ipConnections.compute(ipAddress, (ip, connections) -> {
            if (connections == null) {
                return 1;
            } else if (connections >= maxConnectionsPerIp) {
                logger.info(MARKER_SUSPICIOUS_IP,
                        concatLogMsg(ipAddress, "Max. connection of " + maxConnectionsPerIp + " exceeded"));
                ctx.close();
                return connections;
            } else {
                return connections + 1;
            }
        });
    }

    private void startResetTask(ChannelHandlerContext ctx) {
        ctx.executor().scheduleAtFixedRate(() -> {
            ipRequestCounts.clear(); // Reset request counts every second
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        InetAddress address = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
        String ipAddress = address.getHostAddress();

        ipConnections.compute(ipAddress, (ip, connections) -> {
            if (connections == null || connections <= 1) {
                return null; // Remove the entry if no connections remain
            } else {
                return connections - 1;
            }
        });

        handleLogout(ctx, null);

    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ChannelMessage msg) throws Exception {

        String ip = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();

        // Track the number of requests from this IP
        ipRequestCounts.merge(ip, 1, Integer::sum);

        if (ipRequestCounts.get(ip) > maxRequestsPerSecond) {
            logger.info(MARKER_SUSPICIOUS_IP,
                    concatLogMsg(ip, "Rate limit of " + maxRequestsPerSecond + " exceeded"));
            ctx.close(); // Close connection if rate limit exceeded
            return;
        }

        onMessage(ctx, msg);
    }

    private void onMessage(ChannelHandlerContext ctx, ChannelMessage msg) {

        switch (msg.getType()) {
            case SIGN_UP ->
                handleSignUp(ctx, msg);
            case LOGIN ->
                handleLogin(ctx, msg);
            case SEND_MARKET_ORDER ->
                handleSendMarketOrder(msg);
            case MODIFY_OPEN_ORDER ->
                handleModifyOpenOrder(msg);
            case SEND_CLOSE_POSITION ->
                handleSendClosePosition(msg);
            case PLACE_PENDING_ORDER ->
                handlePlacePendingOrder(msg);
            case MODIFY_PENDING_ORDER ->
                handleModifyPendingOrder(msg);
            case DELETE_PENDING_ORDER ->
                handleDeletePendingOrder(msg);
            case GET_SUPPORTED_SYMBOLS ->
                handleGetSupportedSymbols();
            case GET_SELECTED_SYMBOL_INFOF_LIST ->
                handleGetSelectedSymbolInfoList(msg);
            case SUBCRIBE_SYMBOLS ->
                handleSubscribeSymbols(msg);
            case APPROVE_ACCOUNT ->
                handleApproveAccount(msg);
            case ENABLE_ACCOUNT ->
                handleEnableAccount(msg);
            case DISABLE_ACCOUNT ->
                handleDisableAccount(msg);
            case ACTIVATE_ACCOUNT ->
                handleActivateAccount(msg);
            case DEACTIVATE_ACCOUNT ->
                handleDeactivateAccount(msg);
            case CLOSE_ACCOUNT ->
                handleCloseAccount(msg);
            case GET_ACCOUNT_LIST ->
                handleGetAccountList(msg);
            case GET_DEACTIVATED_ACCOUNT_LIST ->
                handleGetDeactivatedAccountList(msg);
            case GET_DISABLED_ACCOUNT_LIST ->
                handleGetDisabledAccountList(msg);
            case GET_UNAPPROVED_ACCOUNT_LIST ->
                handleGetUnapprovedAccountList(msg);
            case GET_CLOSED_ACCOUNT_LIST ->
                handleGetClosedAccountList(msg);
            case WHITELIST_IPS ->
                handleWhitelistIPs(msg);
            case BLACKLIST_IPS ->
                handleBlacklistIPs(msg);
            case GET_LOGS ->
                handleGetLogs(msg);
            case SET_MAX_CONNECTION_PER_IP ->
                handleSetMaxConnectionPerIP(msg);
            case SET_MAX_REQUEST_PER_SECOND_PER_IP ->
                handleSetMaxRequestPerSecondPerIP(msg);
            case GET_MAX_CONNECTION_PER_IP ->
                handleGetMaxConnectionPerIP(msg);
            case GET_MAX_REQUEST_PER_SECOND_PER_IP ->
                handleGetMaxRequestPerSecondPerIP(msg);
            case GET_ADMIN_LIST ->
                handleGetAdminList(msg);
            case LOGOUT ->
                handleLogout(ctx, msg);
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, ChannelMessage msg) {
        int account_number = msg.getInt(0);
        byte[] hash_password = msg.getByteArray(1);
        int user_type = msg.isInt(2) ? msg.getInt(2) : UserType.TRADER.getValue();

        Client client = new Client(account_number, user_type, ctx);
        clientsMap.put(account_number, client);
        this.brokerAccount.addListeners(client);
        if (!this.brokerAccount.login(account_number, hash_password, user_type)) {
            client.onLogInFail(account_number, "Account not found");
        }

    }

    private void handleSignUp(ChannelHandlerContext ctx, ChannelMessage msg) {
        String email = msg.getString(0);
        byte[] hash_password = msg.getByteArray(1);
        String firstName = msg.getString(2);
        String lastName = msg.getString(3);

        TraderInfo profile = new TraderInfo();
        profile.setAccountNumber(NO_ACCOUNT_NUMBER);//for now no account number
        profile.setAccountName(firstName + " " + lastName);
        profile.setEmail(email);
        profile.setPassword(hash_password);
        profile.setRegistrationTime(System.currentTimeMillis());

        if (!brokerAccount.registerTrader(profile)) {
            Client client = new Client(NO_ACCOUNT_NUMBER, -1, ctx);
            client.onSignUpFail("Could not sign up");
        }
    }

    private void handleLogout(ChannelHandlerContext ctx, ChannelMessage msg) {

        if (msg != null) {
            int account_number = msg.isInt(0) ? msg.getInt(0) : NO_ACCOUNT_NUMBER;
            int user_type = msg.isInt(1) ? msg.getInt(1) : UserType.TRADER.getValue();
            this.brokerAccount.logout(account_number, user_type);
            clientsMap.remove(account_number);
        } else {
            //safely remove the client        
            for (Map.Entry<Integer, Client> client : clientsMap.entrySet()) {
                if (client.getValue().getContext().equals(ctx)) {
                    clientsMap.remove(client.getKey()); // Atomic remove using key and value
                    break; // Exit the loop after removing the first match
                }
            }
        }
        this.brokerAccount.clearListeners(ctx);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("An error occurred at " + this.getClass().getName(), cause);
        ctx.close();
    }

    private void handleSendMarketOrder(ChannelMessage msg) {
        try {
            int account_number = msg.getInt(0);
            String stringified_order = msg.getString(1);
            ManagedOrder order = new ManagedOrder(msg.getIdentifier(), account_number, stringified_order);
            brokerAccount.sendMarketOrder(msg.getIdentifier(), order);
        } catch (SQLException | OrderException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleModifyOpenOrder(ChannelMessage msg) {
        String clOrderID = msg.getString(0);
        double target_price = msg.getDouble(1);
        double stoploss_price = msg.getDouble(2);
        brokerAccount.modifyOpenOrder(msg.getIdentifier(), clOrderID, target_price, stoploss_price);
    }

    private void handleSendClosePosition(ChannelMessage msg) {
        String clOrderID = msg.getString(0);
        double lotSize = msg.getDouble(1);
        double closing_price = msg.getDouble(2);
        int slippage = msg.getInt(3);
        brokerAccount.sendClosePosition(msg.getIdentifier(), clOrderID, lotSize, closing_price, slippage);
    }

    private void handlePlacePendingOrder(ChannelMessage msg) {
        try {
            int account_number = msg.getInt(0);
            String strigified_order = msg.getString(1);
            ManagedOrder order = new ManagedOrder(msg.getIdentifier(), account_number, strigified_order);
            brokerAccount.placePendingOrder(msg.getIdentifier(), order);
        } catch (SQLException | OrderException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleModifyPendingOrder(ChannelMessage msg) {
        String clOrderID = msg.getString(0);
        double open_price = msg.getDouble(1);
        double target_price = msg.getDouble(2);
        double stoploss_price = msg.getDouble(3);
        brokerAccount.modifyPendingOrder(msg.getIdentifier(), clOrderID, open_price, target_price, stoploss_price);
    }

    private void handleDeletePendingOrder(ChannelMessage msg) {
        String clOrderID = msg.getString(0);
        brokerAccount.deletePendingOrder(msg.getIdentifier(), clOrderID);
    }

    private void handleGetSupportedSymbols() {
        brokerAccount.getAllSymbols();
    }

    private void handleGetSelectedSymbolInfoList(ChannelMessage msg) {
        int account_number = msg.getInt(0);

        String[] symbols = msg.getStringArray(1);

        Client client = clientsMap.get(account_number);

        client.onSelectedSymbolInfoList(account_number,
                brokerAccount.getSymbolInfoList(symbols));
    }

    private void handleSubscribeSymbols(ChannelMessage msg) {
        int account_number = msg.getInt(0);
        String[] symbols = msg.getStringArray(1);

        Client client = clientsMap.get(account_number);

        client.setSelectedSymbols(symbols);
    }

    private void handleApproveAccount(ChannelMessage msg) {

        String email = msg.getString(0);
        int admin_id = msg.getInt(1);

        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountAlterPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_ALTER_PRIVILEDGE);
                return;
            }
            TraderDB.updateTraderAccountApproved(email, admin_id);

            client.onAccountApproved(email);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }

    }

    private void handleEnableAccount(ChannelMessage msg) {

        String email = msg.getString(0);
        int admin_id = msg.getInt(1);

        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountAlterPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_ALTER_PRIVILEDGE);
                return;
            }
            TraderDB.updateTraderAccountEnabled(email, true);

            client.onAccountEnabled(email);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }

    }

    private void handleDisableAccount(ChannelMessage msg) {

        String email = msg.getString(0);
        int admin_id = msg.getInt(1);

        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountAlterPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_ALTER_PRIVILEDGE);
                return;
            }
            TraderDB.updateTraderAccountEnabled(email, false);

            client.onAccountDisabled(email);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }

    }

    private void handleActivateAccount(ChannelMessage msg) {

        String email = msg.getString(0);
        int admin_id = msg.getInt(1);

        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountAlterPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_ALTER_PRIVILEDGE);
                return;
            }
            TraderDB.updateTraderAccountActive(email, true);

            client.onAccountActivated(email);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }

    }

    private void handleDeactivateAccount(ChannelMessage msg) {

        String email = msg.getString(0);
        int admin_id = msg.getInt(1);

        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountAlterPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_ALTER_PRIVILEDGE);
                return;
            }
            TraderDB.updateTraderAccountActive(email, false);

            client.onAccountDeactivated(email);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }

    }

    private void handleCloseAccount(ChannelMessage msg) {

        String email = msg.getString(0);
        int admin_id = msg.getInt(1);

        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountAlterPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_ALTER_PRIVILEDGE);
                return;
            }
            TraderDB.updateTraderAccountClose(email, true);

            client.onAccountClosed(email);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }

    }

    private void handleGetAdminList(ChannelMessage msg) {

        int page_index = msg.getInt(0);
        int page_size = msg.getInt(1);
        int admin_id = msg.getInt(2);
        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountViewPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_VIEW_PRIVILEDGE);
                return;
            }
            int overall_total = TraderDB.countTraders();
            List list = AdminDB.queryAdminsRange(page_index, page_size);

            client.onPaginatedAdminList(list, overall_total);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleGetAccountList(ChannelMessage msg) {

        int page_index = msg.getInt(0);
        int page_size = msg.getInt(1);
        int admin_id = msg.getInt(2);
        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountViewPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_VIEW_PRIVILEDGE);
                return;
            }
            int overall_total = TraderDB.countTraders();
            List list = TraderDB.queryTraderRange(page_index, page_size);

            client.onPaginatedAccountList(list, overall_total);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleGetDeactivatedAccountList(ChannelMessage msg) {

        int page_index = msg.getInt(0);
        int page_size = msg.getInt(1);
        int admin_id = msg.getInt(2);
        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountViewPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_VIEW_PRIVILEDGE);
                return;
            }
            int overall_total = TraderDB.countDeactivatedAccounts();
            List list = TraderDB.queryDeactivatedAccountRange(page_index, page_size);

            client.onPaginatedDeactivatedAccountList(list, overall_total);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleGetDisabledAccountList(ChannelMessage msg) {

        int page_index = msg.getInt(0);
        int page_size = msg.getInt(1);
        int admin_id = msg.getInt(2);
        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountViewPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_VIEW_PRIVILEDGE);
                return;
            }
            int overall_total = TraderDB.countDisabledAccounts();
            List list = TraderDB.queryDisabledAccountRange(page_index, page_size);

            client.onPaginatedDisabledAccountList(list, overall_total);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleGetUnapprovedAccountList(ChannelMessage msg) {

        int page_index = msg.getInt(0);
        int page_size = msg.getInt(1);
        int admin_id = msg.getInt(2);
        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasAccountViewPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_ACCOUNT_VIEW_PRIVILEDGE);
                return;
            }

            int overall_total = TraderDB.countUnapprovedAccounts();
            List list = TraderDB.queryUnapprovedAccountRange(page_index, page_size);

            client.onPaginatedUnapprovedAccountList(list, overall_total);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleGetClosedAccountList(ChannelMessage msg) {

        int page_index = msg.getInt(0);
        int page_size = msg.getInt(1);
        int admin_id = msg.getInt(2);
        try {
            int overall_total = TraderDB.countClosedAccounts();
            List list = TraderDB.queryClosedAccountRange(page_index, page_size);
            Client client = clientsMap.get(admin_id);
            client.onPaginatedClosedAccountList(list, overall_total);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleWhitelistIPs(ChannelMessage msg) {
        String[] ip_arr = msg.getStringArray(0);
        int admin_id = msg.getInt(1);

        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasServerConfigPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_SERVER_CONFIG_PRIVILEDGE);
                return;
            }
            //allow list of IPs
            this.dynamicIpFilter.whitelistIPs(ip_arr);

            client.onWhitelistedIPs(ip_arr);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleBlacklistIPs(ChannelMessage msg) {
        String[] ip_arr = msg.getStringArray(0);
        int admin_id = msg.getInt(1);

        try {
            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasServerConfigPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_SERVER_CONFIG_PRIVILEDGE);
                return;
            }

            //disallow list of IPs        
            this.dynamicIpFilter.blacklistIPs(ip_arr);

            client.onBlacklistedIPs(ip_arr);

        } catch (SQLException ex) {
            logger.error("An error occurred", ex);
        }
    }

    private void handleGetLogs(ChannelMessage msg) {
        try {
            int log_level = msg.getInt(0);
            long start_time = -1;
            long end_time = -1;
            int admin_id = -1;

            switch (msg.argumentsCount()) {
                case 2 ->
                    admin_id = msg.getInt(1);
                case 3 -> {
                    start_time = msg.getLong(1);
                    admin_id = msg.getInt(2);
                }
                case 4 -> {
                    start_time = msg.getLong(1);
                    end_time = msg.getLong(2);
                    admin_id = msg.getInt(3);
                }
                default -> {
                }
            }

            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasServerConfigPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_SERVER_CONFIG_PRIVILEDGE);
                return;
            }

            //retrieve from file location and forward back to the admin 
            forwardLogRecords(client, log_level, start_time, end_time);

        } catch (FileNotFoundException | SQLException ex) {
            logger.error("An error occurred", ex);
        }

    }

    private void forwardLogRecords(Client client, int log_level, long start_time, long end_time) throws FileNotFoundException {

        try {

            LogLevel level = LogLevel.fromValue(log_level);
            LocalDateTime startTime = Utils.convertMillisToLocalDateTime(
                    start_time, ZoneId.systemDefault());
            LocalDateTime endTime = Utils.convertMillisToLocalDateTime(
                    end_time, ZoneId.systemDefault());

            List<String> logs = LogCapture.captureLogs(level,
                    Config.LOG_DIR,
                    startTime,
                    endTime);
            
            client.onLogs(level, logs);

        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
        }

    }

    private void handleSetMaxConnectionPerIP(ChannelMessage msg) {
        try {
            int max = msg.getInt(0);
            int admin_id = msg.getInt(1);

            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasServerConfigPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_SERVER_CONFIG_PRIVILEDGE);
                return;
            }

            if (max < LOWER_LIMIT_OF_MAX_CONNECTION_PER_IP) {
                client.onRequestFailed("Max connection per ip can not be less than " + LOWER_LIMIT_OF_MAX_CONNECTION_PER_IP);
                return;
            }

            maxConnectionsPerIp = max;
            client.onMaxConnectionPerIP(max);

        } catch (SQLException ex) {
            Logger.getLogger(AccountHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void handleSetMaxRequestPerSecondPerIP(ChannelMessage msg) {
        try {
            int max = msg.getInt(0);
            int admin_id = msg.getInt(1);

            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasServerConfigPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_SERVER_CONFIG_PRIVILEDGE);
                return;
            }

            if (max < LOWER_LIMIT_OF_MAX_REQUEST_PER_SECOND_PER_IP) {
                client.onRequestFailed("Max request per second per ip cannot be less than " + LOWER_LIMIT_OF_MAX_REQUEST_PER_SECOND_PER_IP);
                return;
            }

            maxRequestsPerSecond = max;
            client.onMaxRequestPerSecondPerIP(max);

        } catch (SQLException ex) {
            Logger.getLogger(AccountHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void handleGetMaxConnectionPerIP(ChannelMessage msg) {
        try {
            int admin_id = msg.getInt(0);

            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasServerConfigPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_SERVER_CONFIG_PRIVILEDGE);
                return;
            }

            client.onMaxConnectionPerIP(maxConnectionsPerIp);

        } catch (SQLException ex) {
            Logger.getLogger(AccountHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void handleGetMaxRequestPerSecondPerIP(ChannelMessage msg) {
        try {
            int admin_id = msg.getInt(0);

            Client client = clientsMap.get(admin_id);
            if (!AdminDB.hasServerConfigPriviledge(admin_id)) {
                client.onRequestFailed(ERR_NO_SERVER_CONFIG_PRIVILEDGE);
                return;
            }

            client.onMaxRequestPerSecondPerIP(maxRequestsPerSecond);

        } catch (SQLException ex) {
            Logger.getLogger(AccountHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) throws IOException, ScanException {
        /* String ipAddress = "127.0.0.1";

        logger.info(MARKER_SUSPICIOUS_IP,
                concatLogMsg(ipAddress, "Testing Max. connection exceeded"));

        logger.info(MARKER_REJECTED_IP, ipAddress);

        logger.warn("This is an warn");

        logger.debug("This is an debug");

        logger.error("This is an error");
        try {
            throw new IllegalStateException("The is another error");
        } catch (IllegalStateException ex) {
            logger.error("The is the error i caught", ex);
        }*/

 /*Path logDir = Paths.get(System.getProperty("user.dir"), "logs");
        LocalDateTime startTime = LocalDateTime.of(2024, 10, 10, 8, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 10, 26, 18, 16, 59);

        List<String> logs = LogCapture.captureLogs(LogLevel.DEBUG, logDir, startTime, endTime);
        logs.forEach(System.out::println);
        
        System.out.println(System.getProperty("user.dir"));
        System.out.println(System.getenv("ProgramData"));*/
        System.out.println(Config.INFO_LOG_FILE);
        System.out.println(Config.WARN_LOG_FILE);
        System.out.println(Config.DEBUG_LOG_FILE);
        System.out.println(Config.ERROR_LOG_FILE);
        System.out.println(Config.TRACE_LOG_FILE);
        System.out.println(Config.REJECTED_IPS_LOG_FILE);
        System.out.println(Config.SUSPICIOUS_IPS_LOG_FILE);

    }

}
