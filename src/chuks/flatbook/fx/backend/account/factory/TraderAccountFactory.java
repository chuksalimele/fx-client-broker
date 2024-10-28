/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbook.fx.backend.account.factory;

import quickfix.ConfigError;
import chuks.flatbook.fx.backend.account.contract.BrokerAccount;


/**
 *
 * @author user
 */
public interface TraderAccountFactory {
    public BrokerAccount createOrderNettingAccount(String settings_filename) throws ConfigError ;
    public BrokerAccount createHedgeAccount(String settings_filename) throws ConfigError ;    
}
