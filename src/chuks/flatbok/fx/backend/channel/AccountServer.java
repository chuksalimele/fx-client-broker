package chuks.flatbok.fx.backend.channel;

import chuks.flatbok.fx.backend.account.contract.BrokerAccount;
import chuks.flatbok.fx.transport.DynamicIpFilter;
import chuks.flatbok.fx.transport.SharableTransportHandler;
import chuks.flatbok.fx.transport.TransportServer;
import org.slf4j.LoggerFactory;
import static chuks.flatbok.fx.backend.config.LogMarker.MARKER_REJECTED_IP;
import static chuks.flatbok.fx.common.util.log.LogConst.concatLogMsg;

/**
 *
 * @author user
 */
public class AccountServer extends TransportServer {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AccountServer.class.getName());
    private final BrokerAccount brokerAccount;
    private final DynamicIpFilter dynamicIpFilter = new DynamicIpFilter();
    private final String[] DEFAULT_WHITELISTED_IPS = {"127.0.0.1"};

    public AccountServer(BrokerAccount brokerAcc, int port) {
        super(port);
        this.brokerAccount = brokerAcc;
        this.dynamicIpFilter.whitelistIPs(DEFAULT_WHITELISTED_IPS);
        this.dynamicIpFilter.setFilterHook(new DynamicIpFilter.IPHook() {
            @Override
            public void onAccepted(String ip) {
            }

            @Override
            public void onRejected(String ip, String reason) {
                logger.info(MARKER_REJECTED_IP, concatLogMsg(ip, reason));
            }

        });
    }

    @Override
    public SharableTransportHandler getHandler() {
        return new AccountHandler(brokerAccount, dynamicIpFilter);
    }

    @Override
    protected DynamicIpFilter getIpFilter() {
        return dynamicIpFilter;
    }

}
