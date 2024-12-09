/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.factory;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.backend.account.type.HedgeAccount;
import quickfix.ConfigError;
import chuks.flatbook.fx.backend.account.contract.BrokerAccount;

/**
 *
 * @author user
 */
// Factory class with methods to create instances using builders
 public class TraderAccountFactoryImpl implements TraderAccountFactory{
    @Override
    public BrokerAccount createOrderNettingAccount(String settings_filename) throws ConfigError  {
        return new OrderNettingAccount.Builder()
                .accountConfig(settings_filename)
                .build();
    }

    @Override
    public BrokerAccount createHedgeAccount(String settings_filename) throws ConfigError  {
        return new HedgeAccount.Builder()
                .accountConfig(settings_filename)
                .build();
    }
}

