/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbok.fx.backend.listener;

import chuks.flatbok.fx.backend.account.contract.Identifier;

/**
 *
 * @author user
 */
public interface ConnectionListener {    
    Identifier onConnectionProgress(String status);
    Identifier onTradeSessionLogOn();
    Identifier onQuoteSessionLogOn();    
    Identifier onTradeSessionLogOut();
    Identifier onPriceSessionLogOut();        
}
