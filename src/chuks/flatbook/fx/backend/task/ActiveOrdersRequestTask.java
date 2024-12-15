/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.task;

import chuks.flatbook.fx.backend.account.Broker;
import chuks.flatbook.fx.backend.exception.OrderActionException;
import chuks.flatbook.fx.backend.exception.TaskTimeoutException;
import chuks.flatbook.fx.common.account.order.UnfilledOrder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    String ACTIVE_ORDERS_REQUEST_TIMEOUT = "ACTIVE_ORDERS_REQUEST_TIMEOUT";

    public ActiveOrdersRequestTask(Broker account, String identifier) {
        super(account, identifier);
        //future.completeOnTimeout(new TaskResult(false, ACTIVE_ORDERS_REQUEST_TIMEOUT), 5, TimeUnit.SECONDS);
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
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                throw new TaskTimeoutException(ACTIVE_ORDERS_REQUEST_TIMEOUT);
            }

        } catch (ConfigError | SessionNotFound | TaskTimeoutException | InterruptedException | ExecutionException ex) {
            future.complete(new TaskResult(false, "Active Orders Request Failed - "+ex.getMessage()));
            logger.error(ex.getMessage(), ex);
        }

        return future;
    }

}
