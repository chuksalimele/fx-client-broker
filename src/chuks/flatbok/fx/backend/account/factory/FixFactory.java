/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbok.fx.backend.account.factory;

import quickfix.ConfigError;
import chuks.flatbok.fx.backend.account.contract.BrokerAccount;

/**
 *
 * @author user
 */
 public class FixFactory {
    
    static TraderAccountFactory getTraderAccountFactory(){
        return new TraderAccountFactoryImpl();
    }
    
    public static BrokerAccount createOrderNettingAccount(String settings_filname) throws ConfigError{
        return getTraderAccountFactory().createOrderNettingAccount(settings_filname);
    }    
    
    
    public static BrokerAccount createHedgeAccount(String settings_filename) throws ConfigError{
        return getTraderAccountFactory().createHedgeAccount(settings_filename);
    }     
}
