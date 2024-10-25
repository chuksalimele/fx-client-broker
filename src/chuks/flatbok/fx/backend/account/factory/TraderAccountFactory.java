/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbok.fx.backend.account.factory;

import quickfix.ConfigError;
import chuks.flatbok.fx.backend.account.contract.BrokerAccount;


/**
 *
 * @author user
 */
public interface TraderAccountFactory {
    public BrokerAccount createOrderNettingAccount(String settings_filename) throws ConfigError ;
    public BrokerAccount createHedgeAccount(String settings_filename) throws ConfigError ;    
}
