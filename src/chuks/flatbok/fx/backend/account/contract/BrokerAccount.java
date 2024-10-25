/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbok.fx.backend.account.contract;

import chuks.flatbok.fx.common.account.profile.TraderAccountProfile;

/**
 *
 * @author user
 */
public interface BrokerAccount extends BrokerOperation{

    public boolean registerTrader(TraderAccountProfile account_profile);        
    
    public boolean login(int account_number, byte[] password, int user_type);  
    
    public boolean logout(int account_number, int user_type);  

    public void sendAccountInfoRequest();
    
    public void sendRequestCurrentOpenPositions();
    
    public void sendRequestActiveOrders();

}
