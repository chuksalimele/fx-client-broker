/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package order;

import chuks.flatbook.fx.common.account.order.MarketOrderIDFamily;
import chuks.flatbook.fx.common.account.order.OrderIDUtil;
import chuks.flatbook.fx.common.account.order.PendingOrderIDFamily;
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
        
        int COUNT = 3;

        //create markets order ids  the relatated ids
        int acc = 1000;
        int identifier = 222222;
        for (int i = 0; i < COUNT; i++) {
            acc++;
            String mrk_id = OrderIDUtil.createMarketOrderID(acc, ""+(++identifier));
            market_ids.add(mrk_id);
            for (int k = 0; k < COUNT; k++) {                
                
                String stp_loss = OrderIDUtil.createModifyStoplossOrderID(mrk_id, ""+(++identifier));
                String trg_loss = OrderIDUtil.createModifyTargetOrderID(mrk_id, ""+(++identifier));
                String close_loss = OrderIDUtil.createCloseOrderID(mrk_id, ""+(++identifier));
                market_ids.add(stp_loss);
                market_ids.add(trg_loss);
                market_ids.add(close_loss);
            }
        }
        
        //create pending order ids  the relatated ids
        for (int i = 0; i < COUNT; i++) {
            acc++;
            String pend_id = OrderIDUtil.createPendingOrderID(acc, ""+(++identifier));
            pending_ids.add(pend_id);
            for (int k = 0; k < COUNT; k++) {                
                
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
        
        System.out.println("==========================================================");        
        System.out.println("==================MARKET ORDER RELATED IDS===========================");
        System.out.println("==========================================================");        
        List<MarketOrderIDFamily> mrkFamLst = OrderIDUtil.groupByMarketOrderIDFamily(market_ids);
        
        for(int i=0; i< mrkFamLst.size(); i++){
            MarketOrderIDFamily family = mrkFamLst.get(i);
            
            System.out.println(family.getMarketOrderID());
            
            for(int k=0; k<  family.getModifyStoplossOrderIDs().size(); k++){
                System.out.println(family.getModifyStoplossOrderIDs().get(k));
            }
            for(int k=0; k<  family.getModifyTakeProfitOrderIDs().size(); k++){
                System.out.println(family.getModifyTakeProfitOrderIDs().get(k));
            }
            for(int k=0; k<  family.getCloseOrderIDs().size(); k++){
                System.out.println(family.getCloseOrderIDs().get(k));
            }
            
            System.out.println("----------------------------------------------");            
            System.out.println();            
            System.out.println("----------------------------------------------");            
            
        }
        System.out.println("==========================================================");                
        System.out.println("==================PENDING ORDER RELATED IDS===========================");
        System.out.println("==========================================================");
        
        List<PendingOrderIDFamily> pndFamLst = OrderIDUtil.groupByPendingOrderIDFamily(pending_ids);                
        
        for(int i=0; i< pndFamLst.size(); i++){
            PendingOrderIDFamily family = pndFamLst.get(i);
            
            System.out.println(family.getPendingOrderID());
                        
            for(int k=0; k<  family.getModifyStoplossOrderIDs().size(); k++){
                System.out.println(family.getModifyStoplossOrderIDs().get(k));
            }
            for(int k=0; k<  family.getModifyTakeProfitOrderIDs().size(); k++){
                System.out.println(family.getModifyTakeProfitOrderIDs().get(k));
            }
            for(int k=0; k<  family.getModifyEntryPriceOrderIDs().size(); k++){
                System.out.println(family.getModifyEntryPriceOrderIDs().get(k));
            }

            for(int k=0; k<  family.getDeleteOrderIDs().size(); k++){
                System.out.println(family.getDeleteOrderIDs().get(k));
            }            
            
            System.out.println("----------------------------------------------");            
            System.out.println();            
            System.out.println("----------------------------------------------");                        
        }
    }
}
