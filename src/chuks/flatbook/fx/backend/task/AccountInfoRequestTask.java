/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.task;

import chuks.flatbook.fx.backend.account.Broker;
import chuks.flatbook.fx.backend.account.BrokerAccountInfo;
import chuks.flatbook.fx.backend.task.netting.NettingCloseTask;
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
public class AccountInfoRequestTask extends Task {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AccountInfoRequestTask.class.getName());

    public AccountInfoRequestTask(Broker account, String identifier) {
        super(account, identifier);
    }

    @Override
    public void onAccountInfoReport(BrokerAccountInfo brokerAccountInfo) {
        future.complete(new TaskResult(true, "Account Info Report"));
        logger.debug("Account Info Report");
    }

    @Override
    protected CompletableFuture<TaskResult> run() {
        try {
            future = FixUtil.sendAccountInfoRequest(account);
        } catch (ConfigError | SessionNotFound ex) {
            future.complete(new TaskResult(false, "Account Info Request Failed"));
            logger.error(ex.getMessage(), ex);
        }

        return future;
    }

}
