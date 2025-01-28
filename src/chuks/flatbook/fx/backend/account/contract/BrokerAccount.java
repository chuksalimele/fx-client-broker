/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.contract;

import chuks.flatbook.fx.common.account.profile.TraderInfo;
import java.util.function.BiConsumer;

/**
 *
 * @author user
 */
public interface BrokerAccount extends BrokerOperation{

    public void registerTrader(TraderInfo account_profile, BiConsumer<Boolean, String> result);        
    
    public void login(int account_number, byte[] password, int user_type, BiConsumer<Boolean, String> result);  
    
    public void logout(int account_number, int user_type, BiConsumer<Boolean, String> result);  

}
