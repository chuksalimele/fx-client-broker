/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.task;

import chuks.flatbook.fx.backend.account.Broker;
import chuks.flatbook.fx.common.account.order.UnfilledOrder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;
import quickfix.ConfigError;
import quickfix.SessionNotFound;
import util.FixUtil;
import util.TaskResult;

/**
 *
 * @author user
 */
public class ActiveOrdersRequestTask extends Task {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ActiveOrdersRequestTask.class.getName());

    public ActiveOrdersRequestTask(Broker account, String identifier) {
        super(account, identifier);
    }

    @Override
    public void onOrderMassStatusReport(List<UnfilledOrder> unfilledOrderList) {
        future.complete(new TaskResult(true, "Active Orders"));
        logger.debug("Active Orders");
    }

    @Override
    protected CompletableFuture<TaskResult> run() {
        try {
            future = FixUtil.sendActiveOrdersRequest(account);
        } catch (ConfigError | SessionNotFound ex) {
            future.complete(new TaskResult(false, "Active Orders Request Failed"));
            logger.error(ex.getMessage(), ex);
        }

        return future;
    }

}
