/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.main;

import chuks.flatbook.fx.backend.account.Broker_old;
import chuks.flatbook.fx.backend.account.contract.BrokerAccount;
import chuks.flatbook.fx.backend.account.factory.FixFactory;
import chuks.flatbook.fx.backend.channel.AccountServer;

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