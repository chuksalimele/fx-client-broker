/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbok.fx.backend.account.contract;

import chuks.flatbok.fx.common.account.order.ManagedOrder;
import chuks.flatbok.fx.common.account.order.SymbolInfo;
import chuks.flatbok.fx.backend.listener.ConnectionListener;
import chuks.flatbok.fx.backend.listener.OrderActionListener;
import chuks.flatbok.fx.backend.listener.SymbolUpdateListener;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import java.util.Set;

/**
 *
 * @author user
 */
public interface BrokerOperation {

    
    public Set<String> getAllSymbols();
    
    public List<SymbolInfo> getSymbolInfoList(String[] symbols);
           
    public void sendMarketOrder(ManagedOrder order);
    
    public void modifyOpenOrder(String clOrdId, double target_price, double stoploss_price);

    public void sendClosePosition(String clOrdId, double lot_size);
    
    public void placePendingOrder(ManagedOrder order);

    public void modifyPendingOrder(String clOrdId, double open_price, double target_price, double stoploss_price);
    
    public void deletePendingOrder(String clOrdId);
    
    public void addListeners(Client client); 
    
    public void clearListeners(Client client);
    
    public void clearListeners(ChannelHandlerContext ctx);
    
    public void refreshContent(int account_number);            

    public void shutdown();
        
}
