/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.factory;

import quickfix.ConfigError;
import chuks.flatbook.fx.backend.account.contract.BrokerAccount;

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
