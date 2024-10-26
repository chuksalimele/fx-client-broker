/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package order;

import chuks.flatbok.fx.common.account.order.MarketOrderIDFamily;
import chuks.flatbok.fx.common.account.order.OrderIDUtil;
import chuks.flatbok.fx.common.account.order.PendingOrderIDFamily;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author user
 */
public class OrderIDTest {

    public static void main(String[] args) throws SQLException {
        List<String> market_ids = new LinkedList();
        List<String> pending_ids = new LinkedList();


        //create markets order ids  the relatated ids
        int acc = 1000;
        int identifier = 222222;
        for (int i = 0; i < 10; i++) {
            acc++;
            String mrk_id = OrderIDUtil.createMarketOrderID(acc, ""+(++identifier));
            market_ids.add(mrk_id);
            for (int k = 0; k < 10; k++) {                
                
                String stp_loss = OrderIDUtil.createModifyStoplossOrderID(mrk_id, ""+(++identifier));
                String trg_loss = OrderIDUtil.createModifyTargetOrderID(mrk_id, ""+(++identifier));
                String close_loss = OrderIDUtil.createCloseOrderID(mrk_id, ""+(++identifier));
                market_ids.add(stp_loss);
                market_ids.add(trg_loss);
                market_ids.add(close_loss);
            }
        }
        
        //create pending order ids  the relatated ids
        for (int i = 0; i < 10; i++) {
            acc++;
            String pend_id = OrderIDUtil.createPendingOrderID(acc, ""+(++identifier));
            pending_ids.add(pend_id);
            for (int k = 0; k < 10; k++) {                
                
                String stp_loss = OrderIDUtil.createModifyStoplossOrderID(pend_id, ""+(++identifier));
                String trg_loss = OrderIDUtil.createModifyTargetOrderID(pend_id, ""+(++identifier));
                String prc_loss = OrderIDUtil.createModifyEntryPriceOrderID(pend_id, ""+(++identifier));                
                String del_loss = OrderIDUtil.createDeleteOrderID(pend_id, ""+(++identifier));
                pending_ids.add(stp_loss);
                pending_ids.add(trg_loss);
                pending_ids.add(prc_loss);                
                pending_ids.add(del_loss);
            }
        }        
        
        List<MarketOrderIDFamily> mrkFamLst = OrderIDUtil.groupByMarketOrderIDFamily(market_ids);
        
        for(int i=0; i< mrkFamLst.size(); i++){
            MarketOrderIDFamily family = mrkFamLst.get(i);
            
        }
        
        List<PendingOrderIDFamily> pndFamLst = OrderIDUtil.groupByPendingOrderIDFamily(pending_ids);
        
        for(int i=0; i< pndFamLst.size(); i++){
            PendingOrderIDFamily family = pndFamLst.get(i);
            
        }
    }
}
