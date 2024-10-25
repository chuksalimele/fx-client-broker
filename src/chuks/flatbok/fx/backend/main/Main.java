/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbok.fx.backend.main;

import chuks.flatbok.fx.backend.account.Broker;
import chuks.flatbok.fx.backend.account.contract.BrokerAccount;
import chuks.flatbok.fx.backend.account.factory.FixFactory;
import chuks.flatbok.fx.backend.channel.AccountServer;

/**
 *
 * @author user
 */
public class Main {

    public static void main(String[] args) throws Exception {

        BrokerAccount brokerAcc = FixFactory
                .createOrderNettingAccount("quickfix.properties");

        new AccountServer(brokerAcc, 8080).run();
    }
}